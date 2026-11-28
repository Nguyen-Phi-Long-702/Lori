"""Smoke test toan bo endpoint cua Backend that (Render), ke ca cac endpoint AI di qua AI Service (Vercel) (ke hoach Tuan 18, Ngay 6).
Moi endpoint duoc goi 1 lan voi 1 tai khoan thu Premium (mock); chi kiem tra HTTP status va cac truong chinh cua phan hoi.
Gia tri nhay cam lay tu bien moi truong BASE_URL, API_KEY, APP_SIGNATURE, AI_SERVICE_BASE_URL, khong ghi trong file.
Moi lan chay tao user w18d6-smoke-<thoi gian>@example.com va de lai du lieu thu trong Neon/Upstash: xoa bang
DELETE FROM users WHERE email LIKE 'w18d6-smoke-%@example.com' (sau khi ZREM user khoi 3 bang xep hang leaderboard:*).
Co goi LLM that (Cloudflare Workers AI) o cac endpoint AI; ket qua ghi vao results/smoke-<ngay>-<gio UTC>.md.
"""
import json
import mimetypes
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

BASE_URL = os.environ.get("BASE_URL", "").rstrip("/")
API_KEY = os.environ.get("API_KEY", "")
APP_SIGNATURE = os.environ.get("APP_SIGNATURE", "")
AI_SERVICE_BASE_URL = os.environ.get("AI_SERVICE_BASE_URL", "").rstrip("/")
HERE = Path(__file__).parent
IMAGE_FILE = HERE.parent / "ai-test" / "images" / "02-cat.jpg"
RESULTS_DIR = HERE / "results"
TIMEOUT_SECONDS = 90
# RateLimitFilter cho 60 request/phut/IP: nghi 1,1 giay sau moi request de khong tu chan minh
PACE_SECONDS = 1.1
# L17-07: AI Service doi khi tra 502 (qua 25 giay cho Cloudflare); Backend doi moi loi cua AI Service thanh 503 "AI service unavailable".
# Thu toi da 3 lan roi ghi nhan dung ket qua
AI_RETRY_STATUSES = (502, 503)
AI_ATTEMPTS = 3
AI_RETRY_PAUSE_SECONDS = 5
RUN_ID = int(time.time())
PASSWORD = "Smoke@12345"
NEW_PASSWORD = "Smoke@67890"

rows = []  # (ket qua, ten, chi tiet, giay)


def send(method, path, payload=None, token=None, with_key=True, base=None, raw=None, content_type="application/json"):
    """Gui 1 request, tra ve (status, body JSON hoac {}, so giay)."""
    headers = {"Content-Type": content_type, "User-Agent": "lori-smoke-test/1.0"}
    if with_key:
        headers["X-API-Key"] = API_KEY
        headers["X-App-Signature"] = APP_SIGNATURE
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = raw if raw is not None else (None if payload is None else json.dumps(payload).encode("utf-8"))
    request = urllib.request.Request((base or BASE_URL) + path, data=data, headers=headers, method=method)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            status, body = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, body = error.code, error.read()
    except OSError:
        status, body = 0, b""
    elapsed = time.perf_counter() - started
    try:
        parsed = json.loads(body.decode("utf-8")) if body else {}
    except ValueError:
        parsed = {}
    return status, parsed, elapsed


def call(method, path, **kwargs):
    result = send(method, path, **kwargs)
    time.sleep(PACE_SECONDS)
    return result


def record(outcome, name, detail, seconds):
    rows.append((outcome, name, detail, seconds))
    print(f"{outcome:<5} {name}  ({detail}, {seconds:.1f}s)")


def check(name, result, expected, has=None):
    """PASS khi status khop `expected` va body co du cac truong trong `has` (khong rong)."""
    status, body, seconds = result
    missing = [key for key in (has or []) if not (isinstance(body, dict) and body.get(key) not in (None, "", [], {}))]
    ok = status == expected and not missing
    detail = f"HTTP {status}, can {expected}" + (f", thieu/rong: {', '.join(missing)}" if missing and status == expected else "")
    record("PASS" if ok else "FAIL", name, detail, seconds)
    return body if ok else None


def check_ai(name, method, path, expected_keys, **kwargs):
    """Endpoint AI: thu toi da AI_ATTEMPTS lan neu gap 502/503 (L17-07)."""
    for attempt in range(1, AI_ATTEMPTS + 1):
        status, body, seconds = call(method, path, **kwargs)
        if status not in AI_RETRY_STATUSES or attempt == AI_ATTEMPTS:
            break
        print(f"      {name}: HTTP {status}, thu lai lan {attempt + 1}/{AI_ATTEMPTS} sau {AI_RETRY_PAUSE_SECONDS}s")
        time.sleep(AI_RETRY_PAUSE_SECONDS)
    label = f"{name} (lan thu {attempt})" if attempt > 1 else name
    return check(label, (status, body, seconds), 200, expected_keys)


def warm_up():
    print("Danh thuc Backend (Render Free co the mat toi ~1 phut)...")
    deadline = time.time() + 240
    while time.time() < deadline:
        if send("GET", "/actuator/health/ping", with_key=False)[0] == 200:
            return
        time.sleep(5)
    sys.exit("Backend khong tra loi trong 4 phut")


def group_public():
    print("== Khong can dang nhap ==")
    check("GET /actuator/health/ping", call("GET", "/actuator/health/ping", with_key=False), 200, ["status"])
    status, body, seconds = call("GET", "/actuator/health", with_key=False)
    record("PASS" if status == 200 and body.get("status") == "UP" else "FAIL",
           "GET /actuator/health (Neon + Upstash)", f"HTTP {status}, status={body.get('status')}", seconds)
    status, body, seconds = send("GET", "/", base=AI_SERVICE_BASE_URL, with_key=False)
    record("PASS" if status == 200 and "Lori AI Service" in body.get("message", "") else "FAIL",
           "GET / cua AI Service", f"HTTP {status}", seconds)


def group_content(token):
    print("== Noi dung (can dang nhap) ==")
    check("GET /api/content/version", call("GET", "/api/content/version", token=token), 200, ["version"])
    check("GET /api/content/updates", call("GET", "/api/content/updates?sinceVersion=0", token=token), 200, ["currentVersion"])


def group_auth():
    print("== Auth ==")
    email = f"w18d6-smoke-{RUN_ID}@example.com"
    body = check("POST /api/auth/register", call("POST", "/api/auth/register",
                 payload={"displayName": "Smoke Test", "email": email, "password": PASSWORD}), 201, ["accessToken"])
    if body is None:
        sys.exit("Khong dang ky duoc user thu, dung lai")
    body = check("POST /api/auth/login", call("POST", "/api/auth/login",
                 payload={"email": email, "password": PASSWORD}), 200, ["accessToken", "refreshToken"])
    if body is None:
        sys.exit("Khong dang nhap duoc user thu, dung lai")
    body = check("POST /api/auth/refresh", call("POST", "/api/auth/refresh",
                 payload={"refreshToken": body["refreshToken"]}), 200, ["accessToken", "refreshToken"])
    if body is None:
        sys.exit("Khong refresh duoc token, dung lai")
    # Khong co ID Token Google that de thu: chi xac nhan endpoint ton tai va tu choi token sai
    check("POST /api/auth/google (token sai)", call("POST", "/api/auth/google", payload={"idToken": "khong-hop-le"}), 401)
    # Email chua dang ky: Backend tra 204 ma khong gui thu that
    check("POST /api/auth/forgot-password (email chua dang ky)", call("POST", "/api/auth/forgot-password",
          payload={"email": f"w18d6-noexist-{RUN_ID}@example.com"}), 204)
    check("POST /api/auth/reset-password (ma sai)", call("POST", "/api/auth/reset-password",
          payload={"email": f"w18d6-noexist-{RUN_ID}@example.com", "code": "000000", "newPassword": NEW_PASSWORD}), 400)
    return email, body


def group_user_and_progress(token):
    print("== Nguoi dung, thanh toan mock, tien trinh ==")
    check("GET /api/users/me", call("GET", "/api/users/me", token=token), 200, ["email"])
    check("PUT /api/users/me", call("PUT", "/api/users/me", payload={"displayName": "Smoke Test 2"}, token=token),
          200, ["displayName"])
    check("POST /api/payment/mock-purchase", call("POST", "/api/payment/mock-purchase",
          payload={"planType": "MONTHLY"}, token=token), 201, ["planType"])
    status, body, seconds = call("GET", "/api/users/me", token=token)
    record("PASS" if status == 200 and body.get("premium") is True else "FAIL",
           "GET /api/users/me sau khi mua (premium = true)", f"HTTP {status}, premium={body.get('premium')}", seconds)
    # Ngay hoc cu hon 7 ngay: du lieu van duoc luu nhung Backend khong cong EXP/streak (giong Postman T6)
    old = datetime.fromtimestamp(time.time() - 30 * 86400, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    items = [
        {"itemType": "grammar_quiz_lesson", "itemId": 26, "status": "completed", "correctCount": 2,
         "incorrectCount": 8, "lastStudiedAt": old},
        {"itemType": "vocab_quiz_topic", "itemId": 13, "status": "completed", "correctCount": 3,
         "incorrectCount": 7, "lastStudiedAt": old},
        {"itemType": "grammar_lesson", "itemId": 27, "status": "completed", "correctCount": 0,
         "incorrectCount": 0, "lastStudiedAt": old},
    ]
    status, body, seconds = call("POST", "/api/progress/sync", payload={"items": items}, token=token)
    record("PASS" if status == 200 and body.get("applied") == 3 else "FAIL",
           "POST /api/progress/sync (3 muc)", f"HTTP {status}, applied={body.get('applied')}", seconds)
    status, body, seconds = call("GET", "/api/progress/pull", token=token)
    pulled = body if isinstance(body, list) else []
    record("PASS" if status == 200 and len(pulled) == 3 else "FAIL",
           "GET /api/progress/pull", f"HTTP {status}, {len(pulled)} muc, can 3", seconds)
    check("GET /api/users/me/stats", call("GET", "/api/users/me/stats", token=token), 200, ["level"])
    check("GET /api/users/me/weaknesses", call("GET", "/api/users/me/weaknesses", token=token), 200)
    check("GET /api/users/me/learning-profile", call("GET", "/api/users/me/learning-profile", token=token), 200)


def group_exams(token):
    print("== De thi ==")
    status, body, seconds = call("GET", "/api/exams", token=token)
    exams = body if isinstance(body, list) else []
    record("PASS" if status == 200 and exams else "FAIL", "GET /api/exams",
           f"HTTP {status}, {len(exams)} de: " + "; ".join(f"{e['title']} ({e['totalQuestions']} cau)" for e in exams), seconds)
    result_id = None
    for exam in exams:
        title = exam["title"]
        detail = check(f"GET /api/exams/{{id}} - {title}", call("GET", f"/api/exams/{exam['id']}", token=token), 200, ["sections"])
        if detail is not None:
            questions = sum(len(s["questions"]) for s in detail["sections"])
            record("PASS" if questions == exam["totalQuestions"] else "FAIL", f"So cau trong chi tiet khop danh sach - {title}",
                   f"{questions} / {exam['totalQuestions']}", 0)
        started = check(f"POST /api/exams/{{id}}/start - {title}", call("POST", f"/api/exams/{exam['id']}/start", token=token),
                        200, ["sessionToken"])
        if started is None:
            continue
        result = check(f"POST /api/exams/{{id}}/submit (bai trang) - {title}", call("POST", f"/api/exams/{exam['id']}/submit",
                       payload={"sessionToken": started["sessionToken"], "answers": []}, token=token), 200, ["id"])
        if result is not None:
            result_id = result["id"]
    status, body, seconds = call("GET", "/api/exams/results", token=token)
    record("PASS" if status == 200 and isinstance(body, list) and len(body) == len(exams) else "FAIL",
           "GET /api/exams/results", f"HTTP {status}, {len(body) if isinstance(body, list) else 0} ket qua, can {len(exams)}", seconds)
    if result_id:
        check("GET /api/exams/results/{id}", call("GET", f"/api/exams/results/{result_id}", token=token), 200, ["result", "items"])
    return result_id


def group_leaderboard(token):
    print("== Bang xep hang ==")
    for period in ("global", "weekly", "monthly"):
        status, body, seconds = call("GET", f"/api/leaderboard/{period}", token=token)
        record("PASS" if status == 200 and isinstance(body, list) else "FAIL",
               f"GET /api/leaderboard/{period}", f"HTTP {status}", seconds)
        status, body, seconds = call("GET", f"/api/leaderboard/{period}/me", token=token)
        record("PASS" if status == 200 and "score" in body else "FAIL",
               f"GET /api/leaderboard/{period}/me", f"HTTP {status}", seconds)


def group_translate(token):
    print("== Dich (LibreTranslate) ==")
    status, body, seconds = call("POST", "/api/translate", token=token,
                                 payload={"text": "Good morning", "sourceLang": "en", "targetLang": "vi"})
    if status == 200 and body.get("translatedText"):
        record("PASS", "POST /api/translate", "HTTP 200", seconds)
    elif status == 503:
        record("KNOWN", "POST /api/translate", "HTTP 503: LibreTranslate chua chay tren Render (INFRA.md muc 12)", seconds)
    else:
        record("FAIL", "POST /api/translate", f"HTTP {status}, can 200 hoac 503", seconds)


def group_ai(token, result_id):
    print("== AI qua Backend -> AI Service (goi LLM that, mat vai phut) ==")
    check_ai("POST /api/ai/chat", "POST", "/api/ai/chat", ["answer"], token=token,
             payload={"message": "What is the difference between 'a' and 'an'? Answer in two short sentences."})
    check_ai("POST /api/ai/conversation", "POST", "/api/ai/conversation", ["reply"], token=token,
             payload={"mode": "daily", "messages": [], "finish": False})
    check_ai("POST /api/ai/analyze-text (grammar)", "POST", "/api/ai/analyze-text", ["type"], token=token,
             payload={"type": "grammar", "text": "She go to school yesterday."})
    check_ai("POST /api/ai/analyze-text (vocabulary)", "POST", "/api/ai/analyze-text", ["type", "words"], token=token,
             payload={"type": "vocabulary", "text": "borrow vs lend"})
    check_ai("POST /api/ai/analyze-quiz", "POST", "/api/ai/analyze-quiz", ["type"], token=token,
             payload={"quizType": "grammar", "itemId": 26})
    if result_id:
        check_ai("POST /api/ai/analyze-exam", "POST", "/api/ai/analyze-exam", ["type"], token=token,
                 payload={"resultId": result_id})
    else:
        record("FAIL", "POST /api/ai/analyze-exam", "khong co resultId vi buoc nop bai o tren that bai", 0)
    check_ai("POST /api/ai/generate-exercise", "POST", "/api/ai/generate-exercise", ["exercises"], token=token,
             payload={"category": "grammar", "topicId": 26})
    check_ai("POST /api/ai/recommend", "POST", "/api/ai/recommend", ["topics"], token=token)
    boundary = uuid.uuid4().hex
    part_type = mimetypes.guess_type(IMAGE_FILE.name)[0] or "application/octet-stream"
    head = (f'--{boundary}\r\nContent-Disposition: form-data; name="image"; filename="{IMAGE_FILE.name}"\r\n'
            f"Content-Type: {part_type}\r\n\r\n").encode("utf-8")
    multipart = head + IMAGE_FILE.read_bytes() + f"\r\n--{boundary}--\r\n".encode("utf-8")
    check_ai("POST /api/ai/identify-image", "POST", "/api/ai/identify-image", ["objects"], token=token,
             raw=multipart, content_type=f"multipart/form-data; boundary={boundary}")


def group_password_and_logout(email, tokens):
    print("== Doi mat khau va dang xuat ==")
    check("PUT /api/users/me/password", call("PUT", "/api/users/me/password", token=tokens["accessToken"],
          payload={"currentPassword": PASSWORD, "newPassword": NEW_PASSWORD}), 204)
    body = check("POST /api/auth/login (mat khau moi)", call("POST", "/api/auth/login",
                 payload={"email": email, "password": NEW_PASSWORD}), 200, ["refreshToken"])
    if body is not None:
        check("POST /api/auth/logout", call("POST", "/api/auth/logout", payload={"refreshToken": body["refreshToken"]}), 204)


def write_report(started):
    RESULTS_DIR.mkdir(exist_ok=True)
    counts = {key: sum(1 for row in rows if row[0] == key) for key in ("PASS", "FAIL", "KNOWN")}
    path = RESULTS_DIR / f"smoke-{started:%Y%m%d-%H%M%S}.md"
    lines = [
        "# Ket qua smoke test (Tuan 18, Ngay 6)", "",
        f"- Bat dau: {started:%Y-%m-%d %H:%M:%S} UTC",
        f"- Backend: {BASE_URL}", f"- AI Service: {AI_SERVICE_BASE_URL}",
        f"- User thu: w18d6-smoke-{RUN_ID}@example.com (Free, sau do mua Premium bang mock-purchase)",
        f"- Tong: {len(rows)} kiem tra, {counts['PASS']} PASS, {counts['FAIL']} FAIL, {counts['KNOWN']} KNOWN", "",
        "| Ket qua | Kiem tra | Chi tiet | Giay |", "|---|---|---|---|",
    ]
    lines += [f"| {o} | {n} | {d} | {s:.1f} |" for o, n, d, s in rows]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Da ghi {path}")
    return counts


def main():
    if not (BASE_URL and API_KEY and APP_SIGNATURE and AI_SERVICE_BASE_URL):
        sys.exit("Thieu bien moi truong BASE_URL, API_KEY, APP_SIGNATURE hoac AI_SERVICE_BASE_URL")
    if not IMAGE_FILE.is_file():
        sys.exit(f"Khong thay anh {IMAGE_FILE}")
    started = datetime.now(timezone.utc)
    warm_up()
    group_public()
    email, tokens = group_auth()
    token = tokens["accessToken"]
    group_content(token)
    group_user_and_progress(token)
    result_id = group_exams(token)
    group_leaderboard(token)
    group_translate(token)
    group_ai(token, result_id)
    group_password_and_logout(email, tokens)
    counts = write_report(started)
    print(f"Tong: {len(rows)} kiem tra, {counts['PASS']} PASS, {counts['FAIL']} FAIL, {counts['KNOWN']} KNOWN")
    sys.exit(1 if counts["FAIL"] else 0)


if __name__ == "__main__":
    main()
