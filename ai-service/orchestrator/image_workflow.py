import logging

from config.image_vocabulary import OBJECT_MEANINGS, OBJECT_WORDS
from context.context_builder import build_image_examples_prompt
from context.dictionary_lookup import lookup_word
from orchestrator.reply_parsing import is_text, parse_json_object
from providers.base import AiProvider, ProviderError
from providers.yolo_detector import detect_objects

logger = logging.getLogger(__name__)

MAX_OBJECTS = 5


# Khong qua sanitize_input: payload chi co anh, tu nhan dien la ten lop co dinh cua YOLO, khong co van ban cua hoc vien
def image_workflow(provider: AiProvider, payload: dict) -> dict:
    # detect_objects tra theo do tin cay giam dan; moi tu chi giu vat co do tin cay cao nhat
    best = {}
    for detection in detect_objects(payload["image"]):
        best.setdefault(OBJECT_WORDS.get(detection.label, detection.label), detection)
    found = list(best.items())[:MAX_OBJECTS]

    objects = []
    for word, detection in found:
        entry = lookup_word(word)
        objects.append(
            {
                "word": word,
                "ipa": entry["ipa"] if entry else None,
                "meaning": OBJECT_MEANINGS.get(word) or entry["definitions"][0]["meaning"],
                "confidence": detection.confidence,
                "box": {"x": detection.x, "y": detection.y, "width": detection.width, "height": detection.height},
            }
        )

    examples = _examples(provider, objects) if objects else {}
    return {
        "type": "image_identification",
        "objects": [
            {**item, **examples.get(item["word"], {"example": None, "exampleVi": None})} for item in objects
        ],
    }


def _examples(provider: AiProvider, objects: list[dict]) -> dict[str, dict]:
    # Cau vi du chi bo sung: LLM loi (het han muc Cloudflare, JSON sai) thi van tra ket qua nhan dien, khong co cau vi du
    try:
        reply = provider.generate(build_image_examples_prompt(objects))
        items = parse_json_object(reply.text).get("examples")
    except ProviderError as error:
        logger.warning("Khong tao duoc cau vi du cho anh: %s", type(error).__name__)
        return {}
    if not isinstance(items, list):
        return {}
    return {
        item["word"].strip().lower(): {"example": item["example"].strip(), "exampleVi": item["exampleVi"].strip()}
        for item in items
        if isinstance(item, dict) and all(is_text(item.get(key)) for key in ("word", "example", "exampleVi"))
    }