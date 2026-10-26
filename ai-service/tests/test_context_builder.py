from context.context_builder import CONTEXT_INTRO, build_prompt
from context.context_builder import (
    CONTEXT_INTRO,
    GRAMMAR_TASK,
    VOCABULARY_TASK,
    build_grammar_prompt,
    build_prompt,
    build_vocabulary_prompt,
)

WEAK = {"name": "Present Perfect", "correctCount": 5, "incorrectCount": 6, "accuracyPercent": 45}
EXAM = {
    "examType": "TOEIC",
    "title": "Test 1",
    "totalScore": 650.0,
    "listeningCorrect": 60,
    "listeningTotal": 100,
    "readingCorrect": 70,
    "readingTotal": 100,
    "submittedAt": "2026-10-20T10:00:00Z",
}
MESSAGE_BLOCK = "<learner_message>\nExplain present perfect\n</learner_message>"


def test_without_context_prompt_has_only_the_message():
    assert build_prompt("Explain present perfect") == MESSAGE_BLOCK
    assert build_prompt("Explain present perfect", {}) == MESSAGE_BLOCK


def test_sections_with_no_data_are_left_out():
    context = {"weakGrammar": [], "weakVocabulary": [], "recentActivity": [], "recentExams": []}
    assert build_prompt("Explain present perfect", context) == MESSAGE_BLOCK


def test_context_comes_before_the_message():
    prompt = build_prompt("Explain present perfect", {"weakGrammar": [WEAK]})
    assert prompt == (
        "<learner_context>\n"
        f"{CONTEXT_INTRO}\n\n"
        "Weak grammar:\n"
        "- Present Perfect: 45% correct (5 right, 6 wrong)\n"
        "</learner_context>\n\n" + MESSAGE_BLOCK
    )


def test_only_sections_present_in_context_appear():
    prompt = build_prompt("Hi", {"weakVocabulary": [{**WEAK, "name": "Du lịch"}]})
    assert "Weak vocabulary topics:\n- Du lịch: 45% correct" in prompt
    assert "Weak grammar" not in prompt
    assert "Recent" not in prompt


def test_recent_activity_omits_counts_when_nothing_was_answered():
    activity = [
        {"type": "grammar lesson", "name": "Articles", "correctCount": 0, "incorrectCount": 0},
        {"type": "grammar quiz", "name": "Articles", "correctCount": 3, "incorrectCount": 4},
    ]
    prompt = build_prompt("Hi", {"recentActivity": activity})
    assert '- grammar lesson "Articles"\n' in prompt
    assert '- grammar quiz "Articles": 3 right, 4 wrong\n' in prompt


def test_recent_exam_line():
    prompt = build_prompt("Hi", {"recentExams": [EXAM, {**EXAM, "examType": "IELTS", "totalScore": 6.5}]})
    assert '- TOEIC "Test 1": total score 650; listening 60/100 correct; reading 70/100 correct' in prompt
    assert '- IELTS "Test 1": total score 6.5;' in prompt


def test_context_never_contains_the_submitted_date():
    assert "2026-10-20" not in build_prompt("Hi", {"recentExams": [EXAM]})


LESSONS = {1: "Present Simple", 7: "Articles (a/an/the)"}


def test_grammar_prompt_has_task_lesson_list_and_message_in_order():
    prompt = build_grammar_prompt("She don't like coffee.", LESSONS)
    assert prompt == (
        f"{GRAMMAR_TASK}\n\n"
        "<lesson_list>\n1: Present Simple\n7: Articles (a/an/the)\n</lesson_list>\n\n"
        "<learner_message>\nShe don't like coffee.\n</learner_message>"
    )


def test_grammar_prompt_puts_learning_context_between_lesson_list_and_message():
    prompt = build_grammar_prompt("She don't like coffee.", LESSONS, {"weakGrammar": [WEAK]})
    assert prompt.index("</lesson_list>") < prompt.index("<learner_context>") < prompt.rindex("<learner_message>")


ENTRY = {"definitions": [{"pos": "V", "meaning": "Làm ảnh hưởng đến.", "example": None}]}


def test_vocabulary_prompt_has_task_dictionary_entries_and_message_in_order():
    prompt = build_vocabulary_prompt("affect, effect", {"affect": ENTRY, "effect": None})
    assert prompt == (
        f"{VOCABULARY_TASK}\n\n"
        "<dictionary_entries>\naffect:\n- [V] Làm ảnh hưởng đến.\neffect: not in the dictionary\n</dictionary_entries>\n\n"
        "<learner_message>\naffect, effect\n</learner_message>"
    )


def test_vocabulary_prompt_removes_angle_brackets_from_dictionary_text():
    entry = {"definitions": [{"pos": "N", "meaning": "[[Mỹ><lóng]] kẻ cướp.", "example": None}]}
    assert "[[Mỹlóng]] kẻ cướp." in build_vocabulary_prompt("gun", {"gun": entry})


def test_vocabulary_prompt_puts_learning_context_between_entries_and_message():
    prompt = build_vocabulary_prompt("affect", {"affect": ENTRY}, {"weakVocabulary": [WEAK]})
    assert prompt.index("</dictionary_entries>") < prompt.index("<learner_context>") < prompt.rindex("<learner_message>")