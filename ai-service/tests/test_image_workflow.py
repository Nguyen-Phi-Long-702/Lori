import json

import pytest

from config.image_vocabulary import OBJECT_MEANINGS, OBJECT_WORDS
from context.dictionary_lookup import lookup_word
from orchestrator import image_workflow as workflow_module
from orchestrator.image_workflow import MAX_OBJECTS, image_workflow
from providers import yolo_detector
from providers.base import ProviderError
from providers.mock import MockProvider
from providers.yolo_detector import Detection

CAT = Detection("cat", 0.95, 0.1, 0.2, 0.3, 0.4)
BOTTLE = Detection("bottle", 0.80, 0.5, 0.5, 0.1, 0.2)
CAT_EXAMPLE = {"word": "cat", "example": "The cat sleeps.", "exampleVi": "Con mèo đang ngủ."}
ENTRIES = {
    "cat": {"ipa": "/kæt/", "definitions": [{"pos": "N", "meaning": "Con mèo.", "example": None}]},
    "bottle": {"ipa": "/ˈbɑːtl/", "definitions": [{"pos": "N", "meaning": "Chai, lọ.", "example": None}]},
}


class SpyProvider(MockProvider):
    def __init__(self, reply="{}"):
        super().__init__(reply)
        self.prompts = []

    def generate(self, prompt):
        self.prompts.append(prompt)
        return super().generate(prompt)


class FailingProvider(MockProvider):
    def generate(self, prompt):
        raise ProviderError("Cloudflare HTTP 429")


@pytest.fixture
def detect(monkeypatch):
    monkeypatch.setattr(workflow_module, "lookup_word", lambda word: ENTRIES.get(word))

    def set_detections(*detections):
        monkeypatch.setattr(workflow_module, "detect_objects", lambda data: list(detections))

    return set_detections


def run(provider=None, image=b"image"):
    return image_workflow(provider or MockProvider(reply=json.dumps({"examples": [CAT_EXAMPLE]})), {"image": image})


def test_object_gets_ipa_and_meaning_from_the_dictionary_and_example_from_the_model(detect):
    detect(CAT)
    assert run() == {
        "type": "image_identification",
        "objects": [
            {
                "word": "cat",
                "ipa": "/kæt/",
                "meaning": "Con mèo.",
                "confidence": 0.95,
                "box": {"x": 0.1, "y": 0.2, "width": 0.3, "height": 0.4},
                "example": "The cat sleeps.",
                "exampleVi": "Con mèo đang ngủ.",
            }
        ],
    }


def test_word_with_its_own_meaning_ignores_the_dictionary_and_missing_ipa_is_null(detect):
    detect(Detection("hair drier", 0.7, 0, 0, 1, 1))
    [item] = run(MockProvider(reply="{}"))["objects"]
    assert (item["word"], item["ipa"], item["meaning"]) == ("hair dryer", None, "máy sấy tóc")


def test_same_word_keeps_only_the_most_confident_detection_and_order_is_kept(detect):
    detect(CAT, BOTTLE, Detection("cat", 0.5, 0, 0, 1, 1))
    objects = run()["objects"]
    assert [(item["word"], item["confidence"]) for item in objects] == [("cat", 0.95), ("bottle", 0.8)]


def test_at_most_max_objects_are_returned(detect, monkeypatch):
    detect(*[Detection(f"thing{i}", 0.9 - i / 100, 0, 0, 1, 1) for i in range(MAX_OBJECTS + 2)])
    monkeypatch.setattr(workflow_module, "lookup_word", lambda word: {"ipa": None, "definitions": [{"meaning": "x"}]})
    assert len(run(MockProvider(reply="{}"))["objects"]) == MAX_OBJECTS


def test_no_detection_returns_empty_list_without_calling_the_model(detect):
    detect()
    provider = SpyProvider()
    assert run(provider) == {"type": "image_identification", "objects": []}
    assert provider.prompts == []


def test_prompt_lists_the_words_with_their_meanings(detect):
    detect(CAT, BOTTLE)
    provider = SpyProvider(reply=json.dumps({"examples": [CAT_EXAMPLE]}))
    run(provider)
    assert "- cat: Con mèo.\n- bottle: Chai, lọ." in provider.prompts[0]


@pytest.mark.parametrize("reply", ["sorry, no JSON", "{}", '{"examples": "x"}', '{"examples": [{"word": "cat"}]}'])
def test_unusable_model_reply_still_returns_the_objects_without_examples(detect, reply):
    detect(CAT)
    [item] = run(MockProvider(reply=reply))["objects"]
    assert (item["word"], item["example"], item["exampleVi"]) == ("cat", None, None)


def test_provider_failure_still_returns_the_objects_without_examples(detect):
    detect(CAT)
    [item] = run(FailingProvider())["objects"]
    assert (item["word"], item["example"], item["exampleVi"]) == ("cat", None, None)


def test_example_of_a_word_the_model_skipped_is_null(detect):
    detect(CAT, BOTTLE)
    objects = run()["objects"]
    assert objects[0]["example"] == "The cat sleeps."
    assert objects[1]["example"] is None


def test_every_yolo_class_has_a_meaning_in_the_dictionary_or_in_object_meanings():
    for label in yolo_detector.class_names():
        word = OBJECT_WORDS.get(label, label)
        entry = lookup_word(word)
        assert OBJECT_MEANINGS.get(word) or (entry and entry["definitions"]), word