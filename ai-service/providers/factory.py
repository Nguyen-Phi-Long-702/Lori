import os

from providers.base import AiProvider
from providers.external_api import ExternalApiProvider
from providers.mock import MockProvider
from providers.self_hosted import SelfHostedLlmProvider

PROVIDER_ENV_NAME = "AI_PROVIDER"
DEFAULT_PROVIDER = "self_hosted"

PROVIDERS = {
    SelfHostedLlmProvider.name: SelfHostedLlmProvider,
    MockProvider.name: MockProvider,
    ExternalApiProvider.name: ExternalApiProvider,
}


def create_provider() -> AiProvider:
    name = os.environ.get(PROVIDER_ENV_NAME, DEFAULT_PROVIDER)
    if name not in PROVIDERS:
        raise ValueError(f"{PROVIDER_ENV_NAME} khong hop le: {name}. Chon mot trong {sorted(PROVIDERS)}")
    return PROVIDERS[name]()