import hashlib
import hmac
import logging
import os
import time

from fastapi import HTTPException, Request

logger = logging.getLogger(__name__)

TIMESTAMP_HEADER = "X-Lori-Timestamp"
SIGNATURE_HEADER = "X-Lori-Signature"
SECRET_ENV_NAME = "AI_SERVICE_SHARED_SECRET"
MIN_SECRET_LENGTH = 32
MAX_CLOCK_SKEW_SECONDS = 300
MAX_TIMESTAMP_LENGTH = 12


def compute_signature(secret: str, timestamp: str, method: str, path: str, body: bytes) -> str:
    body_hash = hashlib.sha256(body).hexdigest()
    canonical = f"{timestamp}\n{method.upper()}\n{path}\n{body_hash}"
    return hmac.new(secret.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256).hexdigest()


def _unauthorized() -> HTTPException:
    return HTTPException(status_code=401, detail="Unauthorized")


async def verify_backend_request(request: Request) -> None:
    # Thieu hoac qua ngan secret -> tu choi moi request (fail closed)
    secret = os.environ.get(SECRET_ENV_NAME, "")
    if len(secret) < MIN_SECRET_LENGTH:
        logger.error("%s chua duoc cau hinh hoac ngan hon %d ky tu", SECRET_ENV_NAME, MIN_SECRET_LENGTH)
        raise _unauthorized()

    timestamp = request.headers.get(TIMESTAMP_HEADER)
    signature = request.headers.get(SIGNATURE_HEADER)
    if not timestamp or not signature:
        raise _unauthorized()
    if not (timestamp.isascii() and timestamp.isdigit() and len(timestamp) <= MAX_TIMESTAMP_LENGTH):
        raise _unauthorized()
    if abs(time.time() - int(timestamp)) > MAX_CLOCK_SKEW_SECONDS:
        raise _unauthorized()

    body = await request.body()
    expected = compute_signature(secret, timestamp, request.method, request.url.path, body)
    # So sanh thoi gian khong doi, tranh lo thong tin qua do tre
    if not hmac.compare_digest(expected.encode("ascii"), signature.encode("utf-8")):
        raise _unauthorized()