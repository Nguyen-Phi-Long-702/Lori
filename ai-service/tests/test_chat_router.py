import json
import time

import pytest
from fastapi.testclient import TestClient

from guardrails.s2s_auth import SECRET_ENV_NAME, SIGNATURE_HEADER, TIMESTAMP_HEADER, compute_signature
from main import app
from orchestrator.ai_orchestrator import AiOrchestrator, TaskType
from orchestrator.chat_workflow import chat_workflow
from providers.external_api import ExternalApiProvider
from providers.mock import MockProvider

SECRET = "lori-test-shared-secret-0123456789abcdef"
PATH = "/ai/chat"

client = TestClient(app)


@pytest.fixture(autouse=True)
def configured_secret(monkeypatch):
    monkeypatch.setenv(SECRET_ENV_NAME, SECRET)


def use_provider(monkeypatch, provider):
    monkeypatch.setattr(app.state, "orchestrator", AiOrchestrator(provider, {TaskType.CHAT: chat_workflow}))


def post_chat(payload):
    body = json.dumps(payload).encode("utf-8")
    ts = str(int(time.time()))
    headers = {
        "Content-Type": "application/json",
        TIMESTAMP_HEADER: ts,
        SIGNATURE_HEADER: compute_signature(SECRET, ts, "POST", PATH, body),
    }
    return client.post(PATH, content=body, headers=headers)


def test_chat_returns_structured_answer(monkeypatch):
    use_provider(monkeypatch, MockProvider(reply="Hello!"))
    response = post_chat({"message": "Hi", "learningContext": {"weakGrammar": []}})
    assert response.status_code == 200
    assert response.json() == {"type": "chat", "answer": "Hello!", "truncated": False}


def test_chat_without_signature_is_rejected(monkeypatch):
    use_provider(monkeypatch, MockProvider())
    assert client.post(PATH, json={"message": "Hi"}).status_code == 401


def test_chat_without_message_is_rejected(monkeypatch):
    use_provider(monkeypatch, MockProvider())
    assert post_chat({}).status_code == 422


def test_message_empty_after_cleaning_returns_400(monkeypatch):
    use_provider(monkeypatch, MockProvider())
    assert post_chat({"message": "<b></b>"}).status_code == 400


def test_provider_failure_returns_502_without_details(monkeypatch):
    use_provider(monkeypatch, ExternalApiProvider())
    response = post_chat({"message": "Hi"})
    assert response.status_code == 502
    assert response.json() == {"detail": "AI provider error"}