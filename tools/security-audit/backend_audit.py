"""Kiem tra bao mat Backend that (Render): ApiKey, Swagger, Security Headers, BruteForce, JWT, InputSanitizer,
thanh toan mock, Exam Token, lop 2 + lop 6 cua AI Gateway va rate limit theo IP.
Gia tri nhay cam lay tu bien moi truong BASE_URL, API_KEY, APP_SIGNATURE, khong ghi trong file.
Moi lan chay tao user thu secaudit.<thoi gian>.<so>@example.com (Premium mock) va de lai du lieu thu trong Neon/Upstash.
Khong goi LLM: moi request AI deu bi lop Sanitizer cua AI Service chan bang 400 nen khong ton han muc Cloudflare.
"""
import base64
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor

BASE_URL = os.environ.get("BASE_URL", "").rstrip("/")
API_KEY = os.environ.get("API_KEY", "")
APP_SIGNATURE = os.environ.get("APP_SIGNATURE", "")
TIMEOUT_SECONDS = 120
PASSWORD = "SecAudit@12345"
# RateLimitFilter cho 60 request/phut/IP: nghi 1,1 giay sau moi request thuong de khong tu chan minh
PACE_SECONDS = 1.1
BUCKET_REFILL_SECONDS = 65
RUN_ID = int(time.time())
SQLI_MESSAGE = "x' UNION SELECT password FROM users"

results = []
user_counter = 0


def request(method, path, payload=None, token=None, extra_headers=None, with_key=True, raw_body=None):
    """Gui 1 request, tra ve (status, body JSON hoac {}, header phan hoi)."""
    headers = {"Content-Type": "application/json", "User-Agent": "lori-security-audit/1.0"}
    if with_key:
        headers["X-API-Key"] = API_KEY
        headers["X-App-Signature"] = APP_SIGNATURE
    if token:
        headers["Authorization"] = f"Bearer {token}"
    headers.update(extra_headers or {})
    data = raw_body if raw_body is not None else (None if payload is None else json.dumps(payload).encode("utf-8"))
    req = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as response:
            status, raw, response_headers = response.status, response.read(), response.headers
    except urllib.error.HTTPError as error:
        status, raw, response_headers = error.code, error.read(), error.headers
    try:
        body = json.loads(raw.decode("utf-8")) if raw else {}
    except ValueError:
        body = {}
    return status, body, response_headers


def api(*args, **kwargs):
    result = request(*args, **kwargs)
    time.sleep(PACE_SECONDS)
    return result


def record(name, ok, detail):
    results.append(ok)
    print(f"{'PASS' if ok else 'FAIL'}  {name}  ({detail})")


def check_status(name, status, expected):
    record(name, status == expected, f"HTTP {status}, can {expected}")


def new_user(display_name="Sec Audit"):
    global user_counter
    user_counter += 1
    email = f"secaudit.{RUN_ID}.{user_counter}@example.com"
    status, body, _ = api("POST", "/api/auth/register",
                          {"displayName": display_name, "email": email, "password": PASSWORD})
    return email, status, body


def new_session(premium=False):
    email, status, body = new_user()
    if status != 201:
        sys.exit(f"Khong tao duoc user thu: HTTP {status} {body}")
    token = body["accessToken"]
    if premium:
        status, body_p, _ = api("POST", "/api/payment/mock-purchase", {"planType": "MONTHLY"}, token)
        if status != 201:
            sys.exit(f"mock-purchase that bai: HTTP {status} {body_p}")
    return email, body["accessToken"], body["refreshToken"]


def decode_part(part):
    return json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))


def encode_part(value):
    return base64.urlsafe_b64encode(json.dumps(value, separators=(",", ":")).encode()).rstrip(b"=").decode()


def wait_for_bucket_refill():
    print(f"... cho {BUCKET_REFILL_SECONDS} giay de gioi han 60 request/phut cua IP nay day lai")
    time.sleep(BUCKET_REFILL_SECONDS)


def warm_up():
    print("Danh thuc Backend (Render Free co the mat toi ~1 phut)...")
    deadline = time.time() + 240
    while time.time() < deadline:
        try:
            status, _, _ = request("GET", "/actuator/health/ping", with_key=False)
            if status == 200:
                return
        except OSError:
            pass
        time.sleep(5)
    sys.exit("Backend khong tra loi trong 4 phut")


def group_api_key(token):
    print("== ApiKey (anti-abuse, ke hoach 8.0) ==")
    me = "/api/users/me"
    check_status("A01 khong co header key", api("GET", me, with_key=False)[0], 403)
    check_status("A02 sai X-API-Key", api("GET", me, extra_headers={"X-API-Key": "sai"})[0], 403)
    check_status("A03 sai X-App-Signature", api("GET", me, extra_headers={"X-App-Signature": "sai"})[0], 403)
    check_status("A04 thieu X-App-Signature (co X-API-Key)",
                 api("GET", me, extra_headers={"X-API-Key": API_KEY}, with_key=False)[0], 403)
    check_status("A05 chu ky viet hoa van hop le (khong phan biet hoa thuong) -> qua lop key, toi JWT thi 401",
                 api("GET", me, extra_headers={"X-App-Signature": APP_SIGNATURE.upper()})[0], 401)
    check_status("A06 key dung + JWT dung", api("GET", me, token=token)[0], 200)
    check_status("A07 JWT dung nhung khong co key", api("GET", me, token=token, with_key=False)[0], 403)
    check_status("A08 /actuator/health/ping khong can key (UptimeRobot)",
                 api("GET", "/actuator/health/ping", with_key=False)[0], 200)
    status, body, _ = api("GET", "/actuator/health", with_key=False)
    components = body.get("components", {})
    no_details = status == 200 and all("details" not in value for value in components.values())
    record("A09 /actuator/health chi lo trang thai, khong lo chi tiet", no_details, f"HTTP {status}")
    check_status("A10 /actuator/env khong duoc mo (key + JWT dung)", api("GET", "/actuator/env", token=token)[0], 404)


def group_swagger():
    print("== Swagger UI tren moi truong public ==")
    for index, path in enumerate(("/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html"), start=1):
        check_status(f"B0{index} GET {path} phai la 404", api("GET", path, with_key=False)[0], 404)


def group_headers():
    print("== Security Headers ==")
    status, _, headers = api("GET", "/actuator/health/ping", with_key=False)
    hsts = headers.get("Strict-Transport-Security") or ""
    expected = (
        ("C01 X-Content-Type-Options = nosniff", headers.get("X-Content-Type-Options") == "nosniff"),
        ("C02 X-Frame-Options = DENY", headers.get("X-Frame-Options") == "DENY"),
        ("C03 Strict-Transport-Security max-age >= 1 nam", "max-age=31536000" in hsts),
        ("C04 Referrer-Policy = no-referrer", headers.get("Referrer-Policy") == "no-referrer"),
        ("C05 Cache-Control co no-store", "no-store" in (headers.get("Cache-Control") or "")),
    )
    for name, ok in expected:
        record(name, ok, f"HTTP {status}")


def group_brute_force():
    print("== BruteForce dang nhap (5 lan sai -> khoa 15 phut) ==")
    email, status, _ = new_user()
    check_status("D01 dang ky user thu", status, 201)
    wrong_ok = True
    for attempt in range(5):
        status, body, _ = api("POST", "/api/auth/login", {"email": email, "password": "Sai@12345"},
                              extra_headers={"X-Forwarded-For": f"198.51.100.{attempt}"})
        wrong_ok = wrong_ok and status == 401 and body.get("message") == "Invalid email or password"
    record("D02 5 lan sai mat khau (moi lan doi IP gia) deu 401 cung thong bao", wrong_ok, "5 request")
    status, body, _ = api("POST", "/api/auth/login", {"email": email, "password": PASSWORD},
                          extra_headers={"X-Forwarded-For": "198.51.100.99"})
    locked = status == 429 and body.get("message") == "Too many failed login attempts, try again later"
    record("D03 lan thu 6 voi mat khau DUNG van bi khoa 429 (khoa theo email, doi IP khong qua duoc)", locked,
           f"HTTP {status}")
    ghost = f"secaudit.{RUN_ID}.ghost@example.com"
    ghost_ok = True
    for _ in range(5):
        status, body, _ = api("POST", "/api/auth/login", {"email": ghost, "password": "Sai@12345"})
        ghost_ok = ghost_ok and status == 401 and body.get("message") == "Invalid email or password"
    record("D04 email khong ton tai: 5 lan dau cung 401 cung thong bao nhu sai mat khau", ghost_ok, "5 request")
    status, body, _ = api("POST", "/api/auth/login", {"email": ghost, "password": "Sai@12345"})
    check_status("D05 email khong ton tai cung bi khoa o lan 6 (khong lo email nao co that)", status, 429)


def group_jwt(token, refresh_token):
    print("== JWT Hardening ==")
    header_part, payload_part, _ = token.split(".")
    payload = decode_part(payload_part)
    me = "/api/users/me"
    audiences = payload.get("aud")
    audiences = [audiences] if isinstance(audiences, str) else (audiences or [])
    alg = decode_part(header_part).get("alg")
    claims_ok = (alg in ("HS256", "HS384", "HS512") and payload.get("iss") == "lori-api"
                 and "lori-android" in audiences and payload.get("exp", 0) - payload.get("iat", 0) == 900
                 and re.fullmatch(r"[0-9a-f-]{36}", payload.get("sub", "")) is not None)
    record("E01 claims: alg HMAC (HS256/384/512), iss, aud, sub = UUID, het han sau 15 phut", claims_ok, f"alg={alg}")
    check_status("E02 token rac", api("GET", me, token="abc.def.ghi")[0], 401)
    forged_none = f"{encode_part({'alg': 'none', 'typ': 'JWT'})}.{payload_part}."
    check_status("E03 token alg=none (khong chu ky)", api("GET", me, token=forged_none)[0], 401)
    check_status("E04 token bi cat chu ky", api("GET", me, token=f"{header_part}.{payload_part}")[0], 401)
    other_sub = encode_part({**payload, "sub": str(uuid.uuid4())})
    check_status("E05 sua sub, giu chu ky cu", api("GET", me, token=f"{header_part}.{other_sub}.{token.split('.')[2]}")[0], 401)
    longer_exp = encode_part({**payload, "exp": payload["exp"] + 86400})
    check_status("E06 keo dai exp, giu chu ky cu", api("GET", me, token=f"{header_part}.{longer_exp}.{token.split('.')[2]}")[0], 401)
    record("E07 refresh token ngau nhien >= 43 ky tu (256-bit)", len(refresh_token) >= 43, f"{len(refresh_token)} ky tu")
    status, body, _ = api("POST", "/api/auth/refresh", {"refreshToken": refresh_token})
    rotated = body.get("refreshToken", "")
    record("E08 refresh -> 200 + refresh token moi khac token cu", status == 200 and rotated not in ("", refresh_token),
           f"HTTP {status}")
    check_status("E09 dung lai refresh token cu (da xoay vong) -> 401",
                 api("POST", "/api/auth/refresh", {"refreshToken": refresh_token})[0], 401)
    check_status("E10 logout -> 204", api("POST", "/api/auth/logout", {"refreshToken": rotated})[0], 204)
    check_status("E11 refresh bang token da logout -> 401",
                 api("POST", "/api/auth/refresh", {"refreshToken": rotated})[0], 401)


def group_sanitizer():
    print("== InputSanitizer (display name) ==")
    cases = (
        ("F01 <script> bi bo ca noi dung", "<script>alert(1)</script>Bob", "Bob"),
        ("F02 the HTML va entity bi bo, khong con < >", "<b>Hi</b> &lt;img src=x onerror=alert(1)&gt; there", None),
        ("F03 dau nhay don (O'Brien) duoc luu nguyen van, khong lam hong truy van", "O'Brien", "O'Brien"),
    )
    for name, display_name, expected in cases:
        email, status, body = new_user(display_name)
        token = body.get("accessToken")
        status_me, me, _ = api("GET", "/api/users/me", token=token)
        stored = me.get("displayName", "")
        ok = status == 201 and status_me == 200 and (
            stored == expected if expected is not None else "<" not in stored and ">" not in stored)
        record(name, ok, f"dang ky HTTP {status}, ten luu: {stored!r}")
    _, status, _ = new_user("<script>x</script>")
    check_status("F04 ten chi toan the HTML -> 400", status, 400)
    _, status, _ = new_user("a" * 101)
    check_status("F05 ten 101 ky tu -> 400", status, 400)
    _, token, _ = new_session()
    status, body, _ = api("PUT", "/api/users/me", {"displayName": "<i>Neo</i>"}, token)
    record("F06 PUT /api/users/me cung lam sach", status == 200 and body.get("displayName") == "Neo",
           f"HTTP {status}")
    status, _, _ = api("POST", "/api/auth/login", {"email": "' OR '1'='1", "password": "x"})
    check_status("F07 email dang SQL injection bi validation chan", status, 400)


def group_payment(token):
    print("== Thanh toan mock (xac thuc yeu cau mua) ==")
    path = "/api/payment/mock-purchase"
    check_status("G01 khong JWT -> 401", api("POST", path, {"planType": "MONTHLY"}, with_key=True)[0], 401)
    check_status("G02 planType khong hop le -> 400", api("POST", path, {"planType": "WEEKLY"}, token)[0], 400)
    check_status("G03 thieu planType -> 400", api("POST", path, {}, token)[0], 400)
    check_status("G04 khong co body -> 400", api("POST", path, None, token)[0], 400)
    status, body, _ = api("POST", path, {"planType": "MONTHLY", "price": 0, "transactionId": "HACK", "status": "ACTIVE"},
                          token)
    record("G05 truong gia/transactionId do client gui bi bo qua, goi MOCK/ACTIVE do server quyet",
           status == 201 and body.get("paymentMethod") == "MOCK" and body.get("status") == "ACTIVE", f"HTTP {status}")


def group_exam(free_token, premium, other_premium):
    print("== Exam Token ==")
    check_status("H01 user Free bi chan khoi /api/exams", api("GET", "/api/exams", token=free_token)[0], 403)
    status, exams, _ = api("GET", "/api/exams", token=premium)
    record("H02 user Premium xem duoc danh sach de", status == 200 and len(exams) >= 1, f"HTTP {status}")
    if not exams:
        return
    first = exams[0]["id"]
    second = next((exam["id"] for exam in exams if exam["id"] != first), None)
    submit = lambda token, exam_id, session: api(  # noqa: E731
        "POST", f"/api/exams/{exam_id}/submit", {"sessionToken": session, "answers": []}, token)
    start = lambda token, exam_id: api("POST", f"/api/exams/{exam_id}/start", None, token)  # noqa: E731

    check_status("H03 token ngau nhien chua tung cap -> 400", submit(premium, first, str(uuid.uuid4()))[0], 400)
    status, body, _ = start(premium, first)
    token_a = body.get("sessionToken", "")
    record("H04 start -> token dang UUID", status == 200 and re.fullmatch(r"[0-9a-f-]{36}", token_a) is not None,
           f"HTTP {status}")
    check_status("H05 user khac dung token cua nguoi khac -> 400", submit(other_premium, first, token_a)[0], 400)
    if second:
        token_b = start(premium, first)[1].get("sessionToken", "")
        check_status("H06 dung token cua de A de nop de B -> 400", submit(premium, second, token_b)[0], 400)
    token_c = start(premium, first)[1].get("sessionToken", "")
    check_status("H07 token dung, dung nguoi, dung de -> 200", submit(premium, first, token_c)[0], 200)
    check_status("H08 nop lai bang token da dung -> 400", submit(premium, first, token_c)[0], 400)
    token_d = start(premium, first)[1].get("sessionToken", "")
    with ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(request, "POST", f"/api/exams/{first}/submit",
                               {"sessionToken": token_d, "answers": []}, premium) for _ in range(2)]
        statuses = sorted(future.result()[0] for future in futures)
    record("H09 hai request nop CUNG LUC chi 1 request thanh cong", statuses == [200, 400], f"{statuses}")
    time.sleep(PACE_SECONDS)
    too_many = [{"questionId": str(uuid.uuid4()), "answer": "A"}] * 501
    check_status("H10 501 dap an -> 400 (gioi han 500)",
                 api("POST", f"/api/exams/{first}/submit", {"sessionToken": "x", "answers": too_many}, premium)[0], 400)
    long_answer = [{"questionId": str(uuid.uuid4()), "answer": "a" * 256}]
    check_status("H11 dap an 256 ky tu -> 400 (gioi han 255)",
                 api("POST", f"/api/exams/{first}/submit", {"sessionToken": "x", "answers": long_answer}, premium)[0], 400)


def group_ai_gateway(free_token, premium, other_premium):
    print("== AI Gateway: lop 2 (JWT + Premium) va lop 6 (rate limit 20 request/phut/user) ==")
    chat = "/api/ai/chat"
    check_status("I01 khong JWT -> 401", api("POST", chat, {"message": "hello"})[0], 401)
    status, body, _ = api("POST", chat, {"message": "hello"}, free_token)
    record("I02 user Free -> 403 PREMIUM_REQUIRED", status == 403 and body.get("message") == "PREMIUM_REQUIRED",
           f"HTTP {status}")
    wait_for_bucket_refill()
    with ThreadPoolExecutor(max_workers=10) as pool:
        futures = [pool.submit(request, "POST", chat, {"message": SQLI_MESSAGE}, premium) for _ in range(30)]
        statuses = [future.result()[0] for future in futures]
    record("I03 30 request dong thoi cua 1 user: co 400 (Sanitizer chan) va co 429 (het luot)",
           statuses.count(400) >= 18 and statuses.count(429) >= 1,
           f"400={statuses.count(400)}, 429={statuses.count(429)}, khac={len(statuses) - statuses.count(400) - statuses.count(429)}")
    status, _, _ = api("POST", chat, {"message": SQLI_MESSAGE}, other_premium)
    check_status("I04 user Premium khac KHONG bi anh huong (gioi han theo user) -> 400", status, 400)


def group_rate_limit_spoof():
    print("== Rate limit theo IP: gia X-Forwarded-For ==")
    wait_for_bucket_refill()

    def spoofed(index):
        return request("GET", "/api/users/me", extra_headers={"X-Forwarded-For": f"10.{RUN_ID % 250}.0.{index}"})[0]

    with ThreadPoolExecutor(max_workers=20) as pool:
        statuses = list(pool.map(spoofed, range(80)))
    record("K01 80 request, moi request mot IP gia -> van phai co 429 (gioi han 60/phut theo IP that)",
           statuses.count(429) >= 1, f"401={statuses.count(401)}, 429={statuses.count(429)}")


def main():
    if not (BASE_URL and API_KEY and APP_SIGNATURE):
        sys.exit("Thieu bien moi truong BASE_URL, API_KEY hoac APP_SIGNATURE")
    warm_up()
    _, free_token, free_refresh = new_session()
    group_api_key(free_token)
    group_swagger()
    group_headers()
    group_brute_force()
    group_jwt(free_token, free_refresh)
    group_sanitizer()
    _, payment_token, _ = new_session()
    group_payment(payment_token)
    _, premium, _ = new_session(premium=True)
    _, other_premium, _ = new_session(premium=True)
    group_exam(free_token, premium, other_premium)
    group_ai_gateway(free_token, premium, other_premium)
    group_rate_limit_spoof()

    failed = results.count(False)
    print(f"\nTong: {len(results)} kiem tra, {len(results) - failed} PASS, {failed} FAIL")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
