from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.concurrency import run_in_threadpool

from guardrails.s2s_auth import verify_backend_request
from orchestrator.ai_orchestrator import TaskType
from providers.yolo_detector import InvalidImageError

router = APIRouter()


# Body la byte cua anh (khong phai JSON): chu ky HMAC cua Backend ky tren dung cac byte nay
@router.post("/ai/identify-image", dependencies=[Depends(verify_backend_request)])
async def identify_image(request: Request):
    payload = {"image": await request.body()}
    try:
        # Suy luan YOLO va goi LLM deu chan, chay trong thread de khong khoa event loop
        return await run_in_threadpool(request.app.state.orchestrator.handle, TaskType.IDENTIFY_IMAGE.value, payload)
    except InvalidImageError:
        raise HTTPException(status_code=400, detail="Invalid input") from None