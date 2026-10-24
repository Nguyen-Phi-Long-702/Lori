from abc import ABC, abstractmethod
from dataclasses import dataclass


class ProviderError(Exception):
    """Provider khong tra duoc ket qua (thieu cau hinh, loi mang, loi tu nha cung cap)."""


@dataclass(frozen=True)
class LlmResponse:
    text: str
    model_id: str
    prompt_tokens: int
    completion_tokens: int
    truncated: bool


class AiProvider(ABC):
    name: str

    @abstractmethod
    def generate(self, prompt: str) -> LlmResponse:
        """Gui prompt cua hoc vien, tra ve cau tra loi cuoi (da bo phan suy luan)."""

    @abstractmethod
    def is_available(self) -> bool:
        """Kiem tra cau hinh, khong goi mang."""