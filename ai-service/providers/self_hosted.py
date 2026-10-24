import http.client
import json
import logging
import os
import urllib.error
import urllib.request

from config.model_config import PRIMARY_MODEL, SYSTEM_PROMPT, TEMPERATURE, ModelSettings
from providers.base import AiProvider, LlmResponse, ProviderError

logger = logging.getLogger(__name__)

ACCOUNT_ID_ENV = "CLOUDFLARE_ACCOUNT_ID"
API_TOKEN_ENV = "CLOUDFLARE_API_TOKEN"
API_URL = "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run/{model}"
# Nho hon read timeout 30 giay cua AiServiceClient (Backend) de AI Service tra loi loi truoc khi Backend bo cuoc
TIMEOUT_SECONDS = 25
MAX_ERROR_DETAIL = 300


class SelfHostedLlmProvider(AiProvider):
    """LLM open-weight chay tren Cloudflare Workers AI (thay cho Ollama tren VM Oracle trong ke hoach)."""

    name = "self_hosted"

    def __init__(self, model: ModelSettings = PRIMARY_MODEL):
        self._model = model

    def is_available(self) -> bool:
        return bool(os.environ.get(ACCOUNT_ID_ENV) and os.environ.get(API_TOKEN_ENV))

    def generate(self, prompt: str) -> LlmResponse:
        if not self.is_available():
            raise ProviderError(f"Thieu bien moi truong {ACCOUNT_ID_ENV} hoac {API_TOKEN_ENV}")
        result = self._call(prompt)
        usage = result.get("usage") or {}
        choices = result.get("choices") or [{}]
        completion_tokens = usage.get("completion_tokens", 0)
        return LlmResponse(
            text=_extract_text(result),
            model_id=self._model.model_id,
            prompt_tokens=usage.get("prompt_tokens", 0),
            completion_tokens=completion_tokens,
            truncated=choices[0].get("finish_reason") == "length"
            or completion_tokens >= self._model.max_output_tokens,
        )

    def _call(self, prompt: str) -> dict:
        body = json.dumps(
            {
                "messages": [
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": prompt},
                ],
                "max_tokens": self._model.max_output_tokens,
                "temperature": TEMPERATURE,
            }
        ).encode("utf-8")
        request = urllib.request.Request(
            API_URL.format(account_id=os.environ[ACCOUNT_ID_ENV], model=self._model.model_id),
            data=body,
            method="POST",
            headers={
                "Authorization": f"Bearer {os.environ[API_TOKEN_ENV]}",
                "Content-Type": "application/json",
                "User-Agent": "lori-ai-service/1.0",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", "replace")[:MAX_ERROR_DETAIL]
            logger.error("Cloudflare Workers AI tra HTTP %s", error.code)
            raise ProviderError(f"Cloudflare HTTP {error.code}: {detail}") from error
        except (OSError, http.client.HTTPException, ValueError) as error:
            logger.error("Goi Cloudflare Workers AI that bai: %s", type(error).__name__)
            raise ProviderError(f"Khong goi duoc Cloudflare: {type(error).__name__}") from error
        result = payload.get("result")
        if not payload.get("success") or not isinstance(result, dict):
            raise ProviderError(f"Cloudflare bao loi: {payload.get('errors')}")
        return result


def _extract_text(result: dict) -> str:
    # Model co suy luan (qwen3, gemma-4) co the tra kem phan suy luan truoc </think>, chi giu phan tra loi cuoi
    choices = result.get("choices")
    if choices:
        message = choices[0].get("message") or {}
        content = message.get("content") or choices[0].get("text") or ""
    else:
        content = result.get("response") or ""
    return content.rpartition("</think>")[2].strip()