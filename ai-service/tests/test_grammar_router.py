import json
import time

import pytest
from fastapi.testclient import TestClient

from guardrails.s2s_auth import SECRET_ENV_NAME, SIGNATURE_HEADER, TIMESTAMP_HEADER, compute_signature
from main import app
from orchestrator.ai_orchestrator import AiOrchestrator, TaskType
from orchestrator.grammar_workflow import grammar_workflow
from providers.mock import MockProvider

SECRET = "lori-test-shared-secret-0123456789abcdef"
PATH = "/ai/grammar"
REPLY = {
    "correct": False,
    "correctedSentence": "She doesn't like coffee.",
    "errors": [{"type": "Subject-verb agreement", "explanation": "Use doesn't with she."}],
    "lessonId": 1,
}

client = TestClient(app)


@pytest.fixture(autouse=True)
def configured_secret(monkeypatch):
    monkeypatch.setenv(SECRET_ENV_NAME, SECRET)


def use_provider(monkeypatch, provider):
    monkeypatch.setattr(app.state, "orchestrator", AiOrchestrator(provider, {TaskType.GRAMMAR: grammar_workflow}))


def post_grammar(payload):
    body = json.dumps(payload).encode("utf-8")
    ts = str(int(time.time()))
    headers = {
        "Content-Type": "application/json",
        TIMESTAMP_HEADER: ts,
        SIGNATURE_HEADER: compute_signature(SECRET, ts, "POST", PATH, body),
    }
    return client.post(PATH, content=body, headers=headers)


def valid_payload(**changes):
    return {"text": "She don't like coffee.", "lessons": {"1": "Present Simple"}, **changes}


def test_grammar_returns_structured_analysis(monkeypatch):
    use_provider(monkeypatch, MockProvider(reply=json.dumps(REPLY)))
    response = post_grammar(valid_payload(learningContext={"weakGrammar": []}))
    assert response.status_code == 200
    assert response.json() == {"type": "grammar_analysis", **REPLY}


def test_grammar_without_signature_is_rejected(monkeypatch):
    use_provider(monkeypatch, MockProvider())
    assert client.post(PATH, json=valid_payload()).status_code == 401


@pytest.mark.parametrize("missing", ["text", "lessons"])
def test_missing_field_is_rejected(monkeypatch, missing):
    use_provider(monkeypatch, MockProvider())
    payload = valid_payload()
    del payload[missing]
    assert post_grammar(payload).status_code == 422


def test_text_empty_after_cleaning_returns_400(monkeypatch):
    use_provider(monkeypatch, MockProvider())
    assert post_grammar(valid_payload(text="<b></b>")).status_code == 400


def test_unusable_provider_reply_returns_502_without_details(monkeypatch):
    use_provider(monkeypatch, MockProvider(reply="sorry, no JSON"))
    response = post_grammar(valid_payload())
    assert response.status_code == 502
    assert response.json() == {"detail": "AI provider error"}