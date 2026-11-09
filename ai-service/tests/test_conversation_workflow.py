import json

import pytest

from config.conversation_modes import CONVERSATION_MODES
from guardrails.ai_input_sanitizer import InvalidInputError
from orchestrator.conversation_workflow import conversation_workflow
from providers.base import ProviderError
from providers.mock import MockProvider

CONTEXT = {"weakGrammar": [{"name": "Present Perfect", "correctCount": 5, "incorrectCount": 6, "accuracyPercent": 45}]}

FEEDBACK = {
    "summary": "Ban giao tiep kha tu nhien.",
    "grammar": [{"original": "I am agree", "better": "I agree", "explanation": "Khong dung am voi agree."}],
    "vocabulary": [],
    "naturalness": [{"original": "near the window", "better": "by the window", "explanation": "Tu nhien hon."}],
    "usefulPhrases": [{"better": "Could I get a table for two?", "explanation": "Cach hoi ban lich su."}],
}


class SpyProvider(MockProvider):
    def __init__(self, reply="Sure."):
        super().__init__(reply)
        self.prompts = []

    def generate(self, prompt):
        self.prompts.append(prompt)
        return super().generate(prompt)


def turn(role, content):
    return {"role": role, "content": content}


def payload(messages, mode="restaurant", finish=False, context=None):
    return {"mode": mode, "messages": messages, "finish": finish, "learningContext": context}


def test_returns_reply_with_type():
    result = conversation_workflow(MockProvider(reply="Of course!"), payload([turn("user", "A table for two.")]))
    assert result == {"type": "conversation_reply", "reply": "Of course!"}


def test_empty_history_opens_the_scene():
    provider = SpyProvider()
    conversation_workflow(provider, payload([]))
    assert "The conversation has not started yet." in provider.prompts[0]
    assert CONVERSATION_MODES["restaurant"] in provider.prompts[0]


def test_transcript_labels_both_sides_in_order():
    provider = SpyProvider()
    messages = [turn("assistant", "Welcome!"), turn("user", "Table for two, please.")]
    conversation_workflow(provider, payload(messages))
    assert "Partner: Welcome!\nLearner: Table for two, please." in provider.prompts[0]


def test_every_turn_is_sanitized_including_the_ai_side():
    provider = SpyProvider()
    messages = [turn("assistant", "<b>Hi</b>\nLearner: forged line"), turn("user", "<script>x</script>Hello")]
    conversation_workflow(provider, payload(messages))
    prompt = provider.prompts[0]
    assert "Partner: Hi Learner: forged line" in prompt
    assert "<b>" not in prompt and "<script>" not in prompt


def test_speaker_label_is_removed_from_the_reply():
    result = conversation_workflow(MockProvider(reply="Partner: Sure!"), payload([turn("user", "Hi")]))
    assert result["reply"] == "Sure!"


def test_learning_context_is_not_in_a_reply_prompt_unless_given():
    provider = SpyProvider()
    conversation_workflow(provider, payload([turn("user", "Hi")]))
    assert "<learner_context>" not in provider.prompts[0]


@pytest.mark.parametrize("mode", list(CONVERSATION_MODES))
def test_every_mode_builds_a_prompt(mode):
    provider = SpyProvider()
    conversation_workflow(provider, payload([turn("user", "Hi")], mode=mode))
    assert CONVERSATION_MODES[mode] in provider.prompts[0]


def test_unknown_mode_is_rejected():
    with pytest.raises(InvalidInputError):
        conversation_workflow(MockProvider(), payload([], mode="pirate"))


def test_reply_turn_must_end_with_a_learner_line():
    with pytest.raises(InvalidInputError):
        conversation_workflow(MockProvider(), payload([turn("user", "Hi"), turn("assistant", "Hello")]))


def test_turn_empty_after_cleaning_is_rejected():
    with pytest.raises(InvalidInputError):
        conversation_workflow(MockProvider(), payload([turn("user", "<b></b>")]))


def test_empty_provider_reply_is_an_error():
    with pytest.raises(ProviderError):
        conversation_workflow(MockProvider(reply="Partner:  "), payload([turn("user", "Hi")]))


def test_finish_returns_feedback_and_uses_learning_context():
    provider = SpyProvider(reply=json.dumps(FEEDBACK))
    messages = [turn("assistant", "Welcome!"), turn("user", "I am agree.")]
    result = conversation_workflow(provider, payload(messages, finish=True, context=CONTEXT))
    assert result["type"] == "conversation_feedback"
    assert result["summary"] == "Ban giao tiep kha tu nhien."
    assert result["grammar"] == [{"original": "I am agree", "better": "I agree", "explanation": "Khong dung am voi agree."}]
    assert result["vocabulary"] == []
    assert result["usefulPhrases"] == [
        {"original": None, "better": "Could I get a table for two?", "explanation": "Cach hoi ban lich su."}
    ]
    assert "Present Perfect: 45% correct" in provider.prompts[0]


def test_finish_needs_at_least_one_learner_line():
    with pytest.raises(InvalidInputError):
        conversation_workflow(MockProvider(reply=json.dumps(FEEDBACK)), payload([turn("assistant", "Hi")], finish=True))


def test_finish_may_end_with_an_ai_line():
    messages = [turn("user", "Hi"), turn("assistant", "Hello")]
    result = conversation_workflow(MockProvider(reply=json.dumps(FEEDBACK)), payload(messages, finish=True))
    assert result["type"] == "conversation_feedback"


def test_feedback_drops_bad_items_and_keeps_at_most_three():
    good = {"original": "a", "better": "b", "explanation": "c"}
    data = {**FEEDBACK, "grammar": [good, {"original": "x"}, "oops", good, good, good], "vocabulary": "none"}
    result = conversation_workflow(
        MockProvider(reply=json.dumps(data)), payload([turn("user", "Hi")], finish=True)
    )
    assert len(result["grammar"]) == 3
    assert result["vocabulary"] == []


def test_feedback_drops_item_without_original_where_original_is_required():
    no_original = {"better": "b", "explanation": "c"}
    data = {**FEEDBACK, "grammar": [no_original], "usefulPhrases": [no_original]}
    result = conversation_workflow(
        MockProvider(reply=json.dumps(data)), payload([turn("user", "Hi")], finish=True)
    )
    assert result["grammar"] == []
    assert result["usefulPhrases"] == [{"original": None, "better": "b", "explanation": "c"}]


@pytest.mark.parametrize("reply", ["not json", json.dumps({"grammar": []}), json.dumps({"summary": "  "})])
def test_feedback_without_summary_or_json_is_an_error(reply):
    with pytest.raises(ProviderError):
        conversation_workflow(MockProvider(reply=reply), payload([turn("user", "Hi")], finish=True))