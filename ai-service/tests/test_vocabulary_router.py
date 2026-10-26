import json
import time

import pytest
from fastapi.testclient import TestClient

from guardrails.s2s_auth import SECRET_ENV_NAME, SIGNATURE_HEADER, TIMESTAMP_HEADER, compute_signature
from main import app
from orchestrator.ai_orchestrator import AiOrchestrator, TaskType
from orchestrator.vocabulary_workflow import vocabulary_workflow
from providers.mock import MockProvider

SECRET = "lori-test-shared-secret-0123456789abcdef"
PATH = "/ai/vocabulary"
REPLY = {
    "words": [{"word": "affect", "explanation": "To change something.", "synonyms": [], "antonyms": [], "collocations": []}],
    "difference": None,
}

client = TestClient(app)


@pytest.fixture(autouse=True)
def configured_secret(monkeypatch):
    monkeypatch.setenv(SECRET_ENV_NAME, SECRET)


def use_provider(monkeypatch, provider):
    monkeypatch.setattr(
        app.state, "orchestrator", AiOrchestrator(provider, {TaskType.VOCABULARY: vocabulary_workflow})
    )


def post_vocabulary(payload):
    body = json.dumps(payload).encode("utf-8")
    ts = str(int(time.time()))
    headers = {
        "Content-Type": "application/json",
        TIMESTAMP_HEADER: ts,
        SIGNATURE_HEADER: compute_signature(SECRET, ts, "POST", PATH, body),
    }
    return client.post(PATH, content=body, headers=headers)


def test_vocabulary_returns_structured_analysis_with_bundled_dictionary(monkeypatch):
    use_provider(monkeypatch, MockProvider(reply=json.dumps(REPLY)))
    response = post_vocabulary({"text": "affect", "learningContext": {"weakVocabulary": []}})
    assert response.status_code == 200
    body = response.json()
    assert body["type"] == "vocabulary_analysis"
    assert body["words"][0]["inDictionary"] is True
    assert body["words"][0]["ipa"]
    assert body["difference"] is None


def test_vocabulary_without_signature_is_rejected(monkeypatch):
    use_provider(monkeypatch, MockProvider())
    assert client.post(PATH, json={"text": "affect"}).status_code == 401


def test_missing_text_is_rejected(monkeypatch):
    use_provider(monkeypatch, MockProvider())
    assert post_vocabulary({}).status_code == 422


@pytest.mark.parametrize("text", ["<b></b>", "one, two, three"])
def test_invalid_input_returns_400(monkeypatch, text):
    use_provider(monkeypatch, MockProvider())
    assert post_vocabulary({"text": text}).status_code == 400


def test_unusable_provider_reply_returns_502_without_details(monkeypatch):
    use_provider(monkeypatch, MockProvider(reply="sorry, no JSON"))
    response = post_vocabulary({"text": "affect"})
    assert response.status_code == 502
    assert response.json() == {"detail": "AI provider error"}