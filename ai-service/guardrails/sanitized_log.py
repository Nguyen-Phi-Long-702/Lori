import json
import logging
import sys

from guardrails.pii_redactor import redact_value

MAX_LOGGED_CHARS = 500
# Chuoi dai hon gioi han nay khong duoc quet/ghi (Backend gioi han 4.000 ky tu moi truong dau vao)
MAX_REDACTED_CHARS = 5_000
# Learning Context la du lieu ca nhan cua hoc vien, khong can cho viec do chat luong AI nen khong ghi
_EXCLUDED_KEYS = {"learningContext"}

# Logging mac dinh chi in tu WARNING tro len; Vercel chi hien dong ghi ra stdout, nen logger nay co handler stdout rieng
logger = logging.getLogger("lori.ai_call")
logger.setLevel(logging.INFO)
logger.addHandler(logging.StreamHandler(sys.stdout))
logger.propagate = False


def _loggable_payload(payload: dict) -> dict:
    return {
        key: f"<{len(value)} bytes>" if isinstance(value, bytes) else value
        for key, value in payload.items()
        if key not in _EXCLUDED_KEYS
    }


def _preview(value) -> str:
    # Che truoc, cat sau: cat truoc co the de lai nua so dien thoai hoac nua email
    text = json.dumps(redact_value(value, MAX_REDACTED_CHARS), ensure_ascii=False)
    return text if len(text) <= MAX_LOGGED_CHARS else text[:MAX_LOGGED_CHARS] + "..."


def log_ai_call(request_id, task_type, model_id, latency_ms, payload, response, error):
    """Lop 7: moi lan goi AI ghi mot dong, noi dung da qua PiiRedactor; khong ghi thong diep cua loi (co the chua noi dung)."""
    logger.info(
        "ai_call id=%s task=%s model=%s status=%s error=%s latency_ms=%d input=%s output=%s",
        request_id,
        task_type,
        model_id,
        "error" if error else "ok",
        error or "-",
        latency_ms,
        _preview(_loggable_payload(payload)),
        _preview(response),
    )