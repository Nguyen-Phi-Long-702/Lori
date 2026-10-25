from context.context_builder import build_prompt
from guardrails.ai_input_sanitizer import sanitize_input
from providers.base import AiProvider, ProviderError


def chat_workflow(provider: AiProvider, payload: dict) -> dict:
    # Thu tu bat buoc: sanitize_input truoc, build_prompt sau (xem docstring build_prompt)
    message = sanitize_input(payload["message"])
    reply = provider.generate(build_prompt(message, payload.get("learningContext")))
    if not reply.text:
        raise ProviderError("Provider tra ve cau tra loi rong")
    return {"type": "chat", "answer": reply.text, "truncated": reply.truncated}