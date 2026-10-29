from fastapi import Depends, FastAPI

from guardrails.s2s_auth import verify_backend_request
from orchestrator.ai_orchestrator import AiOrchestrator, TaskType
from orchestrator.analysis_workflow import exam_analysis_workflow, quiz_analysis_workflow
from orchestrator.chat_workflow import chat_workflow
from orchestrator.grammar_workflow import grammar_workflow
from orchestrator.vocabulary_workflow import vocabulary_workflow
from providers.factory import create_provider
from routers.analysis import router as analysis_router
from routers.chat import router as chat_router
from routers.grammar import router as grammar_router
from routers.health import router as health_router
from routers.vocabulary import router as vocabulary_router

app = FastAPI()
app.state.orchestrator = AiOrchestrator(
    create_provider(),
    {
        TaskType.CHAT: chat_workflow,
        TaskType.GRAMMAR: grammar_workflow,
        TaskType.VOCABULARY: vocabulary_workflow,
        TaskType.ANALYZE_QUIZ: quiz_analysis_workflow,
        TaskType.ANALYZE_EXAM: exam_analysis_workflow,
    },
)
app.include_router(health_router)
app.include_router(chat_router)
app.include_router(grammar_router)
app.include_router(vocabulary_router)
app.include_router(analysis_router)


@app.get("/")
def hello():
    return {"message": "Hello from Lori AI Service"}


@app.post("/ai/ping", dependencies=[Depends(verify_backend_request)])
def ping():
    return {"status": "ok"}