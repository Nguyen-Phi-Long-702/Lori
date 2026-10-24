import http.client
import io
import json
import urllib.error

import pytest

from config.model_config import FALLBACK_MODEL, PRIMARY_MODEL, SYSTEM_PROMPT, TEMPERATURE
from providers.base import ProviderError
from providers.self_hosted import (
    ACCOUNT_ID_ENV,
    API_TOKEN_ENV,
    SelfHostedLlmProvider,
)

URLOPEN = "providers.self_hosted.urllib.request.urlopen"


class FakeResponse:
    def __init__(self, payload):
        self._data = json.dumps(payload).encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def read(self):
        return self._data


def success(result):
    return FakeResponse({"success": True, "result": result})


@pytest.fixture(autouse=True)
def configured_credentials(monkeypatch):
    monkeypatch.setenv(ACCOUNT_ID_ENV, "test-account")
    monkeypatch.setenv(API_TOKEN_ENV, "test-token")


def test_sends_model_settings_and_returns_parsed_response(monkeypatch):
    captured = {}

    def fake_urlopen(request, timeout):
        captured["url"] = request.full_url
        captured["authorization"] = request.get_header("Authorization")
        captured["body"] = json.loads(request.data)
        return success(
            {
                "choices": [{"message": {"content": "Use 'did'."}, "finish_reason": "stop"}],
                "usage": {"prompt_tokens": 40, "completion_tokens": 12},
            }
        )

    monkeypatch.setattr(URLOPEN, fake_urlopen)
    response = SelfHostedLlmProvider().generate("Fix this sentence")

    assert captured["url"].endswith(f"/accounts/test-account/ai/run/{PRIMARY_MODEL.model_id}")
    assert captured["authorization"] == "Bearer test-token"
    assert captured["body"] == {
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": "Fix this sentence"},
        ],
        "max_tokens": PRIMARY_MODEL.max_output_tokens,
        "temperature": TEMPERATURE,
    }
    assert response.text == "Use 'did'."
    assert response.model_id == PRIMARY_MODEL.model_id
    assert (response.prompt_tokens, response.completion_tokens) == (40, 12)
    assert response.truncated is False


def test_reasoning_part_is_removed(monkeypatch):
    monkeypatch.setattr(
        URLOPEN,
        lambda request, timeout: success(
            {"choices": [{"message": {"content": "<think>hmm</think>\n\nFinal answer"}}], "usage": {}}
        ),
    )
    assert SelfHostedLlmProvider().generate("q").text == "Final answer"


def test_reads_response_field_when_no_choices(monkeypatch):
    monkeypatch.setattr(URLOPEN, lambda request, timeout: success({"response": "Plain", "usage": {}}))
    assert SelfHostedLlmProvider().generate("q").text == "Plain"


def test_finish_reason_length_marks_truncated(monkeypatch):
    monkeypatch.setattr(
        URLOPEN,
        lambda request, timeout: success(
            {"choices": [{"message": {"content": ""}, "finish_reason": "length"}], "usage": {"completion_tokens": 5}}
        ),
    )
    assert SelfHostedLlmProvider().generate("q").truncated is True


def test_reaching_max_output_tokens_marks_truncated(monkeypatch):
    monkeypatch.setattr(
        URLOPEN,
        lambda request, timeout: success(
            {"response": "x", "usage": {"completion_tokens": FALLBACK_MODEL.max_output_tokens}}
        ),
    )
    assert SelfHostedLlmProvider(FALLBACK_MODEL).generate("q").truncated is True


def test_fallback_model_is_used_in_url(monkeypatch):
    captured = {}

    def fake_urlopen(request, timeout):
        captured["url"] = request.full_url
        return success({"response": "ok", "usage": {}})

    monkeypatch.setattr(URLOPEN, fake_urlopen)
    response = SelfHostedLlmProvider(FALLBACK_MODEL).generate("q")
    assert captured["url"].endswith(FALLBACK_MODEL.model_id)
    assert response.model_id == FALLBACK_MODEL.model_id


def test_http_error_becomes_provider_error(monkeypatch):
    def fake_urlopen(request, timeout):
        raise urllib.error.HTTPError(request.full_url, 429, "Too Many Requests", {}, io.BytesIO(b'{"errors":[]}'))

    monkeypatch.setattr(URLOPEN, fake_urlopen)
    with pytest.raises(ProviderError, match="429"):
        SelfHostedLlmProvider().generate("q")


def test_network_error_becomes_provider_error(monkeypatch):
    def fake_urlopen(request, timeout):
        raise urllib.error.URLError("unreachable")

    monkeypatch.setattr(URLOPEN, fake_urlopen)
    with pytest.raises(ProviderError):
        SelfHostedLlmProvider().generate("q")


@pytest.mark.parametrize("error", [TimeoutError("timed out"), http.client.IncompleteRead(b"")])
def test_timeout_and_broken_response_become_provider_error(monkeypatch, error):
    def fake_urlopen(request, timeout):
        raise error

    monkeypatch.setattr(URLOPEN, fake_urlopen)
    with pytest.raises(ProviderError):
        SelfHostedLlmProvider().generate("q")


def test_invalid_json_becomes_provider_error(monkeypatch):
    class BadResponse(FakeResponse):
        def read(self):
            return b"<html>gateway error</html>"

    monkeypatch.setattr(URLOPEN, lambda request, timeout: BadResponse({}))
    with pytest.raises(ProviderError):
        SelfHostedLlmProvider().generate("q")


def test_success_without_result_becomes_provider_error(monkeypatch):
    monkeypatch.setattr(URLOPEN, lambda request, timeout: FakeResponse({"success": True}))
    with pytest.raises(ProviderError):
        SelfHostedLlmProvider().generate("q")


def test_unsuccessful_payload_becomes_provider_error(monkeypatch):
    monkeypatch.setattr(
        URLOPEN, lambda request, timeout: FakeResponse({"success": False, "errors": [{"code": 5028}]})
    )
    with pytest.raises(ProviderError, match="5028"):
        SelfHostedLlmProvider().generate("q")


def test_missing_credentials_fail_without_calling_network(monkeypatch):
    monkeypatch.delenv(API_TOKEN_ENV)

    def must_not_be_called(request, timeout):
        raise AssertionError("khong duoc goi mang khi thieu cau hinh")

    monkeypatch.setattr(URLOPEN, must_not_be_called)
    provider = SelfHostedLlmProvider()
    assert provider.is_available() is False
    with pytest.raises(ProviderError):
        provider.generate("q")


def test_is_available_when_both_credentials_are_set():
    assert SelfHostedLlmProvider().is_available() is True