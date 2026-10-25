from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    message: str
    # Learning Context do Backend gui (JSON camelCase, xem LearningContext.java), khong co thi chat khong ca nhan hoa
    learning_context: dict | None = Field(default=None, alias="learningContext")