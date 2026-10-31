import io
import time

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from guardrails.s2s_auth import SECRET_ENV_NAME, SIGNATURE_HEADER, TIMESTAMP_HEADER, compute_signature
from main import app
from orchestrator import image_workflow as workflow_module
from orchestrator.ai_orchestrator import AiOrchestrator, TaskType
from orchestrator.image_workflow import image_workflow
from providers.mock import MockProvider
from providers.yolo_detector import Detection

SECRET = "lori-test-shared-secret-0123456789abcdef"
PATH = "/ai/identify-image"

client = TestClient(app)


@pytest.fixture(autouse=True)
def configured_secret(monkeypatch):
    monkeypatch.setenv(SECRET_ENV_NAME, SECRET)
    monkeypatch.setattr(
        app.state, "orchestrator", AiOrchestrator(MockProvider(reply="{}"), {TaskType.IDENTIFY_IMAGE: image_workflow})
    )


def png_bytes():
    buffer = io.BytesIO()
    Image.new("RGB", (32, 32)).save(buffer, format="PNG")
    return buffer.getvalue()


def post_image(body, signed=True):
    headers = {"Content-Type": "application/octet-stream"}
    if signed:
        ts = str(int(time.time()))
        headers |= {TIMESTAMP_HEADER: ts, SIGNATURE_HEADER: compute_signature(SECRET, ts, "POST", PATH, body)}
    return client.post(PATH, content=body, headers=headers)


def test_identify_image_returns_the_objects_found_in_the_signed_body(monkeypatch):
    received = {}

    def fake_detect(data):
        received["image"] = data
        return [Detection("cat", 0.9, 0.1, 0.1, 0.5, 0.5)]

    monkeypatch.setattr(workflow_module, "detect_objects", fake_detect)
    monkeypatch.setattr(
        workflow_module, "lookup_word", lambda word: {"ipa": "/kæt/", "definitions": [{"meaning": "Con mèo."}]}
    )
    body = png_bytes()
    response = post_image(body)
    assert response.status_code == 200
    assert received["image"] == body
    assert response.json()["type"] == "image_identification"
    assert response.json()["objects"][0]["word"] == "cat"


@pytest.mark.parametrize("body", [b"", b"this is not an image"])
def test_data_that_is_not_an_image_returns_400(body):
    assert post_image(body).status_code == 400


def test_identify_image_without_signature_is_rejected():
    assert post_image(png_bytes(), signed=False).status_code == 401