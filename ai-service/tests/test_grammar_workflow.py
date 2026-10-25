import json

import pytest

from guardrails.ai_input_sanitizer import InvalidInputError
from orchestrator.grammar_workflow import grammar_workflow
from providers.base import ProviderError
from providers.mock import MockProvider

LESSONS = {1: "Present Simple", 7: "Articles (a/an/the)"}
WRONG = {
    "correct": False,
    "correctedSentence": "She doesn't like coffee.",
    "errors": [{"type": "Subject-verb agreement", "explanation": "Use doesn't with she."}],
    "lessonId": 1,
}


class SpyProvider(MockProvider):
    def __init__(self, reply):
        super().__init__(reply)
        self.prompts = []

    def generate(self, prompt):
        self.prompts.append(prompt)
        return super().generate(prompt)


def run(reply, lessons=LESSONS, text="She don't like coffee."):
    if isinstance(reply, dict):
        reply = json.dumps(reply)
    return grammar_workflow(MockProvider(reply=reply), {"text": text, "lessons": lessons})


def test_wrong_sentence_returns_errors_correction_and_lesson():
    assert run(WRONG) == {
        "type": "grammar_analysis",
        "correct": False,
        "correctedSentence": "She doesn't like coffee.",
        "errors": [{"type": "Subject-verb agreement", "explanation": "Use doesn't with she."}],
        "lessonId": 1,
    }


def test_correct_sentence_has_no_errors_and_no_lesson():
    reply = {"correct": True, "correctedSentence": "I like tea.", "errors": [{"type": "x", "explanation": "y"}], "lessonId": 1}
    result = run(reply)
    assert (result["correct"], result["errors"], result["lessonId"]) == (True, [], None)


def test_json_wrapped_in_markdown_fence_is_accepted():
    assert run("```json\n" + json.dumps(WRONG) + "\n```")["lessonId"] == 1


@pytest.mark.parametrize("lesson_id", [99, True, "1", None, [1]])
def test_lesson_not_in_the_list_is_dropped_instead_of_failing(lesson_id):
    assert run({**WRONG, "lessonId": lesson_id})["lessonId"] is None


@pytest.mark.parametrize(
    "reply",
    [
        "not json at all",
        "[1, 2]",
        {**WRONG, "correct": "false"},
        {**WRONG, "correctedSentence": " "},
        {**WRONG, "errors": "none"},
        {**WRONG, "errors": []},
        {**WRONG, "errors": [{"type": "Tense"}]},
        {**WRONG, "errors": ["Tense"]},
    ],
)
def test_malformed_provider_reply_is_a_provider_error(reply):
    with pytest.raises(ProviderError):
        run(reply)


def test_sentence_is_sanitized_and_lessons_reach_the_prompt():
    provider = SpyProvider(json.dumps(WRONG))
    grammar_workflow(provider, {"text": "<b>She don't like coffee.</b>", "lessons": LESSONS})
    assert "<learner_message>\nShe don't like coffee.\n</learner_message>" in provider.prompts[0]
    assert "7: Articles (a/an/the)" in provider.prompts[0]


def test_learning_context_is_part_of_the_prompt():
    provider = SpyProvider(json.dumps(WRONG))
    context = {"weakGrammar": [{"name": "Articles", "correctCount": 1, "incorrectCount": 4, "accuracyPercent": 20}]}
    grammar_workflow(provider, {"text": "She don't like coffee.", "lessons": LESSONS, "learningContext": context})
    assert "Articles: 20% correct" in provider.prompts[0]


def test_text_empty_after_cleaning_is_rejected():
    with pytest.raises(InvalidInputError):
        run(WRONG, text="<script>x</script>")