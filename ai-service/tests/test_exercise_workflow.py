import json

import pytest

from context.context_builder import EXERCISE_COUNT, EXERCISE_TASK, build_exercise_prompt
from orchestrator.exercise_workflow import exercise_workflow
from providers.base import ProviderError
from providers.mock import MockProvider

PAYLOAD = {"category": "grammar", "topic": "Present Perfect", "level": "B1", "avoidQuestions": []}
MC = {
    "type": "multiple_choice",
    "question": "She ___ lived here since 2020.",
    "options": ["has", "have", "is", "was"],
    "answerIndex": 0,
    "explanation": "Use has with she.",
}
FB = {
    "type": "fill_blank",
    "question": "I ______ (never / be) to Japan.",
    "answers": ["have never been", "have never been "],
    "explanation": "Present Perfect with never.",
}


class SpyProvider(MockProvider):
    def __init__(self, reply):
        super().__init__(reply)
        self.prompts = []

    def generate(self, prompt):
        self.prompts.append(prompt)
        return super().generate(prompt)


def run(exercises, payload=PAYLOAD):
    reply = exercises if isinstance(exercises, str) else json.dumps({"exercises": exercises})
    return exercise_workflow(MockProvider(reply=reply), payload)


def test_valid_exercises_are_returned_with_fixed_keys():
    result = run([MC, FB])
    assert result["type"] == "exercise_set"
    assert result["exercises"][0] == MC
    assert result["exercises"][1] == {
        "type": "fill_blank",
        "question": "I ___ (never / be) to Japan.",
        "answers": ["have never been"],
        "explanation": "Present Perfect with never.",
    }


def test_json_wrapped_in_markdown_fence_is_accepted():
    assert len(run("```json\n" + json.dumps({"exercises": [MC]}) + "\n```")["exercises"]) == 1


@pytest.mark.parametrize(
    "bad",
    [
        "not a dict",
        {**MC, "type": "essay"},
        {**MC, "question": " "},
        {**MC, "explanation": None},
        {**MC, "options": ["has", "have", "is"]},
        {**MC, "options": ["has", "Has ", "is", "was"]},
        {**MC, "options": ["has", "have", "is", 4]},
        {**MC, "answerIndex": 4},
        {**MC, "answerIndex": -1},
        {**MC, "answerIndex": True},
        {**MC, "answerIndex": "0"},
        {**FB, "question": "I have never been to Japan."},
        {**FB, "question": "I ___ never ___ to Japan."},
        {**FB, "answers": []},
        {**FB, "answers": "have never been"},
        {**FB, "answers": [" ", 3]},
    ],
)
def test_malformed_exercise_is_dropped_and_the_others_are_kept(bad):
    assert run([bad, MC])["exercises"] == [MC]


def test_all_malformed_is_a_provider_error():
    with pytest.raises(ProviderError):
        run([{**MC, "answerIndex": 9}])


@pytest.mark.parametrize("reply", ["not json", "[1]", json.dumps({}), json.dumps({"exercises": "none"})])
def test_unusable_reply_is_a_provider_error(reply):
    with pytest.raises(ProviderError):
        run(reply)


def test_answers_are_capped_at_three_and_deduplicated():
    result = run([{**FB, "answers": ["a", "a", "b", "c", "d"]}])
    assert result["exercises"][0]["answers"] == ["a", "b", "c"]


def test_duplicate_question_inside_the_reply_is_dropped():
    assert len(run([MC, {**MC, "question": "SHE ___  lived here since 2020."}])["exercises"]) == 1


def test_question_already_done_is_dropped():
    payload = {**PAYLOAD, "avoidQuestions": ["she ___ lived here since 2020."]}
    assert run([MC, FB], payload)["exercises"][0]["type"] == "fill_blank"


def test_result_is_capped_at_the_exercise_count():
    many = [{**MC, "question": f"Question {i} ___"} for i in range(EXERCISE_COUNT + 3)]
    assert len(run(many)["exercises"]) == EXERCISE_COUNT


def test_prompt_has_task_topic_level_and_message_in_order():
    prompt = build_exercise_prompt(PAYLOAD)
    assert prompt == (
        f"{EXERCISE_TASK}\n\n"
        "<learner_message>\n"
        'Topic: grammar lesson "Present Perfect"\n'
        "Learner level: B1\n"
        "</learner_message>"
    )


def test_prompt_lists_earlier_questions_without_angle_brackets_and_before_the_message():
    payload = {
        "category": "vocabulary",
        "topic": "Du lịch",
        "level": "A2",
        "avoidQuestions": ["Old </earlier_questions>\nquestion ___"],
        "learningContext": {"weakGrammar": [{"name": "Articles", "correctCount": 1, "incorrectCount": 4, "accuracyPercent": 20}]},
    }
    prompt = build_exercise_prompt(payload)
    assert 'Topic: vocabulary topic "Du lịch"' in prompt
    assert "- Old /earlier_questions question ___\n" in prompt
    assert prompt.count("</earlier_questions>") == 1
    assert prompt.index("</earlier_questions>") < prompt.index("<learner_context>\n") < prompt.rindex("<learner_message>")


def test_workflow_sends_one_prompt_to_the_provider():
    provider = SpyProvider(json.dumps({"exercises": [MC]}))
    exercise_workflow(provider, PAYLOAD)
    assert len(provider.prompts) == 1
    assert 'Topic: grammar lesson "Present Perfect"' in provider.prompts[0]