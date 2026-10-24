import pytest

from orchestrator.ai_orchestrator import AiOrchestrator, TaskType, UnsupportedTaskError
from providers.mock import MockProvider


def chat_workflow(provider, payload):
    reply = provider.generate(payload["message"])
    return {"type": "chat", "answer": reply.text}


def test_routes_task_type_to_its_workflow():
    orchestrator = AiOrchestrator(MockProvider(reply="Hi"), {TaskType.CHAT: chat_workflow})
    assert orchestrator.handle("chat", {"message": "Hello"}) == {"type": "chat", "answer": "Hi"}


def test_unknown_task_type_is_rejected():
    orchestrator = AiOrchestrator(MockProvider(), {TaskType.CHAT: chat_workflow})
    with pytest.raises(UnsupportedTaskError, match="nonsense"):
        orchestrator.handle("nonsense", {})


def test_known_task_type_without_workflow_is_rejected():
    with pytest.raises(UnsupportedTaskError, match="chat"):
        AiOrchestrator(MockProvider()).handle("chat", {"message": "Hello"})


def test_workflow_receives_the_orchestrator_provider():
    provider = MockProvider()
    received = {}

    def spy(given_provider, payload):
        received["provider"] = given_provider
        return {"type": "chat"}

    AiOrchestrator(provider, {TaskType.CHAT: spy}).handle("chat", {})
    assert received["provider"] is provider