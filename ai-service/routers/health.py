from fastapi import APIRouter, Request

router = APIRouter()


@router.get("/ai/health")
def health(request: Request):
    provider = request.app.state.orchestrator.provider
    return {
        "status": "ok" if provider.is_available() else "degraded",
        "provider": provider.name,
    }