"""Do latency AI Service tren Vercel duoi tai dong thoi (ke hoach Tuan 17, Ngay 4-5). Cach chay: xem INFRA.md muc 40.

Goi POST /ai/chat that (ky HMAC nhu Backend, qua LiveClient) voi cung prompt va cung 3 muc dong thoi 2/5/10 nhu
ai-service/BENCHMARK.md muc 6 de so sanh. Khac BENCHMARK.md: do tai AI Service (Vercel + Cloudflare), khong goi thang Cloudflare.
Request dau tien la cold start: chi co y nghia khi AI Service khong nhan request nao it nhat 30 phut truoc do.
"""
import csv
import math
import os
import statistics
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

from evaluation.live_client import BASE_URL_ENV, LiveClient
from guardrails.s2s_auth import SECRET_ENV_NAME
from guardrails.topic_classifier import OFF_TOPIC_REPLY

RESULTS_DIR = Path(__file__).resolve().parent / "results"
PATH = "/ai/chat"
PROMPT = "What is the difference between 'make' and 'do'? Give me two examples for each."
WARM_REQUESTS = 20
CONCURRENCY_LEVELS = (2, 5, 10)
ROUNDS_PER_LEVEL = 2
FIELDS = ["phase", "concurrency", "round", "index", "status", "ok", "latency_s", "answer_chars", "truncated", "round_wall_s"]


def percentile(values, pct):
    """Nearest-rank, giong tools/llm-benchmark/bench_common.py."""
    ordered = sorted(values)
    return ordered[max(math.ceil(pct / 100 * len(ordered)), 1) - 1]


def timed_chat(client):
    response = client.request("POST", PATH, {"message": PROMPT})
    body = response.body if isinstance(response.body, dict) else {}
    answer = body.get("answer") or ""
    # Cau tra loi co dinh cua bo loc lac de khong qua LLM nen khong tinh la thanh cong
    ok = response.status == 200 and bool(answer) and answer != OFF_TOPIC_REPLY
    return {
        "status": response.status,
        "ok": ok,
        "latency_s": round(response.latency_ms / 1000, 3),
        "answer_chars": len(answer),
        "truncated": bool(body.get("truncated")),
    }


def make_row(phase, concurrency, round_no, index, outcome, round_wall):
    return {
        "phase": phase,
        "concurrency": concurrency,
        "round": round_no,
        "index": index,
        **outcome,
        "round_wall_s": "" if round_wall is None else round(round_wall, 2),
    }


def run_batch(client, level):
    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=level) as pool:
        outcomes = list(pool.map(lambda _: timed_chat(client), range(level)))
    return outcomes, time.perf_counter() - started


def collect(client):
    rows = []
    cold = timed_chat(client)
    rows.append(make_row("cold", 1, 1, 1, cold, None))
    print(f"cold: HTTP {cold['status']}, {cold['latency_s']}s, ok={cold['ok']}")
    if not cold["ok"]:
        return rows

    for index in range(1, WARM_REQUESTS + 1):
        outcome = timed_chat(client)
        rows.append(make_row("warm", 1, 1, index, outcome, None))
        print(f"warm #{index}: HTTP {outcome['status']}, {outcome['latency_s']}s, ok={outcome['ok']}")

    for level in CONCURRENCY_LEVELS:
        for round_no in range(1, ROUNDS_PER_LEVEL + 1):
            outcomes, wall = run_batch(client, level)
            for index, outcome in enumerate(outcomes, start=1):
                rows.append(make_row("concurrent", level, round_no, index, outcome, wall))
            print(f"dong thoi {level} dot {round_no}: {sum(o['ok'] for o in outcomes)}/{level} thanh cong, tong thoi gian {wall:.2f}s")
    return rows


def build_summary(rows):
    cold = rows[0]
    warm = [r for r in rows if r["phase"] == "warm"]
    warm_ok = [r for r in warm if r["ok"]]
    lines = [
        "### Request don le",
        "",
        "| Cold start (s) | Warm thanh cong | p50 (s) | p95 (s) | Lon nhat (s) | Ky tu tra loi TB | Loi |",
        "|---|---|---|---|---|---|---|",
    ]
    cold_text = f"{cold['latency_s']}" if cold["ok"] else f"loi HTTP {cold['status']}"
    errors = sum(1 for r in rows if not r["ok"])
    if warm_ok:
        latencies = [r["latency_s"] for r in warm_ok]
        lines.append(
            f"| {cold_text} | {len(warm_ok)}/{len(warm)} | {percentile(latencies, 50):.2f} | {percentile(latencies, 95):.2f} | "
            f"{max(latencies):.2f} | {statistics.mean(r['answer_chars'] for r in warm_ok):.0f} | {errors} |"
        )
    else:
        lines.append(f"| {cold_text} | 0/{len(warm)} | - | - | - | - | {errors} |")

    lines += [
        "",
        "### Dong thoi",
        "",
        "| Dong thoi | Request thanh cong | p50 (s) | Lon nhat (s) | Tong thoi gian 1 dot TB (s) |",
        "|---|---|---|---|---|",
    ]
    for level in CONCURRENCY_LEVELS:
        level_rows = [r for r in rows if r["phase"] == "concurrent" and r["concurrency"] == level]
        ok_rows = [r for r in level_rows if r["ok"]]
        if not ok_rows:
            lines.append(f"| {level} | 0/{len(level_rows)} | - | - | - |")
            continue
        latencies = [r["latency_s"] for r in ok_rows]
        walls = {r["round"]: r["round_wall_s"] for r in level_rows}.values()
        lines.append(
            f"| {level} | {len(ok_rows)}/{len(level_rows)} | {percentile(latencies, 50):.2f} | {max(latencies):.2f} | "
            f"{statistics.mean(walls):.2f} |"
        )
    return "\n".join(lines) + "\n"


def main():
    base_url = os.environ.get(BASE_URL_ENV, "")
    secret = os.environ.get(SECRET_ENV_NAME, "")
    if not base_url or not secret:
        sys.exit(f"Thieu bien moi truong {BASE_URL_ENV} hoac {SECRET_ENV_NAME}")

    rows = collect(LiveClient(base_url, secret))

    RESULTS_DIR.mkdir(exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    csv_path = RESULTS_DIR / f"ai-load-{stamp}.csv"
    with csv_path.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    summary = build_summary(rows)
    (RESULTS_DIR / f"ai-load-{stamp}-summary.md").write_text(summary, encoding="utf-8")
    print()
    print(summary)
    print(f"Da ghi {csv_path}")


if __name__ == "__main__":
    main()
