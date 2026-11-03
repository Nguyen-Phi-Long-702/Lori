from fastapi import APIRouter, HTTPException, Request

from models.exercise import ExerciseRequest
from orchestrator.ai_orchestrator import TaskType
from providers.base import ProviderError

router = APIRouter()


@router.post("/ai/generate-exercise")
def generate_exercise(body: ExerciseRequest, request: Request):
    try:
        return request.app.state.orchestrator.handle(
            TaskType.GENERATE_EXERCISE.value, body.model_dump(by_alias=True)
        )
    except ProviderError:
        raise HTTPException(status_code=502, detail="AI provider error") from None