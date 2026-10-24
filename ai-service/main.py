from fastapi import Depends, FastAPI

from guardrails.s2s_auth import verify_backend_request
from orchestrator.ai_orchestrator import AiOrchestrator
from providers.factory import create_provider
from routers.health import router as health_router

app = FastAPI()
app.state.orchestrator = AiOrchestrator(create_provider())
app.include_router(health_router)


@app.get("/")
def hello():
    return {"message": "Hello from Lori AI Service"}


@app.post("/ai/ping", dependencies=[Depends(verify_backend_request)])
def ping():
    return {"status": "ok"}