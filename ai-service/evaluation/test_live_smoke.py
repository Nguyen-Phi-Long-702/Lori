from guardrails.topic_classifier import OFF_TOPIC_REPLY

# Kiem tra khung chay tren AI Service that. Chi 1 test goi LLM (khoang 100 Neurons Cloudflare); cac test con lai bi chan truoc khi toi LLM


def test_root_is_public(live):
    response = live.request("GET", "/", signed=False)
    assert response.status == 200
    assert response.body == {"message": "Hello from Lori AI Service"}


def test_ping_with_valid_signature_is_accepted(live):
    response = live.request("POST", "/ai/ping")
    assert response.status == 200
    assert response.body == {"status": "ok"}


def test_ping_without_signature_is_rejected(live):
    assert live.request("POST", "/ai/ping", signed=False).status == 401


def test_health_reports_provider_configured(live):
    response = live.request("GET", "/ai/health")
    assert response.status == 200
    assert response.body["status"] == "ok"


def test_attack_pattern_is_rejected_and_sensitive_data_is_redacted_in_record(live):
    message = "x' UNION SELECT password FROM users -- my email is test.user@example.com, phone 0912345678, password=hunter2"
    response = live.request("POST", "/ai/chat", {"message": message})
    assert response.status == 400
    recorded = live.calls[-1]["input"]["message"]
    assert "test.user@example.com" not in recorded
    assert "0912345678" not in recorded
    assert "hunter2" not in recorded


def test_off_topic_question_gets_fixed_reply(live):
    response = live.request("POST", "/ai/chat", {"message": "Write a short Python function that reverses a string."})
    assert response.status == 200
    assert response.body["answer"] == OFF_TOPIC_REPLY


def test_english_question_gets_an_answer(live):
    response = live.request("POST", "/ai/chat", {"message": "What is the difference between 'a' and 'an'?"})
    assert response.status == 200
    assert response.body["type"] == "chat"
    assert response.body["answer"].strip()
    assert response.body["truncated"] is False