from fastapi import Depends, FastAPI

from guardrails.s2s_auth import verify_backend_request

app = FastAPI()


@app.get("/")
def hello():
    return {"message": "Hello from Lori AI Service"}


@app.post("/ai/ping", dependencies=[Depends(verify_backend_request)])
def ping():
    return {"status": "ok"}