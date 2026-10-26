from collections.abc import Mapping

from context.context_builder import build_grammar_prompt
from guardrails.ai_input_sanitizer import sanitize_input
from orchestrator.reply_parsing import is_text, parse_json_object
from providers.base import AiProvider, ProviderError


def grammar_workflow(provider: AiProvider, payload: dict) -> dict:
    # Thu tu bat buoc: sanitize_input truoc, build_grammar_prompt sau (xem docstring build_prompt)
    sentence = sanitize_input(payload["text"])
    lessons = payload["lessons"]
    reply = provider.generate(build_grammar_prompt(sentence, lessons, payload.get("learningContext")))
    return _to_response(parse_json_object(reply.text), lessons)


def _to_response(data: dict, lessons: Mapping[int, str]) -> dict:
    correct_flag = data.get("correct")
    corrected = data.get("correctedSentence")
    errors = data.get("errors")
    if not isinstance(correct_flag, bool) or not is_text(corrected) or not isinstance(errors, list):
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
        if not (isinstance(error, dict) and is_text(error.get("type")) and is_text(error.get("explanation"))):
            raise ProviderError("JSON cua provider co loi sai dinh dang")
        response["errors"].append({"type": error["type"].strip(), "explanation": error["explanation"].strip()})
    if not response["errors"]:
        raise ProviderError("Provider bao cau sai nhung khong neu loi nao")

    # Model chi duoc chon bai co that; id sai kieu hoac khong co trong danh sach thi bo lien ket, khong bao loi
    lesson_id = data.get("lessonId")
    if isinstance(lesson_id, int) and not isinstance(lesson_id, bool) and lesson_id in lessons:
        response["lessonId"] = lesson_id
    return response