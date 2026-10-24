from providers.base import AiProvider, LlmResponse, ProviderError


class ExternalApiProvider(AiProvider):
    # Chi la cho trong, chi dung de thu nghiem ca nhan, khong phai phu thuoc cua MVP
    name = "external"

    def generate(self, prompt: str) -> LlmResponse:
        raise ProviderError("ExternalApiProvider chua duoc cai dat")

    def is_available(self) -> bool:
        return False