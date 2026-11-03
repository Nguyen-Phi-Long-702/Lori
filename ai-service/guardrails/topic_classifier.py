import re
import unicodedata

OFF_TOPIC_REPLY = (
    "Mình chỉ hỗ trợ học tiếng Anh: từ vựng, ngữ pháp, phát âm và luyện thi TOEIC/IELTS. "
    "Bạn hãy hỏi mình về tiếng Anh nhé!"
)

# Tu khoa viet thuong, khong dau (xem _fold). Chi liet ke tu gan nhu khong bao gio la doi tuong hoc tieng Anh
_OFF_TOPIC_KEYWORDS = (
    "python", "javascript", "html", "css", "sql", "source code", "viet code", "write code", "coding", "lap trinh",
    "thuat toan", "algorithm",
    "bitcoin", "crypto", "forex", "tien ao", "chung khoan", "co phieu",
    "phuong trinh", "dao ham", "tich phan", "giai toan", "bai tap toan",
    "chinh tri", "bau cu", "tong thong",
    "thoi tiet", "ket qua bong da", "lich thi dau",
    "nau an", "cong thuc nau",
    "hack wifi", "hack tai khoan", "malware", "ddos",
)

# Co mot dau hieu hoc tieng Anh thi cho qua: "What does 'bitcoin' mean?" la cau hoi tu vung hop le
_ON_TOPIC_KEYWORDS = (
    "english", "tieng anh", "grammar", "ngu phap", "vocabulary", "vocab", "tu vung", "meaning", "nghia",
    "pronounce", "pronunciation", "phat am", "ielts", "toeic", "translate", "dich", "synonym", "dong nghia",
    "antonym", "trai nghia", "idiom", "thanh ngu", "collocation", "tense", "sentence", "phrase", "difference",
    "khac nhau", "spelling", "how to say",
)


def _fold(text: str) -> str:
    # Bo dau tieng Viet (d gach khong phan tach duoc nen doi rieng) de mot danh sach khop ca chu co dau lan khong dau
    decomposed = unicodedata.normalize("NFD", text.lower().replace("đ", "d"))
    return "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")


def _keyword_pattern(keywords) -> re.Pattern:
    return re.compile(r"\b(?:" + "|".join(re.escape(word) for word in keywords) + r")\b")


_OFF_TOPIC = _keyword_pattern(_OFF_TOPIC_KEYWORDS)
_ON_TOPIC = _keyword_pattern(_ON_TOPIC_KEYWORDS)


def is_off_topic(text: str) -> bool:
    """True khi cau co tu khoa lac de va khong co dau hieu hoc tieng Anh. text da qua sanitize_input."""
    folded = _fold(text)
    return bool(_OFF_TOPIC.search(folded)) and not _ON_TOPIC.search(folded)