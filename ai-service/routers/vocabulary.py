from fastapi import APIRouter, HTTPException, Request

from guardrails.ai_input_sanitizer import InvalidInputError
from models.vocabulary import VocabularyRequest
from orchestrator.ai_orchestrator import TaskType
from providers.base import ProviderError

router = APIRouter()


@router.post("/ai/vocabulary")
def vocabulary(body: VocabularyRequest, request: Request):
    payload = {"text": body.text, "learningContext": body.learning_context}
    try:
        return request.app.state.orchestrator.handle(TaskType.VOCABULARY.value, payload)
    except InvalidInputError:
        raise HTTPException(status_code=400, detail="Invalid input") from None
    except ProviderError:
        raise HTTPException(status_code=502, detail="AI provider error") from None