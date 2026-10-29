from collections.abc import Mapping

from context.context_builder import build_exam_analysis_prompt, build_quiz_analysis_prompt
from orchestrator.reply_parsing import is_text, parse_json_object
from providers.base import AiProvider, ProviderError

MAX_ITEMS = 3


# Khong qua sanitize_input: payload la du lieu co cau truc do Backend dung tu ket qua da cham, khong co van ban tu do cua hoc vien
def quiz_analysis_workflow(provider: AiProvider, payload: dict) -> dict:
    reply = provider.generate(build_quiz_analysis_prompt(payload))
    return _to_response("quiz_analysis", parse_json_object(reply.text), payload["lessons"])


def exam_analysis_workflow(provider: AiProvider, payload: dict) -> dict:
    reply = provider.generate(build_exam_analysis_prompt(payload))
    return _to_response("exam_analysis", parse_json_object(reply.text), payload["lessons"])


def _to_response(response_type: str, data: dict, lessons: Mapping[int, str]) -> dict:
    summary = data.get("summary")
    patterns = data.get("patterns")
    recommendations = data.get("recommendations")
    if not is_text(summary) or not isinstance(patterns, list) or not isinstance(recommendations, list):
        raise ProviderError("JSON cua provider thieu truong bat buoc")

    items = []
    for item in recommendations[:MAX_ITEMS]:
        if not (isinstance(item, dict) and is_text(item.get("title")) and is_text(item.get("reason"))):
            raise ProviderError("JSON cua provider co de xuat sai dinh dang")
        # Model chi duoc chon bai co that; id sai kieu hoac khong co trong danh sach thi bo lien ket, khong bao loi
        lesson_id = item.get("lessonId")
        valid_lesson = isinstance(lesson_id, int) and not isinstance(lesson_id, bool) and lesson_id in lessons
        items.append(
            {
                "title": item["title"].strip(),
                "reason": item["reason"].strip(),
                "lessonId": lesson_id if valid_lesson else None,
            }
        )
    return {
        "type": response_type,
        "summary": summary.strip(),
        # Diem tuyet doi co the khong co loi nao de neu, nen patterns va recommendations duoc phep rong
        "patterns": [pattern.strip() for pattern in patterns if is_text(pattern)][:MAX_ITEMS],
        "recommendations": items,
    }