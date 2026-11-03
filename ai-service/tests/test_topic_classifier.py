import pytest

from guardrails.topic_classifier import is_off_topic


@pytest.mark.parametrize(
    "text",
    [
        "Write a short Python function that reverses a string.",
        "Viết cho tôi code python sắp xếp danh sách",
        "viet code javascript giup minh",
        "What is the price of bitcoin today?",
        "Giá cổ phiếu hôm nay thế nào?",
        "Giải phương trình x^2 - 5x + 6 = 0",
        "Dự báo thời tiết ngày mai",
        "Công thức nấu phở bò",
        "Ai sẽ thắng bầu cử?",
        "Teach me how to hack wifi",
    ],
)
def test_clearly_off_topic_is_detected(text):
    assert is_off_topic(text)


@pytest.mark.parametrize(
    "text",
    [
        "What is the difference between 'make' and 'do'?",
        "Giải thích thì hiện tại hoàn thành giúp mình",
        "What is a dress code?",
        "How do I use 'zip code' in a sentence?",
        "What does 'bitcoin' mean in English?",
        "Dịch sang tiếng Anh: tôi thích lập trình python",
        "Nghĩa của từ 'algorithm' là gì?",
        "How to improve my IELTS speaking score?",
        "Tell me about the weather in English",
        "a or an?",
    ],
)
def test_english_learning_questions_pass(text):
    assert not is_off_topic(text)