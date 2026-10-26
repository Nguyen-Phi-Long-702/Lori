import json

import pytest

from guardrails.ai_input_sanitizer import InvalidInputError
from orchestrator import vocabulary_workflow as workflow_module
from orchestrator.vocabulary_workflow import vocabulary_workflow
from providers.base import ProviderError
from providers.mock import MockProvider

ENTRY = {
    "ipa": "/əˈfɛkt/",
    "definitions": [{"pos": "V", "meaning": "Làm ảnh hưởng đến.", "example": None}],
    "synonyms": ["influence"],
    "antonyms": [],
}
AFFECT = {
    "word": "affect",
    "explanation": "To change something.",
    "synonyms": ["change"],
    "antonyms": ["ignore"],
    "collocations": ["affect health"],
}
EFFECT = {"word": "effect", "explanation": "A result.", "synonyms": [], "antonyms": [], "collocations": []}


class SpyProvider(MockProvider):
    def __init__(self, reply):
        super().__init__(reply)
        self.prompts = []

    def generate(self, prompt):
        self.prompts.append(prompt)
        return super().generate(prompt)


@pytest.fixture(autouse=True)
def fake_dictionary(monkeypatch):
    entries = {"affect": ENTRY}
    monkeypatch.setattr(workflow_module, "lookup_word", lambda term: entries.get(term))


def run(reply, text="affect"):
    if isinstance(reply, dict):
        reply = json.dumps(reply)
    return vocabulary_workflow(MockProvider(reply=reply), {"text": text})


def test_word_in_dictionary_uses_dictionary_data_and_model_explanation():
    result = run({"words": [AFFECT], "difference": None})
    assert result == {
        "type": "vocabulary_analysis",
        "words": [
            {
                "word": "affect",
                "inDictionary": True,
                "ipa": "/əˈfɛkt/",
                "definitions": ENTRY["definitions"],
                "explanation": "To change something.",
                "synonyms": ["influence"],
                "antonyms": ["ignore"],
                "collocations": ["affect health"],
            }
        ],
        "difference": None,
    }


def test_word_not_in_dictionary_has_no_dictionary_data():
    word = run({"words": [EFFECT], "difference": None}, text="effect")["words"][0]
    assert (word["inDictionary"], word["ipa"], word["definitions"]) == (False, None, [])


def test_two_words_need_a_difference_and_keep_the_order_of_the_learner():
    reply = {"words": [EFFECT, AFFECT], "difference": " Affect is a verb. "}
    result = run(reply, text="Affect vs effect")
    assert [w["word"] for w in result["words"]] == ["affect", "effect"]
    assert result["difference"] == "Affect is a verb."


@pytest.mark.parametrize("text", ["affect, effect", "affect / effect", "affect VS. effect"])
def test_separators_split_two_words(text):
    assert len(run({"words": [AFFECT, EFFECT], "difference": "x"}, text=text)["words"]) == 2


def test_difference_is_dropped_for_one_word():
    assert run({"words": [AFFECT], "difference": "ignored"})["difference"] is None


def test_same_word_twice_counts_as_one():
    assert len(run({"words": [AFFECT], "difference": None}, text="affect, Affect")["words"]) == 1


def test_bad_lists_become_empty_and_are_limited():
    word = {**EFFECT, "synonyms": "none", "antonyms": [1, " "], "collocations": [f"c{i}" for i in range(9)]}
    result = run({"words": [word], "difference": None}, text="effect")["words"][0]
    assert (result["synonyms"], result["antonyms"], len(result["collocations"])) == ([], [], 5)


@pytest.mark.parametrize(
    "reply",
    [
        "not json at all",
        {"difference": None},
        {"words": "affect", "difference": None},
        {"words": [], "difference": None},
        {"words": [{**AFFECT, "word": "other"}], "difference": None},
        {"words": [{**AFFECT, "explanation": " "}], "difference": None},
        {"words": ["affect"], "difference": None},
    ],
)
def test_unusable_provider_reply_is_a_provider_error(reply):
    with pytest.raises(ProviderError):
        run(reply)


@pytest.mark.parametrize("difference", [None, " ", 3])
def test_two_words_without_a_difference_is_a_provider_error(difference):
    with pytest.raises(ProviderError):
        run({"words": [AFFECT, EFFECT], "difference": difference}, text="affect, effect")


@pytest.mark.parametrize("text", ["<script>x</script>", " , ", "a, b, c", "x" * 41, "a, " + "y" * 41])
def test_invalid_input_is_rejected(text):
    with pytest.raises(InvalidInputError):
        run({"words": [AFFECT], "difference": None}, text=text)


def test_prompt_has_dictionary_entries_and_sanitized_text():
    provider = SpyProvider(json.dumps({"words": [AFFECT, EFFECT], "difference": "x"}))
    vocabulary_workflow(provider, {"text": "<b>affect</b> vs effect"})
    prompt = provider.prompts[0]
    assert "affect:\n- [V] Làm ảnh hưởng đến.\neffect: not in the dictionary" in prompt
    assert "<learner_message>\naffect vs effect\n</learner_message>" in prompt


def test_learning_context_is_part_of_the_prompt():
    provider = SpyProvider(json.dumps({"words": [AFFECT], "difference": None}))
    context = {"weakVocabulary": [{"name": "Du lịch", "correctCount": 1, "incorrectCount": 4, "accuracyPercent": 20}]}
    vocabulary_workflow(provider, {"text": "affect", "learningContext": context})
    assert "Du lịch: 20% correct" in provider.prompts[0]