import json
import logging
import time

import pytest

from guardrails import sanitized_log
from guardrails.pii_redactor import REDACTED
from guardrails.sanitized_log import MAX_LOGGED_CHARS, MAX_REDACTED_CHARS, log_ai_call
from orchestrator.ai_orchestrator import AiOrchestrator, TaskType, UnsupportedTaskError
from providers.base import ProviderError
from providers.mock import MockProvider


@pytest.fixture(autouse=True)
def captured_ai_call_log(monkeypatch, caplog):
    # Logger cua Lop 7 khong propagate (chi in ra stdout), nen bat tam de caplog doc duoc
    monkeypatch.setattr(sanitized_log.logger, "propagate", True)
    caplog.set_level(logging.INFO, logger=sanitized_log.logger.name)


def chat_workflow(provider, payload):
    return {"type": "chat", "answer": provider.generate(payload["message"]).text}


def failing_workflow(provider, payload):
    raise ProviderError("Cloudflare HTTP 500: chi tiet co the chua 0912345678")


def test_log_line_has_metadata_and_redacted_content(caplog):
    log_ai_call(
        "abc123", "chat", "mock", 42, {"message": "Mail a@b.co or call 0912345678"}, {"answer": "ok"}, None
    )
    line = caplog.records[-1].getMessage()
    assert line.startswith("ai_call id=abc123 task=chat model=mock status=ok error=- latency_ms=42 ")
    assert f"Mail {REDACTED} or call {REDACTED}" in line
    assert "a@b.co" not in line and "0912345678" not in line


def test_learning_context_and_image_bytes_are_not_logged(caplog):
    payload = {"message": "Hi", "learningContext": {"weakGrammar": ["Inversion"]}, "image": b"\x89PNG-secret"}
    log_ai_call("id", "chat", "mock", 1, payload, None, None)
    line = caplog.records[-1].getMessage()
    assert "Inversion" not in line and "weakGrammar" not in line and "PNG-secret" not in line
    assert "<11 bytes>" in line


def test_long_content_is_cut_after_redaction(caplog):
    log_ai_call("id", "chat", "mock", 1, {"message": "a" * (MAX_LOGGED_CHARS * 3) + " a@b.co"}, None, None)
    line = caplog.records[-1].getMessage()
    assert line.count("a") < MAX_LOGGED_CHARS * 2
    assert "..." in line


def test_oversized_string_is_omitted_and_logged_quickly(caplog):
    started = time.perf_counter()
    log_ai_call("id", "chat", "mock", 1, {"message": "a" * 200_000 + "@"}, None, None)
    assert time.perf_counter() - started < 2
    assert f"<{200_001} chars omitted>" in caplog.records[-1].getMessage()
    assert MAX_REDACTED_CHARS < 200_001


def test_log_line_is_a_single_line(caplog):
    log_ai_call("id", "chat", "mock", 1, {"message": "line1\nline2"}, {"answer": "x\ny"}, None)
    assert "\n" not in caplog.records[-1].getMessage()


def test_orchestrator_logs_successful_call(caplog):
    orchestrator = AiOrchestrator(MockProvider(reply="Hello"), {TaskType.CHAT: chat_workflow})
    orchestrator.handle("chat", {"message": "Hi there"})
    line = caplog.records[-1].getMessage()
    assert "task=chat model=mock status=ok error=-" in line
    assert json.dumps({"message": "Hi there"}) in line
    assert json.dumps({"type": "chat", "answer": "Hello"}) in line


def test_orchestrator_logs_response_after_output_validation(caplog):
    orchestrator = AiOrchestrator(MockProvider(reply="Mail a@b.co"), {TaskType.CHAT: chat_workflow})
    orchestrator.handle("chat", {"message": "Hi"})
    line = caplog.records[-1].getMessage()
    assert "a@b.co" not in line and REDACTED in line


def test_orchestrator_logs_error_type_but_not_error_message_and_reraises(caplog):
    orchestrator = AiOrchestrator(MockProvider(), {TaskType.CHAT: failing_workflow})
    with pytest.raises(ProviderError):
        orchestrator.handle("chat", {"message": "Call 0912345678"})
    line = caplog.records[-1].getMessage()
    assert "status=error error=ProviderError" in line
    assert "output=null" in line
    assert "Cloudflare" not in line and "0912345678" not in line


def test_orchestrator_logs_blocked_output_as_error(caplog):
    orchestrator = AiOrchestrator(MockProvider(reply="SELECT * FROM users"), {TaskType.CHAT: chat_workflow})
    with pytest.raises(ProviderError):
        orchestrator.handle("chat", {"message": "Hi"})
    assert "status=error error=OutputBlockedError" in caplog.records[-1].getMessage()


def test_unsupported_task_is_not_logged(caplog):
    with pytest.raises(UnsupportedTaskError):
        AiOrchestrator(MockProvider()).handle("nonsense", {})
    assert caplog.records == []