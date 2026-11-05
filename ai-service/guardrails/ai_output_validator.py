import logging
import re

from guardrails.pii_redactor import EMAIL_PATTERN, PHONE_PATTERN, REDACTED
from providers.base import ProviderError

logger = logging.getLogger(__name__)

# Email va so dien thoai chi bi thay the: phan con lai cua cau tra loi van dung duoc.
_REDACT_PATTERNS = (EMAIL_PATTERN, PHONE_PATTERN)

# Mau chi can xuat hien la chan ca response. Viet hep de cau tieng Anh thuong nhu "Select one from the list" khong bi chan.
_BLOCK_PATTERNS = (
    ("sql", r"\bselect\s+\*\s+from\s+\w+"),
    ("sql", r"\bselect\s+[\w.]+(?:\s*,\s*[\w.]+)*\s+from\s+\w+\s*(?:where|join|group\s+by|order\s+by|limit)\b"),
    ("sql", r"\binsert\s+into\s+\w+\s*(?:\(|values\b)"),
    ("sql", r"\b(?:drop|truncate|alter)\s+(?:table|database)\s+\w+"),
    ("sql", r"\bdelete\s+from\s+\w+\s+where\b"),
    ("sql", r"\bupdate\s+\w+\s+set\s+\w+\s*="),
    ("system_path", r"(?<!\w)/(?:etc|proc|root|var|usr|home|bin|sbin|tmp|opt|sys|dev|mnt|vercel)/\w"),
    ("system_path", r"\b[a-z]:[/\\](?:windows|users|program files|programdata)\b"),
    ("system_path", r"\bfile://"),
    ("system_json", r"\"role\"\s*:\s*\"system\""),
    (
        "system_json",
        r"\"(?:weakGrammar|weakVocabulary|recentActivity|recentExams|learningContext|userId|api[_-]?key|password"
        r"|secret|access[_-]?token|authorization|system[_-]?prompt)\"\s*:",
    ),
)
_BLOCK_COMPILED = tuple((reason, re.compile(pattern, re.IGNORECASE)) for reason, pattern in _BLOCK_PATTERNS)


class OutputBlockedError(ProviderError):
    """Response chua mau nhay cam (SQL, duong dan he thong, JSON he thong); AI Service tra 502 nhu loi provider."""


def validate_output(response):
    """Quet moi chuoi trong response (dict/list long nhau): chan khi co SQL/duong dan/JSON he thong, thay email/SDT."""
    if isinstance(response, str):
        return _validate_text(response)
    if isinstance(response, list):
        return [validate_output(item) for item in response]
    if isinstance(response, dict):
        return {key: validate_output(value) for key, value in response.items()}
    return response


def _validate_text(text: str) -> str:
    for reason, pattern in _BLOCK_COMPILED:
        if pattern.search(text):
            # Chi ghi loai mau, khong ghi noi dung (co the chua thong tin ca nhan)
            logger.warning("AiOutputValidator chan response: %s", reason)
            raise OutputBlockedError(f"Response chua mau nhay cam: {reason}")
    for pattern in _REDACT_PATTERNS:
        text = pattern.sub(REDACTED, text)
    return text