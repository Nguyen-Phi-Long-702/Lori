from fastapi import APIRouter, Depends, HTTPException, Request

from guardrails.s2s_auth import verify_backend_request
from models.analysis import ExamAnalysisRequest, QuizAnalysisRequest
from orchestrator.ai_orchestrator import TaskType
from providers.base import ProviderError

router = APIRouter()


@router.post("/ai/analyze-quiz", dependencies=[Depends(verify_backend_request)])
def analyze_quiz(body: QuizAnalysisRequest, request: Request):
    return _handle(request, TaskType.ANALYZE_QUIZ, body.model_dump(by_alias=True))


@router.post("/ai/analyze-exam", dependencies=[Depends(verify_backend_request)])
def analyze_exam(body: ExamAnalysisRequest, request: Request):
    return _handle(request, TaskType.ANALYZE_EXAM, body.model_dump(by_alias=True))


def _handle(request: Request, task_type: TaskType, payload: dict):
    try:
        return request.app.state.orchestrator.handle(task_type.value, payload)
    except ProviderError:
        raise HTTPException(status_code=502, detail="AI provider error") from None