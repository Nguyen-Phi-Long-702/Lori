from pydantic import BaseModel, Field


class VocabularyRequest(BaseModel):
    # Mot tu/cum tu, hoac hai tu cach nhau bang dau phay, dau gach cheo hoac "vs"
    text: str
    learning_context: dict | None = Field(default=None, alias="learningContext")