from pydantic import BaseModel, Field


class GrammarRequest(BaseModel):
    text: str
    # {id: ten} cac bai ngu phap cua Lori do Backend gui; model chi duoc lien ket toi id trong danh sach nay
    lessons: dict[int, str]
    learning_context: dict | None = Field(default=None, alias="learningContext")