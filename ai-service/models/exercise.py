from typing import Literal

from pydantic import BaseModel, Field


class ExerciseRequest(BaseModel):
    category: Literal["grammar", "vocabulary"]
    # Ten bai ngu phap / chu de tu vung do Backend lay tu learning-names.json (chu de tu vung bang tieng Viet)
    topic: str
    # Trinh do uoc tinh do Learning Engine tinh
    level: Literal["A1", "A2", "B1", "B2", "C1"]
    # Cac cau hoi da sinh truoc do cho chu de nay, de model khong lap lai
    avoidQuestions: list[str] = []
    learning_context: dict | None = Field(default=None, alias="learningContext")