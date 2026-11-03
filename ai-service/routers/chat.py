from fastapi import APIRouter, HTTPException, Request

from guardrails.ai_input_sanitizer import InvalidInputError
from models.chat import ChatRequest
from orchestrator.ai_orchestrator import TaskType
from providers.base import ProviderError

router = APIRouter()


# def (khong phai async def): provider goi mang kieu chan, FastAPI se chay trong thread rieng
@router.post("/ai/chat")
def chat(body: ChatRequest, request: Request):
    payload = {"message": body.message, "learningContext": body.learning_context}
    try:
        return request.app.state.orchestrator.handle(TaskType.CHAT.value, payload)
    except InvalidInputError:
        raise HTTPException(status_code=400, detail="Invalid input") from None
    except ProviderError:
        # Khong tra noi dung loi cua provider cho Backend (co the chua chi tiet tu Cloudflare)
        raise HTTPException(status_code=502, detail="AI provider error") from None