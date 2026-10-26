import json

from providers.base import ProviderError


def parse_json_object(text: str) -> dict:
    # Model co the boc JSON trong khoi ```json, nen lay tu "{" dau den "}" cuoi
    try:
        data = json.loads(text[text.find("{") : text.rfind("}") + 1])
    except ValueError:
        raise ProviderError("Provider khong tra JSON hop le") from None
    if not isinstance(data, dict):
        raise ProviderError("Provider khong tra JSON object")
    return data


def is_text(value) -> bool:
    return isinstance(value, str) and bool(value.strip())