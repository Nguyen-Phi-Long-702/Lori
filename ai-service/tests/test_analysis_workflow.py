import json

import pytest

from orchestrator.analysis_workflow import exam_analysis_workflow, quiz_analysis_workflow
from providers.base import ProviderError
from providers.mock import MockProvider

LESSONS = {7: "Articles (a/an/the)", 11: "Present Perfect"}
PAYLOAD = {
    "quizType": "grammar",
    "title": "Present Perfect",
    "correctCount": 3,
    "incorrectCount": 7,
    "accuracyPercent": 30,
    "history": None,
    "lessons": LESSONS,
}
EXAM_PAYLOAD = {
    "examType": "IELTS",
    "title": "IELTS Practice Test 1",
    "totalScore": 6.5,
    "listeningScore": 7.0,
    "readingScore": 6.0,
    "listeningCorrect": 30,
    "listeningTotal": 40,
    "readingCorrect": 25,
    "readingTotal": 40,
    "timeSpentSeconds": 7200,
    "parts": [],
    "questionTypes": [],
    "wrongQuestions": [],
    "lessons": LESSONS,
}
REPLY = {
    "summary": "You got 3 of 10 right.",
    "patterns": ["You often mix up have and has."],
    "recommendations": [{"title": "Review Present Perfect", "reason": "It is your weakest topic.", "lessonId": 11}],
}


class SpyProvider(MockProvider):
    def __init__(self, reply):
        super().__init__(reply)
        self.prompts = []

    def generate(self, prompt):
        self.prompts.append(prompt)
        return super().generate(prompt)


def run(reply, workflow=quiz_analysis_workflow, payload=PAYLOAD):
    if isinstance(reply, dict):
        reply = json.dumps(reply)
    return workflow(MockProvider(reply=reply), payload)


def test_quiz_analysis_returns_summary_patterns_and_recommendations():
    assert run(REPLY) == {"type": "quiz_analysis", **REPLY}


def test_exam_analysis_uses_the_exam_type():
    assert run(REPLY, exam_analysis_workflow, EXAM_PAYLOAD)["type"] == "exam_analysis"


def test_json_wrapped_in_markdown_fence_is_accepted():
    assert run("```json\n" + json.dumps(REPLY) + "\n```")["summary"] == "You got 3 of 10 right."


@pytest.mark.parametrize("lesson_id", [99, True, "11", None, [11]])
def test_lesson_not_in_the_list_is_dropped_instead_of_failing(lesson_id):
    reply = {**REPLY, "recommendations": [{**REPLY["recommendations"][0], "lessonId": lesson_id}]}
    assert run(reply)["recommendations"][0]["lessonId"] is None


def test_lists_are_capped_at_three_items_and_bad_patterns_are_dropped():
    reply = {
        "summary": "ok",
        "patterns": ["a", "b", " ", 5, "c", "d"],
        "recommendations": [{"title": str(i), "reason": "r", "lessonId": None} for i in range(5)],
    }
    result = run(reply)
    assert result["patterns"] == ["a", "b", "c"]
    assert len(result["recommendations"]) == 3


def test_empty_patterns_and_recommendations_are_allowed():
    result = run({"summary": "Perfect score.", "patterns": [], "recommendations": []})
    assert (result["patterns"], result["recommendations"]) == ([], [])


@pytest.mark.parametrize(
    "reply",
    [
        "not json at all",
        "[1, 2]",
        {**REPLY, "summary": " "},
        {**REPLY, "patterns": "none"},
        {**REPLY, "recommendations": "none"},
        {**REPLY, "recommendations": [{"title": "Review"}]},
        {**REPLY, "recommendations": ["Review"]},
    ],
)
def test_malformed_provider_reply_is_a_provider_error(reply):
    with pytest.raises(ProviderError):
        run(reply)


def test_workflow_sends_the_quiz_prompt_to_the_provider():
    provider = SpyProvider(json.dumps(REPLY))
    quiz_analysis_workflow(provider, PAYLOAD)
    assert len(provider.prompts) == 1
    assert 'Quiz: grammar quiz "Present Perfect"' in provider.prompts[0]