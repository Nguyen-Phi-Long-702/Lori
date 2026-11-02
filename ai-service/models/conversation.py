from typing import Literal

from pydantic import BaseModel, Field

from config.conversation_modes import MAX_TURNS


class ConversationTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str


class ConversationRequest(BaseModel):
    mode: str
    # Toan bo cac luot tu dau buoi (client giu lich su, AI Service khong luu gi); rong khi AI mo dau buoi hoi thoai
    messages: list[ConversationTurn] = Field(default_factory=list, max_length=MAX_TURNS)
    # true: ket thuc buoi, tra nhan xet thay vi cau tra loi tiep theo
    finish: bool = False
    # Chi co khi finish = true (Backend khong gui o cac luot tro chuyen)
    learning_context: dict | None = Field(default=None, alias="learningContext")