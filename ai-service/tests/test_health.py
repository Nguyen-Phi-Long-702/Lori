import time

import pytest
from fastapi.testclient import TestClient

from guardrails.s2s_auth import SECRET_ENV_NAME, SIGNATURE_HEADER, TIMESTAMP_HEADER, compute_signature
from main import app
from orchestrator.ai_orchestrator import AiOrchestrator
from providers.external_api import ExternalApiProvider
from providers.mock import MockProvider

SECRET = "lori-test-shared-secret-0123456789abcdef"
PATH = "/ai/health"

client = TestClient(app)


@pytest.fixture(autouse=True)
def configured_secret(monkeypatch):
    monkeypatch.setenv(SECRET_ENV_NAME, SECRET)


def use_provider(monkeypatch, provider):
    monkeypatch.setattr(app.state, "orchestrator", AiOrchestrator(provider))


def signed_headers():
    ts = str(int(time.time()))
    return {TIMESTAMP_HEADER: ts, SIGNATURE_HEADER: compute_signature(SECRET, ts, "GET", PATH, b"")}


def test_health_is_ok_when_provider_is_available(monkeypatch):
    use_provider(monkeypatch, MockProvider())
    response = client.get(PATH, headers=signed_headers())
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "provider": "mock"}


def test_health_is_degraded_when_provider_is_unavailable(monkeypatch):
    use_provider(monkeypatch, ExternalApiProvider())
    response = client.get(PATH, headers=signed_headers())
    assert response.status_code == 200
    assert response.json() == {"status": "degraded", "provider": "external"}


def test_health_without_signature_is_rejected():
    assert client.get(PATH).status_code == 401


def test_health_with_wrong_signature_is_rejected():
    headers = {TIMESTAMP_HEADER: str(int(time.time())), SIGNATURE_HEADER: "0" * 64}
    assert client.get(PATH, headers=headers).status_code == 401