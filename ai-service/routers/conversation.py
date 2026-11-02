from fastapi import APIRouter, Depends, HTTPException, Request

from guardrails.ai_input_sanitizer import InvalidInputError
from guardrails.s2s_auth import verify_backend_request
from models.conversation import ConversationRequest
from orchestrator.ai_orchestrator import TaskType
from providers.base import ProviderError

router = APIRouter()


@router.post("/ai/conversation", dependencies=[Depends(verify_backend_request)])
def conversation(body: ConversationRequest, request: Request):
    try:
        return request.app.state.orchestrator.handle(TaskType.CONVERSATION.value, body.model_dump(by_alias=True))
    except InvalidInputError:
        raise HTTPException(status_code=400, detail="Invalid input") from None
    except ProviderError:
        raise HTTPException(status_code=502, detail="AI provider error") from None