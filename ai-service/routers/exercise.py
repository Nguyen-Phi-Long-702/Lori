from fastapi import APIRouter, Depends, HTTPException, Request

from guardrails.s2s_auth import verify_backend_request
from models.exercise import ExerciseRequest
from orchestrator.ai_orchestrator import TaskType
from providers.base import ProviderError

router = APIRouter()


@router.post("/ai/generate-exercise", dependencies=[Depends(verify_backend_request)])
def generate_exercise(body: ExerciseRequest, request: Request):
    try:
        return request.app.state.orchestrator.handle(
            TaskType.GENERATE_EXERCISE.value, body.model_dump(by_alias=True)
        )
    except ProviderError:
        raise HTTPException(status_code=502, detail="AI provider error") from None