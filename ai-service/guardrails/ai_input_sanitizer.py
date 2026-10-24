import html
import re
import unicodedata

MAX_INPUT_LENGTH = 1000
# Cat som de regex ben duoi khong phai quet chuoi qua dai (tranh ton CPU cua Vercel)
MAX_RAW_LENGTH = MAX_INPUT_LENGTH * 4

# Cc: ky tu dieu khien; Cf: ky tu vo hinh (zero-width, dao chieu chu, tag); Co: private use;
# Cs: surrogate; Cn: chua duoc gan; Zl/Zp: ngat dong/ngat doan cua Unicode
_BLOCKED_CATEGORIES = {"Cc", "Cf", "Co", "Cs", "Cn", "Zl", "Zp"}

_FENCED_CODE = re.compile(r"```.*?```", re.DOTALL)
_SCRIPT_OR_STYLE = re.compile(r"<(script|style)\b.*?</\1\s*>", re.DOTALL | re.IGNORECASE)
# Chi coi la the khi sau "<" la chu cai, "/" hoac "!", de phep so sanh nhu "3 < 5" khong bi nuot mat chu
_HTML_TAG = re.compile(r"</?[A-Za-z!][^>]*>")
_LEFTOVER = re.compile(r"[<>`]")
_SPACES = re.compile(r"[ \t]+")
_BLANK_LINES = re.compile(r"\n{3,}")


class InvalidInputError(Exception):
    """Input rong sau khi lam sach."""


def sanitize_input(text: str) -> str:
    # Giai ma entity truoc, roi NFKC (ky tu full-width thanh ASCII), de khong the lach bo loc bang cach ma hoa
    text = html.unescape(text[:MAX_RAW_LENGTH])
    text = unicodedata.normalize("NFKC", text)[:MAX_RAW_LENGTH]

    # Bo ky tu bat thuong truoc khi bo HTML/code, de ky tu vo hinh chen giua khong lam hong regex
    text = text.replace("\t", " ")
    text = "".join(ch for ch in text if ch == "\n" or unicodedata.category(ch) not in _BLOCKED_CATEGORIES)

    text = _FENCED_CODE.sub(" ", text)
    text = _SCRIPT_OR_STYLE.sub(" ", text)
    text = _HTML_TAG.sub(" ", text)
    text = _LEFTOVER.sub("", text)

    text = _SPACES.sub(" ", text)
    text = "\n".join(line.strip() for line in text.split("\n"))
    text = _BLANK_LINES.sub("\n\n", text).strip()

    text = text[:MAX_INPUT_LENGTH].rstrip()
    if not text:
        raise InvalidInputError("Input rong sau khi lam sach")
    return text