import pytest

from providers.base import AiProvider, LlmResponse, ProviderError
from providers.external_api import ExternalApiProvider
from providers.mock import MockProvider


def test_mock_provider_returns_response_with_full_schema():
    response = MockProvider(reply="Hello").generate("Any prompt")
    assert response == LlmResponse(
        text="Hello", model_id="mock", prompt_tokens=0, completion_tokens=0, truncated=False
    )


def test_mock_provider_is_available():
    assert MockProvider().is_available() is True


def test_external_provider_is_a_stub_that_always_fails():
    provider = ExternalApiProvider()
    assert provider.is_available() is False
    with pytest.raises(ProviderError):
        provider.generate("Any prompt")


def test_providers_follow_the_interface():
    assert isinstance(MockProvider(), AiProvider)
    assert isinstance(ExternalApiProvider(), AiProvider)