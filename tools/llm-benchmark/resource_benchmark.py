"""Do muc tieu thu Neurons cua cac model LLM ung vien tren Cloudflare Workers AI (ke hoach Tuan 10, Ngay 3-4).

Cach dung: xem ai-service/BENCHMARK.md muc 4.
Moi cap (model, prompt) chay RUNS_PER_PROMPT lan. Dau ra: file CSV trong thu muc results/ va bang tong hop in ra man hinh.
"""
import csv
import json
import os
import statistics
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ACCOUNT_ID_ENV = "CLOUDFLARE_ACCOUNT_ID"
API_TOKEN_ENV = "CLOUDFLARE_API_TOKEN"
API_URL = "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run/{model}"
RUNS_PER_PROMPT = 3
MAX_TOKENS = 1024
TEMPERATURE = 0
TIMEOUT_SECONDS = 120
DAILY_FREE_NEURONS = 10000
RESULTS_DIR = Path(__file__).parent / "results"

# Don gia Neuron cho 1 trieu token (vao, ra), lay tu trang pricing cua Cloudflare (kiem tra 23/10/2026)
MODELS = {
    "@cf/meta/llama-3.2-1b-instruct": (2457, 18252),
    "@cf/meta/llama-3.2-3b-instruct": (4625, 30475),
    "@cf/meta/llama-3.1-8b-instruct-fp8": (13778, 26128),
    "@cf/google/gemma-4-26b-a4b-it": (9091, 27273),
    "@cf/qwen/qwen3-30b-a3b-fp8": (4625, 30475),
    # 2 model duoi day chi de so sanh quantization voi ban fp8 o tren; Cloudflare thong bao ngung hoat dong tu 30/05/2026,
    # neu goi loi thi script bo qua model do
    "@cf/meta/llama-3.1-8b-instruct-awq": (11161, 24215),
    "@cf/meta/llama-3.1-8b-instruct": (25608, 75147),
}

SYSTEM_PROMPT = "You are an English tutor for Vietnamese learners. Answer in clear, simple English."

LEARNER_PROFILE = """Learner profile:
- Level: intermediate, around TOEIC 550.
- Goal: reach TOEIC 750 in three months.
- Weak topics: past perfect, conditional sentences, prepositions of time, prepositions after adjectives, business vocabulary.
- Recent mistakes:
  1. "If I will have time, I will call you."
  2. "She has finished the report yesterday."
  3. "I am interested on this job."
  4. "We have met him in 2019."
  5. "The meeting will start at the 9 o'clock."
  6. "He is good in English."
  7. "I have lived here since five years."
  8. "If she would study harder, she would pass the exam."
- Recent quiz results: grammar 55 percent, vocabulary 70 percent, reading 62 percent, listening 58 percent.
- Study habit: about 20 minutes per day, mostly in the evening, three to four days per week.
- Preferred explanation style: short rules with examples."""

PROMPTS = {
    "chat": "What is the difference between 'make' and 'do'? Give me two examples for each.",
    "grammar": "Check this sentence, correct it and explain the error: \"She don't like going to school yesterday.\"",
    "vocabulary": "Explain the word \"reluctant\": give its meaning, two example sentences, one synonym and one antonym.",
    "context": LEARNER_PROFILE + "\n\nBased on this profile, explain the difference between the present perfect and the past simple, "
    "using examples that match my weak points.",
    "analysis": "A learner got these TOEIC Part 5 questions wrong. Find the common pattern and suggest what to review.\n"
    "1. The manager ___ that we postpone the meeting. (A) suggest (B) suggests (C) suggested (D) suggesting. The learner chose (B).\n"
    "2. All employees must submit the form ___ Friday. (A) in (B) by (C) at (D) on. The learner chose (A).\n"
    "3. She is responsible ___ training new staff. (A) of (B) to (C) for (D) with. The learner chose (A).",
}


def call_model(account_id, token, model, prompt, system_prompt=SYSTEM_PROMPT, max_tokens=MAX_TOKENS):
    body = json.dumps(
        {
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": prompt},
            ],
            "max_tokens": max_tokens,
            "temperature": TEMPERATURE,
        }
    ).encode("utf-8")
    request = urllib.request.Request(
        API_URL.format(account_id=account_id, model=model),
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "User-Agent": "lori-llm-benchmark/1.0",
        },
    )
    with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
        payload = json.loads(response.read().decode("utf-8"))
    if not payload.get("success"):
        raise RuntimeError(f"Cloudflare bao loi: {payload.get('errors')}")
    return payload["result"]


def read_usage(result):
    usage = result.get("usage")
    if not usage:
        raise RuntimeError("Phan hoi khong co truong usage")
    finish_reason = ""
    if result.get("choices"):
        finish_reason = result["choices"][0].get("finish_reason") or ""
    return usage["prompt_tokens"], usage["completion_tokens"], finish_reason


def measure_model(account_id, token, model):
    rate_in, rate_out = MODELS[model]
    rows = []
    for prompt_id, prompt in PROMPTS.items():
        for run in range(1, RUNS_PER_PROMPT + 1):
            try:
                result = call_model(account_id, token, model, prompt)
                prompt_tokens, completion_tokens, finish_reason = read_usage(result)
            except urllib.error.HTTPError as error:
                detail = error.read().decode("utf-8", "replace")[:300]
                print(f"  LOI HTTP {error.code}: {detail}")
                print("  Bo qua phan con lai cua model nay")
                return rows
            except (urllib.error.URLError, TimeoutError, RuntimeError, ValueError, KeyError) as error:
                print(f"  LOI: {error}")
                print("  Bo qua phan con lai cua model nay")
                return rows
            neurons = (prompt_tokens * rate_in + completion_tokens * rate_out) / 1_000_000
            rows.append(
                {
                    "timestamp_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                    "model": model,
                    "prompt_id": prompt_id,
                    "run": run,
                    "prompt_tokens": prompt_tokens,
                    "completion_tokens": completion_tokens,
                    "finish_reason": finish_reason,
                    "neurons": round(neurons, 4),
                }
            )
            print(f"  {prompt_id} #{run}: vao={prompt_tokens} ra={completion_tokens} neurons={neurons:.2f} ({finish_reason or '-'})")
    return rows


def print_summary(rows):
    by_model = {}
    for row in rows:
        by_model.setdefault(row["model"], []).append(row)

    print("\n### Bang tong hop theo model")
    print("| Model | So request | Token vao TB | Token ra TB | Neurons/request TB | Neurons/request lon nhat | Bi cat (length) | So request/ngay |")
    print("|---|---|---|---|---|---|---|---|")
    for model, model_rows in by_model.items():
        mean_neurons = statistics.mean(r["neurons"] for r in model_rows)
        max_neurons = max(r["neurons"] for r in model_rows)
        mean_in = statistics.mean(r["prompt_tokens"] for r in model_rows)
        mean_out = statistics.mean(r["completion_tokens"] for r in model_rows)
        truncated = sum(1 for r in model_rows if r["finish_reason"] == "length" or r["completion_tokens"] >= MAX_TOKENS)
        per_day = DAILY_FREE_NEURONS / mean_neurons
        print(f"| `{model}` | {len(model_rows)} | {mean_in:.0f} | {mean_out:.0f} | {mean_neurons:.2f} | {max_neurons:.2f} | {truncated} | {per_day:.0f} |")


def main():
    account_id = os.environ.get(ACCOUNT_ID_ENV)
    token = os.environ.get(API_TOKEN_ENV)
    if not account_id or not token:
        sys.exit(f"Thieu bien moi truong {ACCOUNT_ID_ENV} hoac {API_TOKEN_ENV}")

    rows = []
    for model in MODELS:
        print(model)
        rows.extend(measure_model(account_id, token, model))
    if not rows:
        sys.exit("Khong co request nao thanh cong")

    RESULTS_DIR.mkdir(exist_ok=True)
    csv_path = RESULTS_DIR / f"resource-{datetime.now(timezone.utc):%Y%m%d-%H%M%S}.csv"
    with open(csv_path, "w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)

    print_summary(rows)
    print(f"\nTong Neurons cua lan chay nay: {sum(r['neurons'] for r in rows):.2f}")
    print(f"Da luu: {csv_path}")


if __name__ == "__main__":
    main()