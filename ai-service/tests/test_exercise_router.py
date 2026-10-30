import json
import time

import pytest
from fastapi.testclient import TestClient

from guardrails.s2s_auth import SECRET_ENV_NAME, SIGNATURE_HEADER, TIMESTAMP_HEADER, compute_signature
from main import app
from orchestrator.ai_orchestrator import AiOrchestrator, TaskType
from orchestrator.exercise_workflow import exercise_workflow
from providers.base import ProviderError
from providers.mock import MockProvider

SECRET = "lori-test-shared-secret-0123456789abcdef"
PATH = "/ai/generate-exercise"
EXERCISE = {
    "type": "multiple_choice",
    "question": "She ___ lived here since 2020.",
    "options": ["has", "have", "is", "was"],
    "answerIndex": 0,
    "explanation": "Use has with she.",
}
REQUEST = {
    "category": "grammar",
    "topic": "Present Perfect",
    "level": "B1",
    "avoidQuestions": ["Old question ___"],
    "learningContext": {"weakGrammar": []},
}

client = TestClient(app)


@pytest.fixture(autouse=True)
def configured_secret(monkeypatch):
    monkeypatch.setenv(SECRET_ENV_NAME, SECRET)


def use_provider(monkeypatch, provider):
    monkeypatch.setattr(
        app.state, "orchestrator", AiOrchestrator(provider, {TaskType.GENERATE_EXERCISE: exercise_workflow})
    )


def post(payload):
    body = json.dumps(payload).encode("utf-8")
    ts = str(int(time.time()))
    headers = {
        "Content-Type": "application/json",
        TIMESTAMP_HEADER: ts,
        SIGNATURE_HEADER: compute_signature(SECRET, ts, "POST", PATH, body),
    }
    return client.post(PATH, content=body, headers=headers)


def test_generate_exercise_returns_the_exercise_set(monkeypatch):
    use_provider(monkeypatch, MockProvider(reply=json.dumps({"exercises": [EXERCISE]})))
    response = post(REQUEST)
    assert response.status_code == 200
    assert response.json() == {"type": "exercise_set", "exercises": [EXERCISE]}


def test_request_without_context_and_earlier_questions_is_accepted(monkeypatch):
    use_provider(monkeypatch, MockProvider(reply=json.dumps({"exercises": [EXERCISE]})))
    assert post({"category": "vocabulary", "topic": "Du lịch", "level": "A1"}).status_code == 200


def test_request_without_signature_is_rejected(monkeypatch):
    use_provider(monkeypatch, MockProvider())
    assert client.post(PATH, json=REQUEST).status_code == 401


@pytest.mark.parametrize(
    "change", [{"category": "listening"}, {"level": "D9"}, {"topic": None}, {"avoidQuestions": "none"}]
)
def test_invalid_field_is_rejected(monkeypatch, change):
    use_provider(monkeypatch, MockProvider())
    assert post({**REQUEST, **change}).status_code == 422


def test_provider_error_returns_502_without_details(monkeypatch):
    class Failing(MockProvider):
        def generate(self, prompt):
            raise ProviderError("Cloudflare HTTP 500: secret detail")

    use_provider(monkeypatch, Failing())
    response = post(REQUEST)
    assert response.status_code == 502
    assert "secret" not in response.text