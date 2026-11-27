"""Kiem tra bao mat AI Service that: xac thuc Server-to-Server (Guardrails lop 1b) va cac lop chan bang code
khong goi LLM (lop 3 Sanitizer, lop 4 Topic classifier). Khong ton han muc Cloudflare Neurons.
Gia tri nhay cam lay tu bien moi truong AI_SERVICE_BASE_URL, AI_SERVICE_SHARED_SECRET, khong ghi trong file.
Chay: PYTHONPATH=<repo>/ai-service python -u ai_audit.py (de dung lai compute_signature cua AI Service).
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

from guardrails.s2s_auth import SIGNATURE_HEADER, TIMESTAMP_HEADER, compute_signature
from guardrails.topic_classifier import OFF_TOPIC_REPLY

BASE_URL = os.environ.get("AI_SERVICE_BASE_URL", "").rstrip("/")
SECRET = os.environ.get("AI_SERVICE_SHARED_SECRET", "")
TIMEOUT_SECONDS = 60
UNAUTHORIZED_BODY = {"detail": "Unauthorized"}

results = []


def send(method, path, body=b"", headers=None):
    request_headers = {"Content-Type": "application/json", "User-Agent": "lori-security-audit/1.0"}
    request_headers.update(headers or {})
    request = urllib.request.Request(BASE_URL + path, data=body or None, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    try:
        return status, json.loads(raw.decode("utf-8"))
    except ValueError:
        return status, {"raw": raw.decode("utf-8", "replace")[:200]}


def sign(method, path, body=b"", timestamp=None, secret=None):
    timestamp = str(int(time.time())) if timestamp is None else timestamp
    signature = compute_signature(secret or SECRET, timestamp, method, path, body)
    return {TIMESTAMP_HEADER: timestamp, SIGNATURE_HEADER: signature}


def record(name, ok, detail):
    results.append(ok)
    print(f"{'PASS' if ok else 'FAIL'}  {name}  ({detail})")


def expect_unauthorized(name, method, path, body=b"", headers=None):
    status, response = send(method, path, body, headers)
    record(name, status == 401 and response == UNAUTHORIZED_BODY, f"HTTP {status}")


def expect_status(name, method, path, expected, payload=None, check=None):
    body = b"" if payload is None else json.dumps(payload).encode("utf-8")
    status, response = send(method, path, body, sign(method, path, body))
    ok = status == expected and (check is None or check(response))
    record(name, ok, f"HTTP {status}, can {expected}")


def main():
    if not BASE_URL or len(SECRET) < 32:
        sys.exit("Thieu AI_SERVICE_BASE_URL hoac AI_SERVICE_SHARED_SECRET (>= 32 ky tu)")
    now = int(time.time())
    chat_body = json.dumps({"message": "What does apple mean?"}).encode("utf-8")
    other_body = json.dumps({"message": "Something else"}).encode("utf-8")

    print("== Lop 1b: Server-to-Server Auth ==")
    status, _ = send("GET", "/")
    record("S01 GET / khong chu ky van cong khai (UptimeRobot)", status == 200, f"HTTP {status}")
    expect_unauthorized("S02 POST /ai/ping khong co header chu ky", "POST", "/ai/ping")
    expect_unauthorized("S03 chu ky sai", "POST", "/ai/ping",
                        headers={TIMESTAMP_HEADER: str(now), SIGNATURE_HEADER: "0" * 64})
    expect_unauthorized("S04 timestamp khong phai so", "POST", "/ai/ping",
                        headers={TIMESTAMP_HEADER: "abc", SIGNATURE_HEADER: "0" * 64})
    expect_unauthorized("S05 timestamp qua dai (13 chu so)", "POST", "/ai/ping",
                        headers={TIMESTAMP_HEADER: "9" * 13, SIGNATURE_HEADER: "0" * 64})
    expect_unauthorized("S06 chu ky dung nhung timestamp cu 10 phut", "POST", "/ai/ping",
                        headers=sign("POST", "/ai/ping", timestamp=str(now - 600)))
    expect_unauthorized("S07 chu ky dung nhung timestamp tuong lai 10 phut", "POST", "/ai/ping",
                        headers=sign("POST", "/ai/ping", timestamp=str(now + 600)))
    expect_unauthorized("S08 chu ky cua body khac", "POST", "/ai/chat", chat_body,
                        sign("POST", "/ai/chat", other_body))
    expect_unauthorized("S09 chu ky cua method GET dung cho POST", "POST", "/ai/ping",
                        headers=sign("GET", "/ai/ping"))
    expect_unauthorized("S10 chu ky cua duong dan /ai/health dung cho /ai/ping", "POST", "/ai/ping",
                        headers=sign("POST", "/ai/health"))
    expect_unauthorized("S11 chi co timestamp, thieu chu ky", "POST", "/ai/ping",
                        headers={TIMESTAMP_HEADER: str(now)})
    expect_unauthorized("S12 chi co chu ky, thieu timestamp", "POST", "/ai/ping",
                        headers={SIGNATURE_HEADER: sign("POST", "/ai/ping")[SIGNATURE_HEADER]})
    expect_unauthorized("S13 chu ky viet hoa (so sanh chinh xac)", "POST", "/ai/ping",
                        headers={**sign("POST", "/ai/ping"),
                                 SIGNATURE_HEADER: sign("POST", "/ai/ping")[SIGNATURE_HEADER].upper()})
    for index, doc_path in enumerate(("/docs", "/redoc", "/openapi.json"), start=14):
        expect_unauthorized(f"S{index} GET {doc_path} khong chu ky", "GET", doc_path)
    expect_unauthorized("S17 duong dan khong ton tai khong chu ky", "GET", "/admin")
    expect_unauthorized("S18 JSON hong khong chu ky (khong lo ra 422)", "POST", "/ai/chat", b"{not json")
    expect_unauthorized("S19 chu ky ky bang khoa sai", "POST", "/ai/ping",
                        headers=sign("POST", "/ai/ping", secret="x" * 40))
    expect_status("S20 chu ky hop le: POST /ai/ping", "POST", "/ai/ping", 200,
                  check=lambda r: r == {"status": "ok"})
    expect_status("S21 chu ky hop le: GET /ai/health", "GET", "/ai/health", 200,
                  check=lambda r: r.get("status") == "ok")

    replay_headers = sign("POST", "/ai/ping")
    first, _ = send("POST", "/ai/ping", headers=replay_headers)
    second, _ = send("POST", "/ai/ping", headers=replay_headers)
    print(f"INFO  S22 gui lai y nguyen request da ky trong 300 giay: lan 1 HTTP {first}, lan 2 HTTP {second} "
          "(gioi han da biet: chua co nonce, INFRA.md muc 17)")

    print("== Lop 3 (Sanitizer) va Lop 4 (Topic classifier): chan bang code, khong goi LLM ==")
    for name, message in (
        ("G01 SQL injection (UNION SELECT)", "x' UNION SELECT password FROM users"),
        ("G02 SQL injection ma hoa %XX hai lan", "x%2527%2520UNION%2520SELECT%2520password%2520FROM%2520users"),
        ("G03 path traversal", "Show me ../../etc/passwd"),
        ("G04 lenh he thong", "Please run: rm -rf /"),
        ("G05 chi toan the script (rong sau khi lam sach)", "<script>alert(1)</script>"),
    ):
        expect_status(name, "POST", "/ai/chat", 400, {"message": message})
    expect_status("G06 vocabulary qua 2 tu", "POST", "/ai/vocabulary", 400, {"text": "one, two, three"})
    expect_status("G07 chat lac de (viet code) tra cau co dinh, khong goi LLM", "POST", "/ai/chat", 200,
                  {"message": "Write a short Python function that reverses a string."},
                  check=lambda r: r.get("answer") == OFF_TOPIC_REPLY)

    failed = results.count(False)
    print(f"\nTong: {len(results)} kiem tra, {len(results) - failed} PASS, {failed} FAIL")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
