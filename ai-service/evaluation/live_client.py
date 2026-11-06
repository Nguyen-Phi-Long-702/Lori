import json
import time
import urllib.error
import urllib.request

from guardrails.pii_redactor import redact_value
from guardrails.s2s_auth import SIGNATURE_HEADER, TIMESTAMP_HEADER, compute_signature

BASE_URL_ENV = "AI_SERVICE_BASE_URL"
# Lon hon 25 giay timeout cua provider de nhan duoc loi 502 cua AI Service thay vi tu bo cuoc truoc
TIMEOUT_SECONDS = 60


class LiveResponse:
    def __init__(self, status: int, body: dict, latency_ms: int):
        self.status = status
        self.body = body
        self.latency_ms = latency_ms


class LiveClient:
    """Goi AI Service that qua HTTPS, ky HMAC dung nhu Backend; moi lan goi duoc ghi lai (da che thong tin nhay cam)."""

    def __init__(self, base_url: str, secret: str):
        self._base_url = base_url.rstrip("/")
        self._secret = secret
        self.calls = []
        self.current_test = None

    def request(
        self,
        method: str,
        path: str,
        payload: dict | None = None,
        signed: bool = True,
        image: tuple[str, bytes] | None = None,
    ) -> LiveResponse:
        # Anh gui nguyen byte (khong phai JSON), giong Backend; ban ghi chi giu ten file va kich thuoc
        body = image[1] if image else (b"" if payload is None else json.dumps(payload).encode("utf-8"))
        headers = {"Content-Type": "application/octet-stream" if image else "application/json", "User-Agent": "lori-ai-eval/1.0"}
        if signed:
            timestamp = str(int(time.time()))
            headers[TIMESTAMP_HEADER] = timestamp
            headers[SIGNATURE_HEADER] = compute_signature(self._secret, timestamp, method, path, body)
        request = urllib.request.Request(self._base_url + path, data=body or None, headers=headers, method=method)

        started = time.perf_counter()
        try:
            with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        except OSError as error:
            status, raw = 0, json.dumps({"error": type(error).__name__}).encode("utf-8")
        latency_ms = int((time.perf_counter() - started) * 1000)

        try:
            parsed = json.loads(raw.decode("utf-8"))
        except ValueError:
            parsed = {"raw": raw.decode("utf-8", "replace")[:300]}
        self.calls.append(
            {
                "test": self.current_test,
                "request": f"{method} {path}",
                "input": {"file": image[0], "bytes": len(image[1])} if image else redact_value(payload),
                "status": status,
                "latency_ms": latency_ms,
                "output": redact_value(parsed),
            }
        )
        return LiveResponse(status, parsed, latency_ms)