"""Ham dung chung cho cac script benchmark Ngay 5: goi model co do thoi gian, tach noi dung tra loi, tinh phan vi."""
import math
import os
import sys
import time
import urllib.error
import http.client

import resource_benchmark as base

# 5 model ung vien (ai-service/BENCHMARK.md muc 2)
CANDIDATES = [
    "@cf/meta/llama-3.2-1b-instruct",
    "@cf/meta/llama-3.2-3b-instruct",
    "@cf/meta/llama-3.1-8b-instruct-fp8",
    "@cf/google/gemma-4-26b-a4b-it",
    "@cf/qwen/qwen3-30b-a3b-fp8",
]


def get_credentials():
    account_id = os.environ.get(base.ACCOUNT_ID_ENV)
    token = os.environ.get(base.API_TOKEN_ENV)
    if not account_id or not token:
        sys.exit(f"Thieu bien moi truong {base.ACCOUNT_ID_ENV} hoac {base.API_TOKEN_ENV}")
    return account_id, token


def extract_text(result):
    """Tra ve noi dung tra loi cuoi (bo phan suy luan). Ho tro ca dang choices va dang response cua Workers AI."""
    choices = result.get("choices")
    if choices:
        message = choices[0].get("message") or {}
        content = message.get("content") or choices[0].get("text") or ""
    else:
        content = result.get("response") or ""
    if "</think>" in content:
        content = content.rpartition("</think>")[2]
    return content.strip()


def timed_request(account_id, token, model, prompt, system_prompt=base.SYSTEM_PROMPT, max_tokens=base.MAX_TOKENS):
    outcome = {
        "ok": False,
        "latency": 0.0,
        "prompt_tokens": 0,
        "completion_tokens": 0,
        "truncated": False,
        "neurons": 0.0,
        "text": "",
        "error": "",
    }
    started = time.perf_counter()
    try:
        result = base.call_model(account_id, token, model, prompt, system_prompt, max_tokens)
        outcome["latency"] = time.perf_counter() - started
        prompt_tokens, completion_tokens, finish_reason = base.read_usage(result)
    except urllib.error.HTTPError as error:
        outcome["latency"] = time.perf_counter() - started
        outcome["error"] = f"HTTP {error.code}: {error.read().decode('utf-8', 'replace')[:300]}"
        return outcome
    except (urllib.error.URLError, http.client.HTTPException, OSError, RuntimeError, ValueError, KeyError) as error:
        outcome["latency"] = time.perf_counter() - started
        outcome["error"] = f"{type(error).__name__}: {error}"
        return outcome

    rate_in, rate_out = base.MODELS[model]
    outcome["ok"] = True
    outcome["prompt_tokens"] = prompt_tokens
    outcome["completion_tokens"] = completion_tokens
    outcome["truncated"] = finish_reason == "length" or completion_tokens >= max_tokens
    outcome["neurons"] = (prompt_tokens * rate_in + completion_tokens * rate_out) / 1_000_000
    outcome["text"] = extract_text(result)
    return outcome


def percentile(values, pct):
    """Phan vi theo nearest-rank: gia tri thu ceil(pct/100 * n) trong danh sach da sap xep."""
    ordered = sorted(values)
    rank = max(1, math.ceil(pct / 100 * len(ordered)))
    return ordered[rank - 1]