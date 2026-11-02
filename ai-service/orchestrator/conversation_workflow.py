import re

from config.conversation_modes import CONVERSATION_MODES
from context.context_builder import build_conversation_prompt
from guardrails.ai_input_sanitizer import InvalidInputError, sanitize_input
from orchestrator.reply_parsing import is_text, parse_json_object
from providers.base import AiProvider, ProviderError

MAX_FEEDBACK_ITEMS = 3
_FEEDBACK_LISTS = ("grammar", "vocabulary", "naturalness")
# Model hay lap lai nhan "Partner:" cua ban ghi hoi thoai o dau cau tra loi
_SPEAKER_LABEL = re.compile(r"^\s*(Partner|You)\s*:\s*", re.IGNORECASE)


def conversation_workflow(provider: AiProvider, payload: dict) -> dict:
    persona = CONVERSATION_MODES.get(payload["mode"])
    if persona is None:
        raise InvalidInputError("Mode hoi thoai khong hop le")

    # Moi luot do client gui len, ke ca luot cua AI (client co the gia), nen deu phai qua sanitize_input
    turns = [(turn["role"], sanitize_input(turn["content"])) for turn in payload["messages"]]
    finish = payload["finish"]
    if finish and not any(role == "user" for role, _ in turns):
        raise InvalidInputError("Chua co luot nao cua hoc vien de nhan xet")
    if not finish and turns and turns[-1][0] != "user":
        raise InvalidInputError("Luot cuoi phai la cua hoc vien")

    reply = provider.generate(build_conversation_prompt(persona, turns, finish, payload.get("learningContext")))
    if finish:
        return _to_feedback(parse_json_object(reply.text))

    line = _SPEAKER_LABEL.sub("", reply.text).strip()
    if not line:
        raise ProviderError("Provider tra ve cau tra loi rong")
    return {"type": "conversation_reply", "reply": line}


def _to_feedback(data: dict) -> dict:
    summary = data.get("summary")
    if not is_text(summary):
        raise ProviderError("JSON cua provider thieu summary")
    response = {"type": "conversation_feedback", "summary": summary.strip()}
    for key in _FEEDBACK_LISTS:
        response[key] = _items(data.get(key), has_original=True)
    response["usefulPhrases"] = _items(data.get("usefulPhrases"), has_original=False)
    return response


# Muc sai dinh dang bi bo qua, khong bao loi: cac muc con lai van dung duoc
def _items(raw, has_original: bool) -> list[dict]:
    if not isinstance(raw, list):
        return []
    items = []
    for item in raw:
        if not (isinstance(item, dict) and is_text(item.get("better")) and is_text(item.get("explanation"))):
            continue
        if has_original and not is_text(item.get("original")):
            continue
        items.append(
            {
                "original": item["original"].strip() if has_original else None,
                "better": item["better"].strip(),
                "explanation": item["explanation"].strip(),
            }
        )
    return items[:MAX_FEEDBACK_ITEMS]