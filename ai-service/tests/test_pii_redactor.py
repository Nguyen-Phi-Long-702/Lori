import pytest

from guardrails.pii_redactor import REDACTED, redact, redact_value

JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk"


@pytest.mark.parametrize(
    "text",
    [
        "What is the difference between 'a' and 'an'?",
        "Your score is 850 on 2026-11-04, band 7.5.",
        "Call me at 5 o'clock, token of appreciation, a secret garden.",
        "Count: 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15",
        "It is about 1 000 000 000 bytes.",
        "The order number is 1234567890123.",
        "The bearer of bad news arrived.",
    ],
)
def test_normal_text_is_unchanged(text):
    assert redact(text) == text


@pytest.mark.parametrize(
    "text, expected",
    [
        ("Mail me at john.doe@example.com now", f"Mail me at {REDACTED} now"),
        ("Call 0912 345 678.", f"Call {REDACTED}."),
        ("Call +84 912.345.678.", f"Call {REDACTED}."),
        (f"My token is {JWT} ok", f"My token is {REDACTED} ok"),
        ("Authorization: Bearer abc.def-123", f"Authorization: {REDACTED} {REDACTED}"),
        ("password=hunter2 and more", f"password={REDACTED} and more"),
        ('{"api_key": "sk-12345"}', f'{{"api_key": "{REDACTED}"}}'),
        ("PASSWORD: hunter2", f"PASSWORD: {REDACTED}"),
        ("postgres://lori:s3cret@db.example.com/lori", f"postgres://{REDACTED}@db.example.com/lori"),
        ("Card 4111 1111 1111 1111 expires soon", f"Card {REDACTED} expires soon"),
        ("Card 4111-1111-1111-1111.", f"Card {REDACTED}."),
        ("Card 4111111111111111", f"Card {REDACTED}"),
    ],
)
def test_sensitive_data_is_redacted(text, expected):
    assert redact(text) == expected


def test_digits_that_fail_the_luhn_check_are_not_a_card():
    assert redact("Number 4111 1111 1111 1112") == "Number 4111 1111 1111 1112"


def test_nested_value_is_redacted_and_keeps_its_shape():
    value = {"message": "Mail a@b.co", "turns": [{"content": "Call 0912345678"}, None], "n": 3, "ok": True}
    assert redact_value(value) == {
        "message": f"Mail {REDACTED}",
        "turns": [{"content": f"Call {REDACTED}"}, None],
        "n": 3,
        "ok": True,
    }


def test_string_longer_than_max_length_is_omitted_instead_of_scanned():
    value = {"message": "a" * 50 + "@example.com", "items": ["short a@b.co", "b" * 11]}
    assert redact_value(value, max_length=10) == {
        "message": "<62 chars omitted>",
        "items": ["<12 chars omitted>", "<11 chars omitted>"],
    }
    assert redact_value("short a@b.co", max_length=12) == f"short {REDACTED}"