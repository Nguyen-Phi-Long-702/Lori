import time

import pytest
from fastapi.testclient import TestClient

from guardrails.s2s_auth import (
    SECRET_ENV_NAME,
    SIGNATURE_HEADER,
    TIMESTAMP_HEADER,
    compute_signature,
)
from main import app

SECRET = "lori-test-shared-secret-0123456789abcdef"
PATH = "/ai/ping"
BODY = b'{"hello":"lori"}'

client = TestClient(app)


@pytest.fixture(autouse=True)
def configured_secret(monkeypatch):
    monkeypatch.setenv(SECRET_ENV_NAME, SECRET)


def signed_headers(secret=SECRET, timestamp=None, method="POST", path=PATH, body=BODY):
    ts = str(int(time.time())) if timestamp is None else str(timestamp)
    return {
        TIMESTAMP_HEADER: ts,
        SIGNATURE_HEADER: compute_signature(secret, ts, method, path, body),
    }


def test_signature_matches_shared_test_vector():
    # Gia tri nay phai trung voi AiServiceSignerTest ben Backend
    signature = compute_signature(SECRET, "1760000000", "POST", PATH, BODY)
    assert signature == "a2ccda8db585a00eebc97d2b0ae19b15afc9b2b1f3b2530db1b36237efe89a3a"


def test_valid_signature_is_accepted():
    response = client.post(PATH, content=BODY, headers=signed_headers())
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_missing_headers_is_rejected():
    assert client.post(PATH, content=BODY).status_code == 401


def test_wrong_secret_is_rejected():
    headers = signed_headers(secret="another-shared-secret-0123456789abcdef")
    assert client.post(PATH, content=BODY, headers=headers).status_code == 401


def test_tampered_body_is_rejected():
    headers = signed_headers()
    assert client.post(PATH, content=b'{"hello":"evil"}', headers=headers).status_code == 401


def test_signature_for_another_path_is_rejected():
    headers = signed_headers(path="/ai/other")
    assert client.post(PATH, content=BODY, headers=headers).status_code == 401


def test_expired_timestamp_is_rejected():
    headers = signed_headers(timestamp=int(time.time()) - 301)
    assert client.post(PATH, content=BODY, headers=headers).status_code == 401


def test_future_timestamp_is_rejected():
    headers = signed_headers(timestamp=int(time.time()) + 301)
    assert client.post(PATH, content=BODY, headers=headers).status_code == 401


def test_non_numeric_timestamp_is_rejected():
    headers = {TIMESTAMP_HEADER: "abc", SIGNATURE_HEADER: "0" * 64}
    assert client.post(PATH, content=BODY, headers=headers).status_code == 401


def test_oversized_timestamp_is_rejected():
    headers = {TIMESTAMP_HEADER: "9" * 5000, SIGNATURE_HEADER: "0" * 64}
    assert client.post(PATH, content=BODY, headers=headers).status_code == 401


def test_non_ascii_signature_is_rejected():
    headers = {
        TIMESTAMP_HEADER: str(int(time.time())),
        SIGNATURE_HEADER: b"\xe9",
    }
    assert client.post(PATH, content=BODY, headers=headers).status_code == 401


def test_secret_not_configured_rejects_everything(monkeypatch):
    headers = signed_headers()
    monkeypatch.delenv(SECRET_ENV_NAME)
    assert client.post(PATH, content=BODY, headers=headers).status_code == 401


def test_too_short_secret_rejects_everything(monkeypatch):
    headers = signed_headers(secret="short")
    monkeypatch.setenv(SECRET_ENV_NAME, "short")
    assert client.post(PATH, content=BODY, headers=headers).status_code == 401


def test_hello_world_still_public():
    response = client.get("/")
    assert response.status_code == 200
    assert response.json() == {"message": "Hello from Lori AI Service"}