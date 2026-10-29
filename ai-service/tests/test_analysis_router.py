import json
import time

import pytest
from fastapi.testclient import TestClient

from guardrails.s2s_auth import SECRET_ENV_NAME, SIGNATURE_HEADER, TIMESTAMP_HEADER, compute_signature
from main import app
from orchestrator.ai_orchestrator import AiOrchestrator, TaskType
from orchestrator.analysis_workflow import exam_analysis_workflow, quiz_analysis_workflow
from providers.base import ProviderError
from providers.mock import MockProvider

SECRET = "lori-test-shared-secret-0123456789abcdef"
REPLY = {
    "summary": "You got 3 of 10 right.",
    "patterns": ["You often mix up have and has."],
    "recommendations": [{"title": "Review Present Perfect", "reason": "It is your weakest topic.", "lessonId": 11}],
}
QUIZ = {
    "quizType": "grammar",
    "title": "Present Perfect",
    "correctCount": 3,
    "incorrectCount": 7,
    "accuracyPercent": 30,
    "history": {"correctCount": 12, "incorrectCount": 20, "status": "reviewing"},
    "lessons": {"11": "Present Perfect"},
    "learningContext": {"weakGrammar": []},
}
EXAM = {
    "examType": "TOEIC",
    "title": "TOEIC Practice Test 1",
    "totalScore": 650.0,
    "listeningScore": 330.0,
    "readingScore": 320.0,
    "listeningCorrect": 60,
    "listeningTotal": 100,
    "readingCorrect": 70,
    "readingTotal": 100,
    "timeSpentSeconds": 5700,
    "parts": [{"name": "Part 5", "skill": "READING", "correctCount": 18, "totalCount": 30, "accuracyPercent": 60}],
    "questionTypes": [{"name": "MULTIPLE_CHOICE", "correctCount": 130, "totalCount": 200, "accuracyPercent": 65}],
    "wrongQuestions": [{"part": "Part 5", "question": "He ___ the report.", "yourAnswer": None, "correctAnswer": "B: wrote"}],
    "lessons": {"11": "Present Perfect"},
}

client = TestClient(app)


@pytest.fixture(autouse=True)
def configured_secret(monkeypatch):
    monkeypatch.setenv(SECRET_ENV_NAME, SECRET)


def use_provider(monkeypatch, provider):
    workflows = {TaskType.ANALYZE_QUIZ: quiz_analysis_workflow, TaskType.ANALYZE_EXAM: exam_analysis_workflow}
    monkeypatch.setattr(app.state, "orchestrator", AiOrchestrator(provider, workflows))


def post(path, payload):
    body = json.dumps(payload).encode("utf-8")
    ts = str(int(time.time()))
    headers = {
        "Content-Type": "application/json",
        TIMESTAMP_HEADER: ts,
        SIGNATURE_HEADER: compute_signature(SECRET, ts, "POST", path, body),
    }
    return client.post(path, content=body, headers=headers)


@pytest.mark.parametrize(
    "path, payload, response_type",
    [("/ai/analyze-quiz", QUIZ, "quiz_analysis"), ("/ai/analyze-exam", EXAM, "exam_analysis")],
)
def test_analysis_returns_structured_result(monkeypatch, path, payload, response_type):
    use_provider(monkeypatch, MockProvider(reply=json.dumps(REPLY)))
    response = post(path, payload)
    assert response.status_code == 200
    assert response.json() == {"type": response_type, **REPLY}


@pytest.mark.parametrize("path, payload", [("/ai/analyze-quiz", QUIZ), ("/ai/analyze-exam", EXAM)])
def test_analysis_without_signature_is_rejected(monkeypatch, path, payload):
    use_provider(monkeypatch, MockProvider())
    assert client.post(path, json=payload).status_code == 401


@pytest.mark.parametrize(
    "path, payload, missing",
    [("/ai/analyze-quiz", QUIZ, "title"), ("/ai/analyze-quiz", QUIZ, "lessons"), ("/ai/analyze-exam", EXAM, "parts")],
)
def test_missing_field_is_rejected(monkeypatch, path, payload, missing):
    use_provider(monkeypatch, MockProvider())
    body = {key: value for key, value in payload.items() if key != missing}
    assert post(path, body).status_code == 422


def test_unknown_quiz_type_is_rejected(monkeypatch):
    use_provider(monkeypatch, MockProvider())
    assert post("/ai/analyze-quiz", {**QUIZ, "quizType": "listening"}).status_code == 422


def test_provider_error_returns_502_without_details(monkeypatch):
    class Failing(MockProvider):
        def generate(self, prompt):
            raise ProviderError("Cloudflare HTTP 500: secret detail")

    use_provider(monkeypatch, Failing())
    response = post("/ai/analyze-quiz", QUIZ)
    assert response.status_code == 502
    assert "secret" not in response.text


def test_quiz_without_history_or_context_is_accepted(monkeypatch):
    use_provider(monkeypatch, MockProvider(reply=json.dumps(REPLY)))
    body = {key: value for key, value in QUIZ.items() if key not in ("history", "learningContext")}
    assert post("/ai/analyze-quiz", body).status_code == 200