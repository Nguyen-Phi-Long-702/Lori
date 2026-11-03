import html
import re
import unicodedata
from urllib.parse import unquote

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

# Mau tan cong ro rang (SQL injection, lenh he thong / ma, path traversal). Chi chua mau du hep de cau tieng Anh binh thuong
# nhu "delete from the list" hay "select the correct answer" khong bi chan. Chay tren ban da giai ma %XX, chu thuong.
_UNSAFE_PATTERNS = tuple(
    re.compile(pattern)
    for pattern in (
        r"\bunion\s+(all\s+)?select\b",
        r"\b(drop|truncate)\s+(table|database|schema)\b",
        r";\s*(drop|delete|insert|update|alter|truncate)\b",
        r"\binsert\s+into\s+\w+\s*(\(|values\b)",
        r"\bupdate\s+\w+\s+set\s+\w+\s*=",
        r"\b(or|and)\s+['\"]?(\d+)['\"]?\s*=\s*['\"]?\2\b",
        r"\b(information_schema|pg_sleep|xp_cmdshell)\b|\binto\s+outfile\b",
        r"\b(eval|exec)\s*\(",
        r"\bos\.(system|popen)\b|\bsubprocess\.|__import__|\bimport\s+(os|subprocess|socket)\b",
        r"\brm\s+-[rf]+\b|\b(curl|wget)\s+\S*://|\|\s*(sh|bash)\b",
        r"\$\(|\$\{|\{\{|<\?php|<%",
        r"(?<!\.)\.\.[/\\]",
        r"(?:^|[\s\"'=:(])/(?:etc|proc|root|var)/",
        r"\b[a-z]:[/\\](?:windows|users)\b|\bfile://",
    )
)

_SPACES = re.compile(r"[ \t]+")
_BLANK_LINES = re.compile(r"\n{3,}")


class InvalidInputError(Exception):
    """Input rong sau khi lam sach, hoac chua mau tan cong."""


def sanitize_input(text: str) -> str:
    # Giai ma entity truoc, roi NFKC (ky tu full-width thanh ASCII), de khong the lach bo loc bang cach ma hoa
    text = html.unescape(text[:MAX_RAW_LENGTH])
    text = unicodedata.normalize("NFKC", text)[:MAX_RAW_LENGTH]

    # Bo ky tu bat thuong truoc khi bo HTML/code, de ky tu vo hinh chen giua khong lam hong regex
    text = text.replace("\t", " ")
    text = "".join(ch for ch in text if ch == "\n" or unicodedata.category(ch) not in _BLOCKED_CATEGORIES)

    # Phat hien truoc khi bo khoi code/HTML, de mau tan cong nam trong khoi code van bi chan
    if any(pattern.search(unquote(unquote(text)).lower()) for pattern in _UNSAFE_PATTERNS):
        raise InvalidInputError("Input chua mau tan cong")

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