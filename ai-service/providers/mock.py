from providers.base import AiProvider, LlmResponse


class MockProvider(AiProvider):
    name = "mock"

    def __init__(self, reply: str = "This is a mock reply."):
        self._reply = reply

    def generate(self, prompt: str) -> LlmResponse:
        return LlmResponse(
            text=self._reply,
            model_id="mock",
            prompt_tokens=0,
            completion_tokens=0,
            truncated=False,
        )

    def is_available(self) -> bool:
        return True