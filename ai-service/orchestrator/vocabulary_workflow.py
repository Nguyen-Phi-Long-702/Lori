import re

from context.context_builder import build_vocabulary_prompt
from context.dictionary_lookup import lookup_word
from guardrails.ai_input_sanitizer import InvalidInputError, sanitize_input
from orchestrator.reply_parsing import is_text, parse_json_object
from providers.base import AiProvider, ProviderError

MAX_TERMS = 2
MAX_TERM_LENGTH = 40
MAX_LIST_ITEMS = 5
_SEPARATOR = re.compile(r",|/|\bvs\b\.?", re.IGNORECASE)


def vocabulary_workflow(provider: AiProvider, payload: dict) -> dict:
    # Thu tu bat buoc: sanitize_input truoc, build_vocabulary_prompt sau (xem docstring build_prompt)
    text = sanitize_input(payload["text"])
    terms = _parse_terms(text)
    # Tra tu dien Lori truoc, nghia chuan lay tu day; LLM chi giai thich them
    entries = {term: lookup_word(term) for term in terms}
    reply = provider.generate(build_vocabulary_prompt(text, entries, payload.get("learningContext")))
    return _to_response(parse_json_object(reply.text), terms, entries)


def _parse_terms(text: str) -> list[str]:
    terms = [" ".join(part.lower().split()) for part in _SEPARATOR.split(text)]
    terms = list(dict.fromkeys(term for term in terms if term))
    if not terms or len(terms) > MAX_TERMS or any(len(term) > MAX_TERM_LENGTH for term in terms):
        raise InvalidInputError("Can 1 hoac 2 tu/cum tu, moi tu/cum tu toi da 40 ky tu")
    return terms


def _text_list(value) -> list[str]:
    # Danh sach sai kieu thi coi nhu rong, khong bao loi: phan nay chi bo sung cho tu dien
    if not isinstance(value, list):
        return []
    return [item.strip() for item in value if is_text(item)][:MAX_LIST_ITEMS]


def _to_response(data: dict, terms: list[str], entries: dict) -> dict:
    items = data.get("words")
    if not isinstance(items, list):
        raise ProviderError("JSON cua provider thieu danh sach words")
    by_word = {
        item["word"].strip().lower(): item for item in items if isinstance(item, dict) and is_text(item.get("word"))
    }

    words = []
    for term in terms:
        item = by_word.get(term)
        if item is None or not is_text(item.get("explanation")):
            raise ProviderError(f"JSON cua provider thieu giai thich cho tu: {term}")
        entry = entries[term]
        words.append(
            {
                "word": term,
                "inDictionary": entry is not None,
                "ipa": entry["ipa"] if entry else None,
                "definitions": entry["definitions"] if entry else [],
                "explanation": item["explanation"].strip(),
                # Tu dien co thi dung tu dien, khong co moi lay cua model
                "synonyms": (entry["synonyms"] if entry else []) or _text_list(item.get("synonyms")),
                "antonyms": (entry["antonyms"] if entry else []) or _text_list(item.get("antonyms")),
                "collocations": _text_list(item.get("collocations")),
            }
        )

    difference = data.get("difference")
    if len(terms) == 2 and not is_text(difference):
        raise ProviderError("Provider khong giai thich su khac nhau cua 2 tu")
    return {
        "type": "vocabulary_analysis",
        "words": words,
        "difference": difference.strip() if len(terms) == 2 else None,
    }