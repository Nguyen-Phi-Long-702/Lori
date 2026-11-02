"""Ham dung chung cho image_test.py va conversation_test.py: goi API that cua Backend (Render), dang ky user thu, mua Premium.
Gia tri nhay cam lay tu bien moi truong BASE_URL, API_KEY, APP_SIGNATURE, khong ghi trong file.
"""
import json
import mimetypes
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE_URL = os.environ.get("BASE_URL", "").rstrip("/")
API_KEY = os.environ.get("API_KEY", "")
APP_SIGNATURE = os.environ.get("APP_SIGNATURE", "")
TIMEOUT_SECONDS = 60
PASSWORD = "AiTest@12345"


def require_env():
    if not (BASE_URL and API_KEY and APP_SIGNATURE):
        sys.exit("Thieu bien moi truong BASE_URL, API_KEY hoac APP_SIGNATURE")


def _send(path, data, content_type, token):
    headers = {"Content-Type": content_type, "X-API-Key": API_KEY, "X-App-Signature": APP_SIGNATURE}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method="POST")
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    except OSError as error:
        return 0, {"error": f"{type(error).__name__}: {error}"}, time.perf_counter() - started
    elapsed = time.perf_counter() - started
    try:
        body = json.loads(raw.decode("utf-8"))
    except ValueError:
        body = {"raw": raw.decode("utf-8", "replace")[:300]}
    return status, body, elapsed


def post_json(path, payload, token=None):
    return _send(path, json.dumps(payload).encode("utf-8"), "application/json", token)


def post_image(path, filename, data, token=None):
    boundary = uuid.uuid4().hex
    part_type = mimetypes.guess_type(filename)[0] or "application/octet-stream"
    head = (f'--{boundary}\r\nContent-Disposition: form-data; name="image"; filename="{filename}"\r\n'
            f"Content-Type: {part_type}\r\n\r\n").encode("utf-8")
    body = head + data + f"\r\n--{boundary}--\r\n".encode("utf-8")
    return _send(path, body, f"multipart/form-data; boundary={boundary}", token)


def register_user(prefix):
    """Dang ky 1 user moi (Free); tra ve (email, access token)."""
    email = f"{prefix}.{int(time.time() * 1000)}@example.com"
    status, body, _ = post_json("/api/auth/register", {"displayName": "AI Tester", "email": email, "password": PASSWORD})
    if status != 201:
        sys.exit(f"Dang ky that bai: HTTP {status} {body}")
    return email, body["accessToken"]


def buy_premium(token):
    status, body, _ = post_json("/api/payment/mock-purchase", {"planType": "MONTHLY"}, token)
    if status != 201:
        sys.exit(f"mock-purchase that bai: HTTP {status} {body}")


def is_premium_blocked(status, body):
    return status == 403 and body.get("message") == "PREMIUM_REQUIRED"