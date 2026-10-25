from collections.abc import Callable, Mapping
from enum import Enum

from providers.base import AiProvider


class TaskType(str, Enum):
    CHAT = "chat"
    GRAMMAR = "grammar"


class UnsupportedTaskError(Exception):
    """task_type khong hop le hoac chua co workflow."""


# Workflow nhan provider va payload tu Backend, tra ve response co cau truc (dict co truong "type")
Workflow = Callable[[AiProvider, dict], dict]


class AiOrchestrator:
    def __init__(self, provider: AiProvider, workflows: Mapping[TaskType, Workflow] | None = None):
        self.provider = provider
        self._workflows = dict(workflows or {})

    def handle(self, task_type: str, payload: dict) -> dict:
        try:
            workflow = self._workflows[TaskType(task_type)]
        except (ValueError, KeyError):
            raise UnsupportedTaskError(f"task_type chua duoc ho tro: {task_type}") from None
        return workflow(self.provider, payload)