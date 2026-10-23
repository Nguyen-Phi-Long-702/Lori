"""Do latency, throughput va concurrency cua cac model LLM ung vien tren Cloudflare Workers AI (ke hoach Tuan 10, Ngay 5).

Cach dung: xem ai-service/BENCHMARK.md muc 6.
Moi model: 1 request dau tien (cold), WARM_REQUESTS request tuan tu (warm), roi CONCURRENCY_LEVELS x ROUNDS_PER_LEVEL dot request dong thoi.
Dau ra: file CSV va file tong hop .md trong thu muc results/.
"""
import csv
import statistics
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

import bench_common as common
import resource_benchmark as base

PROMPT = base.PROMPTS["chat"]
WARM_REQUESTS = 20
CONCURRENCY_LEVELS = (2, 5, 10)
ROUNDS_PER_LEVEL = 2


def make_row(model, phase, concurrency, round_no, outcome, round_wall):
    ok = outcome["ok"]
    tokens_per_sec = outcome["completion_tokens"] / outcome["latency"] if ok and outcome["latency"] > 0 else ""
    return {
        "timestamp_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "model": model,
        "phase": phase,
        "concurrency": concurrency,
        "round": round_no,
        "ok": int(ok),
        "latency_s": round(outcome["latency"], 3),
        "prompt_tokens": outcome["prompt_tokens"],
        "completion_tokens": outcome["completion_tokens"],
        "tokens_per_sec": round(tokens_per_sec, 2) if ok else "",
        "truncated": int(outcome["truncated"]),
        "neurons": round(outcome["neurons"], 4),
        "round_wall_s": round(round_wall, 3) if round_wall else "",
        "error": outcome["error"],
    }


def print_row(label, row):
    if row["ok"]:
        print(f"  {label}: {row['latency_s']}s vao={row['prompt_tokens']} ra={row['completion_tokens']} ({row['tokens_per_sec']} token/s)")
    else:
        print(f"  {label}: LOI {row['error']}")


def run_batch(account_id, token, model, size):
    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=size) as pool:
        futures = [pool.submit(common.timed_request, account_id, token, model, PROMPT) for _ in range(size)]
        outcomes = [future.result() for future in futures]
    return outcomes, time.perf_counter() - started


def measure_model(account_id, token, model):
    rows = []
    outcome = common.timed_request(account_id, token, model, PROMPT)
    row = make_row(model, "cold", 1, 1, outcome, None)
    rows.append(row)
    print_row("cold", row)
    if not outcome["ok"]:
        print("  Bo qua phan con lai cua model nay")
        return rows

    for index in range(1, WARM_REQUESTS + 1):
        outcome = common.timed_request(account_id, token, model, PROMPT)
        row = make_row(model, "warm", 1, index, outcome, None)
        rows.append(row)
        print_row(f"warm #{index}", row)

    for level in CONCURRENCY_LEVELS:
        for round_no in range(1, ROUNDS_PER_LEVEL + 1):
            outcomes, wall = run_batch(account_id, token, model, level)
            level_rows = [make_row(model, "concurrent", level, round_no, item, wall) for item in outcomes]
            rows.extend(level_rows)
            ok_count = sum(r["ok"] for r in level_rows)
            print(f"  dong thoi {level} dot {round_no}: {ok_count}/{level} thanh cong, tong thoi gian {wall:.2f}s")
    return rows


def build_summary(rows):
    by_model = {}
    for row in rows:
        by_model.setdefault(row["model"], []).append(row)

    single = [
        "### Request don le (cold start va warm)",
        "",
        "| Model | Cold start (s) | Warm thanh cong | p50 (s) | p95 (s) | Token/s (trung vi) | Token ra TB | Bi cat (length) | Loi |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    concurrent = [
        "### Dong thoi (1 = request tuan tu o pha warm)",
        "",
        "| Model | Dong thoi | Request thanh cong | p50 (s) | Lon nhat (s) | Throughput tong (token/s) |",
        "|---|---|---|---|---|---|",
    ]
    for model, model_rows in by_model.items():
        cold = next(r for r in model_rows if r["phase"] == "cold")
        warm = [r for r in model_rows if r["phase"] == "warm"]
        warm_ok = [r for r in warm if r["ok"]]
        errors = sum(1 for r in model_rows if not r["ok"])
        truncated = sum(r["truncated"] for r in model_rows)
        cold_text = f"{cold['latency_s']}" if cold["ok"] else "loi"
        if not warm_ok:
            single.append(f"| `{model}` | {cold_text} | 0/{len(warm)} | - | - | - | - | {truncated} | {errors} |")
            continue

        latencies = [r["latency_s"] for r in warm_ok]
        p50 = common.percentile(latencies, 50)
        p95 = common.percentile(latencies, 95)
        tokens_per_sec = statistics.median(r["tokens_per_sec"] for r in warm_ok)
        mean_out = statistics.mean(r["completion_tokens"] for r in warm_ok)
        single.append(
            f"| `{model}` | {cold_text} | {len(warm_ok)}/{len(warm)} | {p50:.2f} | {p95:.2f} | {tokens_per_sec:.1f} | {mean_out:.0f} | {truncated} | {errors} |"
        )
        total_tokens = sum(r["completion_tokens"] for r in warm_ok)
        concurrent.append(
            f"| `{model}` | 1 | {len(warm_ok)}/{len(warm)} | {p50:.2f} | {max(latencies):.2f} | {total_tokens / sum(latencies):.1f} |"
        )

        for level in CONCURRENCY_LEVELS:
            level_rows = [r for r in model_rows if r["phase"] == "concurrent" and r["concurrency"] == level]
            ok_rows = [r for r in level_rows if r["ok"]]
            if not ok_rows:
                concurrent.append(f"| `{model}` | {level} | 0/{len(level_rows)} | - | - | - |")
                continue
            level_latencies = [r["latency_s"] for r in ok_rows]
            round_throughputs = []
            for round_no in range(1, ROUNDS_PER_LEVEL + 1):
                round_ok = [r for r in ok_rows if r["round"] == round_no]
                if round_ok:
                    round_throughputs.append(sum(r["completion_tokens"] for r in round_ok) / round_ok[0]["round_wall_s"])
            concurrent.append(
                f"| `{model}` | {level} | {len(ok_rows)}/{len(level_rows)} | {common.percentile(level_latencies, 50):.2f} | "
                f"{max(level_latencies):.2f} | {statistics.mean(round_throughputs):.1f} |"
            )
    return single + [""] + concurrent


def main():
    account_id, token = common.get_credentials()
    rows = []
    for model in common.CANDIDATES:
        print(model)
        rows.extend(measure_model(account_id, token, model))

    stamp = f"{datetime.now(timezone.utc):%Y%m%d-%H%M%S}"
    base.RESULTS_DIR.mkdir(exist_ok=True)
    csv_path = base.RESULTS_DIR / f"latency-{stamp}.csv"
    with open(csv_path, "w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)

    summary = build_summary(rows)
    summary_path = base.RESULTS_DIR / f"latency-{stamp}-summary.md"
    summary_path.write_text("\n".join(summary) + "\n", encoding="utf-8")

    print()
    print("\n".join(summary))
    print(f"\nTong Neurons cua lan chay nay: {sum(r['neurons'] for r in rows):.2f}")
    print(f"Da luu: {csv_path}")
    print(f"Da luu: {summary_path}")


if __name__ == "__main__":
    main()