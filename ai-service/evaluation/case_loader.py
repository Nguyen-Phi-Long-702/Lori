import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATASET_DIR = HERE / "dataset"
IMAGE_DIR = HERE / "images"
# Cung file ma Backend dung de gui danh sach bai ngu phap cho AI Service (LearningContextService)
LEARNING_NAMES = HERE.parents[1] / "backend" / "src" / "main" / "resources" / "ai" / "learning-names.json"
CHAT_KINDS = ("chat_context", "blocked", "off_topic", "refuse", "on_topic")


def lessons() -> dict[int, str]:
    names = json.loads(LEARNING_NAMES.read_text(encoding="utf-8"))["grammarLessons"]
    return {int(lesson_id): title for lesson_id, title in names.items()}


def load_cases() -> list[dict]:
    cases = []
    # Moi file dataset/<nhom>.json la mot nhom case; ten file la ten nhom
    for path in sorted(DATASET_DIR.glob("*.json")):
        for case in json.loads(path.read_text(encoding="utf-8")):
            cases.append({**case, "group": path.stem})
    return cases


def build_request(case: dict) -> tuple[str, dict | None, tuple[str, bytes] | None]:
    """Tra ve (duong dan, body JSON, anh) giong request Backend gui sang AI Service; anh la (ten file, byte)."""
    kind = case["kind"]
    if kind == "grammar":
        return "/ai/grammar", {"text": case["text"], "lessons": lessons()}, None
    if kind == "vocabulary":
        return "/ai/vocabulary", {"text": case["text"]}, None
    if kind == "quiz":
        return "/ai/analyze-quiz", {**case["payload"], "lessons": lessons()}, None
    if kind == "exam":
        return "/ai/analyze-exam", {**case["payload"], "lessons": lessons()}, None
    if kind == "recommend":
        return "/ai/recommend", {"weakTopics": case["weakTopics"], "lessons": lessons()}, None
    if kind in CHAT_KINDS:
        return "/ai/chat", {"message": case["message"], "learningContext": case.get("context")}, None
    if kind == "image":
        return "/ai/identify-image", None, (case["file"], (IMAGE_DIR / case["file"]).read_bytes())
    raise ValueError(f"kind khong hop le: {kind}")