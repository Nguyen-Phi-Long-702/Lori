from fastapi import APIRouter, Depends, Request

from guardrails.s2s_auth import verify_backend_request

router = APIRouter()


@router.get("/ai/health", dependencies=[Depends(verify_backend_request)])
def health(request: Request):
    provider = request.app.state.orchestrator.provider
    return {
        "status": "ok" if provider.is_available() else "degraded",
        "provider": provider.name,
    }