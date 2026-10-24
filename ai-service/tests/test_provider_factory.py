import pytest

from providers.external_api import ExternalApiProvider
from providers.factory import PROVIDER_ENV_NAME, create_provider
from providers.mock import MockProvider
from providers.self_hosted import SelfHostedLlmProvider


def test_default_is_self_hosted(monkeypatch):
    monkeypatch.delenv(PROVIDER_ENV_NAME, raising=False)
    assert isinstance(create_provider(), SelfHostedLlmProvider)


@pytest.mark.parametrize(
    "name, expected",
    [("self_hosted", SelfHostedLlmProvider), ("mock", MockProvider), ("external", ExternalApiProvider)],
)
def test_env_selects_provider(monkeypatch, name, expected):
    monkeypatch.setenv(PROVIDER_ENV_NAME, name)
    assert isinstance(create_provider(), expected)


def test_unknown_name_is_rejected(monkeypatch):
    monkeypatch.setenv(PROVIDER_ENV_NAME, "ollama")
    with pytest.raises(ValueError, match="ollama"):
        create_provider()