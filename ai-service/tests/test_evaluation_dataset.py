import re
from collections import Counter

import pytest

from config.model_config import SYSTEM_PROMPT
from context.dictionary_lookup import lookup_word
from evaluation.case_checks import CHECKS
from evaluation.case_loader import CHAT_KINDS, build_request, lessons, load_cases
from guardrails.ai_input_sanitizer import InvalidInputError, sanitize_input
from guardrails.topic_classifier import OFF_TOPIC_REPLY, is_off_topic
from models.analysis import ExamAnalysisRequest, QuizAnalysisRequest, RecommendRequest
from models.chat import ChatRequest
from models.grammar import GrammarRequest
from models.vocabulary import VocabularyRequest

# Kiem tra bo Evaluation Dataset khong can mang va khong ton Neurons: dataset dung cau truc, dap an chac chan khop code that
CASES = load_cases()
EXPECTED_GROUP_SIZES = {
    "grammar": 40, "vocabulary": 40, "quiz_exam": 20, "personalization": 20, "guardrails": 30, "image": 20,
}


def of_kind(*kinds):
    return [case for case in CASES if case["kind"] in kinds]


def test_dataset_size_and_ids():
    for group, size in Counter(case["group"] for case in CASES).items():
        assert size == EXPECTED_GROUP_SIZES[group], group
    assert len({case["id"] for case in CASES}) == len(CASES)
    assert all(case["kind"] in CHECKS for case in CASES)


def test_requests_match_ai_service_models():
    models = {"grammar": GrammarRequest, "vocabulary": VocabularyRequest, "quiz": QuizAnalysisRequest,
              "exam": ExamAnalysisRequest, "recommend": RecommendRequest}
    for case in CASES:
        if case["kind"] == "image":
            continue
        _, payload, _ = build_request(case)
        (models.get(case["kind"]) or ChatRequest).model_validate(payload)


def test_accuracy_percent_matches_counts():
    def check(item, correct, total):
        assert item["accuracyPercent"] == round(100 * correct / total), item

    for case in of_kind("quiz"):
        p = case["payload"]
        check(p, p["correctCount"], p["correctCount"] + p["incorrectCount"])
    for case in of_kind("recommend"):
        for topic in case["weakTopics"]:
            check(topic, topic["correctCount"], topic["correctCount"] + topic["incorrectCount"])
    for case in of_kind("exam"):
        for stat in case["payload"]["parts"] + case["payload"]["questionTypes"]:
            check(stat, stat["correctCount"], stat["totalCount"])


def test_lesson_ids_exist_in_lessons():
    known = lessons()
    for case in CASES:
        assert set(case.get("lesson_ids", [])) <= set(known), case["id"]
        assert case.get("first_lesson") is None or case["first_lesson"] in known, case["id"]


def test_grammar_fix_patterns_do_not_match_the_wrong_sentence():
    for case in of_kind("grammar"):
        if not case["correct"]:
            assert not re.search(case["fix"], case["text"], re.IGNORECASE), case["id"]


def test_vocabulary_cases_match_lori_dictionary():
    for case in of_kind("vocabulary"):
        assert 1 <= len(case["words"]) <= 2 and len(case["explain_any"]) == len(case["words"]), case["id"]
        assert case["in_dict"] == [lookup_word(word) is not None for word in case["words"]], case["id"]


def test_guardrail_cases_are_handled_by_the_layer_they_target():
    for case in of_kind(*CHAT_KINDS):
        message = case["message"]
        if case["kind"] == "blocked":
            with pytest.raises(InvalidInputError):
                sanitize_input(message)
            continue
        # Cac case con lai phai toi duoc Topic classifier; chi off_topic moi bi no chan
        assert is_off_topic(sanitize_input(message)) == (case["kind"] == "off_topic"), case["id"]


def test_refuse_cases_have_a_measurable_condition():
    for case in of_kind("refuse"):
        assert case.get("forbidden") or case.get("canary") or case.get("leak"), case["id"]


def test_checks_detect_wrong_answers():
    grammar = {"kind": "grammar", "correct": False, "fix": r"does(n.t| not) like"}
    good = {"type": "grammar_analysis", "correct": False, "correctedSentence": "She doesn't like tea.", "errors": [{}]}
    assert CHECKS["grammar"](grammar, 200, good) == []
    assert CHECKS["grammar"](grammar, 200, {**good, "correctedSentence": "She don't like tea."})
    assert CHECKS["grammar"](grammar, 502, {})

    quiz = {"kind": "quiz", "lesson_ids": [26], "must_link": True}
    body = {"type": "quiz_analysis", "summary": "ok", "patterns": [], "recommendations": [{"title": "t", "reason": "r", "lessonId": 26}]}
    assert CHECKS["quiz"](quiz, 200, body) == []
    body["recommendations"][0]["lessonId"] = 3
    assert len(CHECKS["quiz"](quiz, 200, body)) == 2

    leak = {"kind": "refuse", "leak": True}
    leaked = {"type": "chat", "answer": "Sure! " + SYSTEM_PROMPT.splitlines()[-1]}
    assert CHECKS["refuse"](leak, 200, leaked)
    assert CHECKS["refuse"](leak, 200, {"type": "chat", "answer": "I can only help with English."}) == []
    assert CHECKS["refuse"]({"kind": "refuse", "canary": "PWNED-1"}, 200, {"type": "chat", "answer": "PWNED-1."})
    assert CHECKS["off_topic"]({"kind": "off_topic"}, 200, {"answer": OFF_TOPIC_REPLY}) == []