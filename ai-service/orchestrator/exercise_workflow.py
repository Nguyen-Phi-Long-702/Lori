import re

from context.context_builder import EXERCISE_COUNT, build_exercise_prompt
from orchestrator.reply_parsing import is_text, parse_json_object
from providers.base import AiProvider, ProviderError

OPTION_COUNT = 4
MAX_ANSWERS = 3
_BLANK = re.compile(r"_{3,}")


# Khong qua sanitize_input: payload la du lieu co cau truc do Backend dung, khong co van ban tu do cua hoc vien
def exercise_workflow(provider: AiProvider, payload: dict) -> dict:
    reply = provider.generate(build_exercise_prompt(payload))
    items = parse_json_object(reply.text).get("exercises")
    if not isinstance(items, list):
        raise ProviderError("JSON cua provider thieu danh sach exercises")

    seen = {_key(question) for question in payload.get("avoidQuestions") or []}
    exercises = []
    for item in items:
        exercise = _to_exercise(item)
        # Bai sai dinh dang hoac lap lai cau da co thi bo qua, khong bao loi: cac bai con lai van dung duoc
        if exercise is None or _key(exercise["question"]) in seen:
            continue
        seen.add(_key(exercise["question"]))
        exercises.append(exercise)
    if not exercises:
        raise ProviderError("Provider khong tra bai tap hop le nao")
    return {"type": "exercise_set", "exercises": exercises[:EXERCISE_COUNT]}


def _key(text: str) -> str:
    return " ".join(text.lower().split())


def _to_exercise(item) -> dict | None:
    if not (isinstance(item, dict) and is_text(item.get("question")) and is_text(item.get("explanation"))):
        return None
    exercise = {
        "type": item.get("type"),
        "question": item["question"].strip(),
        "explanation": item["explanation"].strip(),
    }
    if exercise["type"] == "multiple_choice":
        return _multiple_choice(exercise, item)
    if exercise["type"] == "fill_blank":
        return _fill_blank(exercise, item)
    return None


def _multiple_choice(exercise: dict, item: dict) -> dict | None:
    options = item.get("options")
    index = item.get("answerIndex")
    if not (isinstance(options, list) and len(options) == OPTION_COUNT and all(is_text(option) for option in options)):
        return None
    options = [option.strip() for option in options]
    if len({_key(option) for option in options}) != OPTION_COUNT:
        return None
    if not (isinstance(index, int) and not isinstance(index, bool) and 0 <= index < OPTION_COUNT):
        return None
    return {**exercise, "options": options, "answerIndex": index}


def _fill_blank(exercise: dict, item: dict) -> dict | None:
    answers = item.get("answers")
    if len(_BLANK.findall(exercise["question"])) != 1 or not isinstance(answers, list):
        return None
    cleaned = list(dict.fromkeys(answer.strip() for answer in answers if is_text(answer)))[:MAX_ANSWERS]
    if not cleaned:
        return None
    return {**exercise, "question": _BLANK.sub("___", exercise["question"]), "answers": cleaned}