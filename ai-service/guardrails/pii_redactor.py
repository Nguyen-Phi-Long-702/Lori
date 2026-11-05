import re

REDACTED = "[redacted]"

EMAIL_PATTERN = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+")
# shortcut: so dien thoai chi nhan dang dang 0xxxxxxxxx, 0xxx xxx xxx (dau cach/cham/gach ngang) hoac +<ma nuoc> ...;
# dang (xxx) xxx-xxxx cua My khong bi bat. (?<!\d[ .-]) de nhom cuoi cua so ngan cach hang nghin ("1 000 000 000") khong bi coi la SDT
PHONE_PATTERN = re.compile(
    r"(?<!\w)(?<!\d[ .-])(?:\+\d{1,3}(?:[ .-]?\d){8,12}|0\d{9,10}|0\d{2,3}[ .-]\d{3,4}[ .-]\d{3,4})(?!\w)"
)

_JWT = re.compile(r"\beyJ[\w-]{5,}\.[\w-]{5,}\.[\w-]*")
# Token tu 8 ky tu tro len, de cau tieng Anh thuong nhu "the bearer of bad news" khong bi che
_BEARER = re.compile(r"\bBearer\s+[\w.~+/-]{8,}=*", re.IGNORECASE)
# Chuoi ket noi co tai khoan: postgres://user:matkhau@host -> postgres://[redacted]@host
_URL_CREDENTIALS = re.compile(r"(?<=://)[^\s/@:]+:[^\s/@]+@")
# Giu ten truong va dau phan cach, chi che gia tri: password=abc, "api_key": "abc", token: abc
_SECRET_FIELD = re.compile(
    r"""\b(password|passwd|pwd|secret|token|api[_-]?key|authorization)\b(["']?\s*[:=]\s*["']?)[^\s"',;}]+""",
    re.IGNORECASE,
)
# Day 13-19 chu so (cho phep cach/gach ngang); chi che khi qua phep kiem tra Luhn de khong che nham so thuong
_CARD = re.compile(r"(?<!\d)\d(?:[ -]?\d){12,18}(?!\d)")


def _is_card_number(digits: str) -> bool:
    total = 0
    for index, char in enumerate(reversed(digits)):
        value = int(char)
        if index % 2:
            value = value * 2 - 9 if value > 4 else value * 2
        total += value
    return total % 10 == 0


def _redact_card(match: re.Match) -> str:
    digits = re.sub(r"\D", "", match.group())
    return REDACTED if _is_card_number(digits) else match.group()


def redact(text: str) -> str:
    """Che thong tin nhay cam (JWT, token, mat khau, chuoi ket noi, so the, email, so dien thoai) truoc khi ghi log."""
    text = _JWT.sub(REDACTED, text)
    text = _BEARER.sub("Bearer " + REDACTED, text)
    text = _URL_CREDENTIALS.sub(REDACTED + "@", text)
    text = _SECRET_FIELD.sub(r"\g<1>\g<2>" + REDACTED, text)
    text = _CARD.sub(_redact_card, text)
    text = EMAIL_PATTERN.sub(REDACTED, text)
    return PHONE_PATTERN.sub(REDACTED, text)


def redact_value(value, max_length=None):
    """Che moi chuoi trong dict/list long nhau, giu nguyen cau truc; gia tri khong phai chuoi giu nguyen.

    Chuoi dai hon max_length bi thay bang "<N chars omitted>" thay vi quet: cac mau tren co the chay cham (binh phuong
    theo do dai) tren chuoi rat dai khong co ky tu phan cach.
    """
    if isinstance(value, str):
        if max_length is not None and len(value) > max_length:
            return f"<{len(value)} chars omitted>"
        return redact(value)
    if isinstance(value, list):
        return [redact_value(item, max_length) for item in value]
    if isinstance(value, dict):
        return {key: redact_value(item, max_length) for key, item in value.items()}
    return value