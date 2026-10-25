import json
from collections.abc import Mapping

from context.context_builder import build_grammar_prompt
from guardrails.ai_input_sanitizer import sanitize_input
from providers.base import AiProvider, ProviderError


def grammar_workflow(provider: AiProvider, payload: dict) -> dict:
    # Thu tu bat buoc: sanitize_input truoc, build_grammar_prompt sau (xem docstring build_prompt)
    sentence = sanitize_input(payload["text"])
    lessons = payload["lessons"]
    reply = provider.generate(build_grammar_prompt(sentence, lessons, payload.get("learningContext")))
    return _to_response(_parse_json(reply.text), lessons)


def _parse_json(text: str) -> dict:
    # Model co the boc JSON trong khoi ```json, nen lay tu "{" dau den "}" cuoi
    try:
        data = json.loads(text[text.find("{") : text.rfind("}") + 1])
    except ValueError:
        raise ProviderError("Provider khong tra JSON hop le") from None
    if not isinstance(data, dict):
        raise ProviderError("Provider khong tra JSON object")
    return data


def _is_text(value) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _to_response(data: dict, lessons: Mapping[int, str]) -> dict:
    correct_flag = data.get("correct")
    corrected = data.get("correctedSentence")
    errors = data.get("errors")
    if not isinstance(correct_flag, bool) or not _is_text(corrected) or not isinstance(errors, list):
        raise ProviderError("JSON cua provider thieu truong bat buoc")

    response = {
        "type": "grammar_analysis",
        "correct": correct_flag,
        "correctedSentence": corrected.strip(),
        "errors": [],
        "lessonId": None,
    }
    if correct_flag:
        return response

    for error in errors:
        if not (isinstance(error, dict) and _is_text(error.get("type")) and _is_text(error.get("explanation"))):
            raise ProviderError("JSON cua provider co loi sai dinh dang")
        response["errors"].append({"type": error["type"].strip(), "explanation": error["explanation"].strip()})
    if not response["errors"]:
        raise ProviderError("Provider bao cau sai nhung khong neu loi nao")

    # Model chi duoc chon bai co that; id sai kieu hoac khong co trong danh sach thi bo lien ket, khong bao loi
    lesson_id = data.get("lessonId")
    if isinstance(lesson_id, int) and not isinstance(lesson_id, bool) and lesson_id in lessons:
        response["lessonId"] = lesson_id
    return response