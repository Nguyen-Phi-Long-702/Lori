import pytest

from guardrails.ai_input_sanitizer import InvalidInputError
from orchestrator.chat_workflow import chat_workflow
from providers.base import LlmResponse, ProviderError
from providers.mock import MockProvider

CONTEXT = {"weakGrammar": [{"name": "Present Perfect", "correctCount": 5, "incorrectCount": 6, "accuracyPercent": 45}]}


class SpyProvider(MockProvider):
    def __init__(self, reply="Sure."):
        super().__init__(reply)
        self.prompts = []

    def generate(self, prompt):
        self.prompts.append(prompt)
        return super().generate(prompt)


def test_returns_chat_response_with_type():
    result = chat_workflow(MockProvider(reply="Use 'an' before vowel sounds."), {"message": "a or an?"})
    assert result == {"type": "chat", "answer": "Use 'an' before vowel sounds.", "truncated": False}


def test_message_is_sanitized_before_it_reaches_the_prompt():
    provider = SpyProvider()
    chat_workflow(provider, {"message": "<b>Hello</b>"})
    assert provider.prompts == ["<learner_message>\nHello\n</learner_message>"]


def test_learning_context_is_part_of_the_prompt():
    provider = SpyProvider()
    chat_workflow(provider, {"message": "Help me", "learningContext": CONTEXT})
    assert "Present Perfect: 45% correct" in provider.prompts[0]


def test_missing_or_null_learning_context_is_allowed():
    provider = SpyProvider()
    chat_workflow(provider, {"message": "Help me"})
    chat_workflow(provider, {"message": "Help me", "learningContext": None})
    assert all("<learner_context>" not in prompt for prompt in provider.prompts)


def test_message_empty_after_cleaning_is_rejected():
    with pytest.raises(InvalidInputError):
        chat_workflow(MockProvider(), {"message": "<script>x</script>"})


def test_empty_provider_reply_is_an_error():
    with pytest.raises(ProviderError):
        chat_workflow(MockProvider(reply=""), {"message": "Hello"})


def test_truncated_flag_is_passed_through():
    class TruncatedProvider(MockProvider):
        def generate(self, prompt):
            return LlmResponse(text="Cut off", model_id="mock", prompt_tokens=1, completion_tokens=2, truncated=True)

    assert chat_workflow(TruncatedProvider(), {"message": "Hello"})["truncated"] is True