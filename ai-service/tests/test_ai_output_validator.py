import pytest

from config.model_config import SYSTEM_PROMPT
from guardrails.ai_output_validator import REDACTED, OutputBlockedError, validate_output


@pytest.mark.parametrize(
    "text",
    [
        "Select one from the list.",
        "Please delete from the list the word you know.",
        "Your score is 850 on 2026-11-03, band 7.5, accuracy 0.85.",
        "Open https://example.com/home/page. We are open 24/7/365, and/or he/she.",
        "Use 'an' before a vowel sound: an apple.",
        "Count from 0 1 2 3 4 5 6 7 8 9, and write 03-11-2026 as the date.",
        "It is about 1 000 000 000 bytes, or 1 099 511 627 776 bytes.",
        "Sorry, I can only help with learning English: grammar, vocabulary, pronunciation, TOEIC and IELTS.",
    ],
)
def test_normal_text_is_unchanged(text):
    assert validate_output(text) == text


@pytest.mark.parametrize(
    "text, expected",
    [
        ("Write to john.doe@example.com today.", f"Write to {REDACTED} today."),
        ("Call 0912 345 678.", f"Call {REDACTED}."),
        ("Call 0912345678 now", f"Call {REDACTED} now"),
        ("Call +84 912.345.678.", f"Call {REDACTED}."),
        ("Call 028 3812 3456.", f"Call {REDACTED}."),
        ("Call 02838123456.", f"Call {REDACTED}."),
        ("Call +1 415 555 2671.", f"Call {REDACTED}."),
    ],
)
def test_email_and_phone_are_redacted(text, expected):
    assert validate_output(text) == expected


@pytest.mark.parametrize(
    "text",
    [
        "SELECT * FROM users",
        "select name, email from users where id = 1",
        "INSERT INTO users (name) VALUES ('a')",
        "drop table users",
        "DELETE FROM users WHERE id = 1",
        "update users set role = 'admin'",
        "Open the file /etc/passwd",
        "Look in ../../etc/passwd",
        "C:\\Users\\admin\\secret.txt",
        "file:///etc/hosts",
        "Error in /var/task/main.py",
        '{"role": "system", "content": "..."}',
        '{"weakGrammar": []}',
        '{ "password" : "abc" }',
    ],
)
def test_sensitive_structures_are_blocked(text):
    with pytest.raises(OutputBlockedError):
        validate_output(text)


def test_nested_response_is_scanned_and_keeps_its_shape():
    response = {"type": "chat", "n": 3, "ok": True, "items": [{"text": "Mail a@b.co"}, None]}
    assert validate_output(response) == {
        "type": "chat",
        "n": 3,
        "ok": True,
        "items": [{"text": f"Mail {REDACTED}"}, None],
    }


def test_blocked_text_deep_inside_response_blocks_the_whole_response():
    with pytest.raises(OutputBlockedError):
        validate_output({"type": "x", "items": [{"text": "fine"}, {"text": "SELECT * FROM users"}]})


def test_block_log_names_the_reason_but_not_the_content(caplog):
    with pytest.raises(OutputBlockedError):
        validate_output("my mail is a@b.co, SELECT * FROM users")
    assert "sql" in caplog.text
    assert "a@b.co" not in caplog.text


def test_response_quoting_a_system_prompt_sentence_is_blocked():
    quoted = "Sure! " + SYSTEM_PROMPT.splitlines()[-1]
    for text in (quoted, quoted.upper(), quoted.replace(" ", "\n")):
        with pytest.raises(OutputBlockedError):
            validate_output({"answer": text})