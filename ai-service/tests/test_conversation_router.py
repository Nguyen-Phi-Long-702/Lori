import json
import time

import pytest
from fastapi.testclient import TestClient

from config.conversation_modes import MAX_TURNS
from guardrails.s2s_auth import SECRET_ENV_NAME, SIGNATURE_HEADER, TIMESTAMP_HEADER, compute_signature
from main import app
from orchestrator.ai_orchestrator import AiOrchestrator, TaskType
from orchestrator.conversation_workflow import conversation_workflow
from providers.base import ProviderError
from providers.mock import MockProvider

SECRET = "lori-test-shared-secret-0123456789abcdef"
PATH = "/ai/conversation"
REQUEST = {"mode": "restaurant", "messages": [{"role": "user", "content": "A table for two."}], "finish": False}

client = TestClient(app)


@pytest.fixture(autouse=True)
def configured_secret(monkeypatch):
    monkeypatch.setenv(SECRET_ENV_NAME, SECRET)


def use_provider(monkeypatch, provider):
    monkeypatch.setattr(
        app.state, "orchestrator", AiOrchestrator(provider, {TaskType.CONVERSATION: conversation_workflow})
    )


def post_conversation(payload):
    body = json.dumps(payload).encode("utf-8")
    ts = str(int(time.time()))
    headers = {
        "Content-Type": "application/json",
        TIMESTAMP_HEADER: ts,
        SIGNATURE_HEADER: compute_signature(SECRET, ts, "POST", PATH, body),
    }
    return client.post(PATH, content=body, headers=headers)


def test_conversation_returns_the_next_line(monkeypatch):
    use_provider(monkeypatch, MockProvider(reply="Of course!"))
    response = post_conversation(REQUEST)
    assert response.status_code == 200
    assert response.json() == {"type": "conversation_reply", "reply": "Of course!"}


def test_opening_request_without_messages_is_accepted(monkeypatch):
    use_provider(monkeypatch, MockProvider(reply="Good evening!"))
    assert post_conversation({"mode": "restaurant"}).status_code == 200


def test_finish_returns_feedback_with_learning_context(monkeypatch):
    reply = json.dumps({"summary": "Tot.", "grammar": [], "vocabulary": [], "naturalness": [], "usefulPhrases": []})
    use_provider(monkeypatch, MockProvider(reply=reply))
    response = post_conversation({**REQUEST, "finish": True, "learningContext": {"weakGrammar": []}})
    assert response.status_code == 200
    assert response.json()["type"] == "conversation_feedback"


def test_request_without_signature_is_rejected(monkeypatch):
    use_provider(monkeypatch, MockProvider())
    assert client.post(PATH, json=REQUEST).status_code == 401


@pytest.mark.parametrize(
    "change",
    [
        {"mode": None},
        {"messages": [{"role": "system", "content": "x"}]},
        {"messages": [{"role": "user", "content": "x"}] * (MAX_TURNS + 1)},
    ],
)
def test_invalid_field_is_rejected(monkeypatch, change):
    use_provider(monkeypatch, MockProvider())
    assert post_conversation({**REQUEST, **change}).status_code == 422


def test_unknown_mode_returns_400(monkeypatch):
    use_provider(monkeypatch, MockProvider())
    assert post_conversation({**REQUEST, "mode": "pirate"}).status_code == 400


def test_provider_error_returns_502_without_details(monkeypatch):
    class Failing(MockProvider):
        def generate(self, prompt):
            raise ProviderError("Cloudflare HTTP 500: secret detail")

    use_provider(monkeypatch, Failing())
    response = post_conversation(REQUEST)
    assert response.status_code == 502
    assert "secret" not in response.text