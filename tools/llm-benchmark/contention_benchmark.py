"""Do resource contention khi YOLO va LLM chay dong thoi trong cung mot container 1 vCPU / 2 GB (ke hoach Tuan 10, Ngay 6).

Cach dung: xem ai-service/BENCHMARK.md muc 11.
Cac pha: (1) LLM chay mot minh, (2) nap YOLO va YOLO chay mot minh, (3) YOLO chay lien tuc trong khi gui lai cac request LLM.
Dau ra: file CSV va file tong hop .md trong thu muc results/.
"""
import csv
import resource
import sys
import threading
import time
from datetime import datetime, timezone
from importlib.metadata import version
from pathlib import Path

import bench_common as common
import resource_benchmark as base

HERE = Path(__file__).resolve().parent
sys.path.append(str(HERE.parent.parent / "ai-service"))
from config import model_config as cfg

WEIGHTS = "/opt/yolo/yolov8n.pt"
LLM_REQUESTS = 10
YOLO_RUNS = 20
IMAGE_SIZE = 640
PROMPT = base.PROMPTS["chat"]


def peak_rss_mb():
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024


def run_llm(account_id, token, phase, rows):
    for index in range(1, LLM_REQUESTS + 1):
        outcome = common.timed_request(account_id, token, cfg.PRIMARY_MODEL.model_id, PROMPT)
        rows.append(
            {
                "kind": "llm",
                "phase": phase,
                "index": index,
                "ok": int(outcome["ok"]),
                "seconds": round(outcome["latency"], 3),
                "completion_tokens": outcome["completion_tokens"],
                "truncated": int(outcome["truncated"]),
                "neurons": round(outcome["neurons"], 4),
                "error": outcome["error"],
            }
        )
        print(f"  llm {phase} #{index}: " + (f"{outcome['latency']:.2f}s" if outcome["ok"] else f"LOI {outcome['error']}"))


def yolo_once(model, image):
    started = time.perf_counter()
    model.predict(source=image, imgsz=IMAGE_SIZE, device="cpu", verbose=False)
    return time.perf_counter() - started


def yolo_row(phase, index, seconds):
    return {
        "kind": "yolo",
        "phase": phase,
        "index": index,
        "ok": 1,
        "seconds": round(seconds, 3),
        "completion_tokens": "",
        "truncated": "",
        "neurons": "",
        "error": "",
    }


def describe(rows, kind, phase):
    values = [r["seconds"] for r in rows if r["kind"] == kind and r["phase"] == phase and r["ok"]]
    total = sum(1 for r in rows if r["kind"] == kind and r["phase"] == phase)
    if not values:
        return f"| {kind} | {phase} | 0/{total} | - | - | - |"
    mean = sum(values) / len(values)
    return (
        f"| {kind} | {phase} | {len(values)}/{total} | {common.percentile(values, 50):.2f} | "
        f"{common.percentile(values, 95):.2f} | {mean:.2f} |"
    )


def main():
    account_id, token = common.get_credentials()
    rows = []
    stages = []

    print("Pha 1: LLM chay mot minh")
    run_llm(account_id, token, "alone", rows)
    stages.append(("Sau pha LLM mot minh (chua import ultralytics/torch)", peak_rss_mb()))

    print("Pha 2: nap YOLO va YOLO chay mot minh")
    # Import o day de moc RAM o pha 1 chua gom ultralytics/torch
    started = time.perf_counter()
    import torch
    from ultralytics import YOLO
    from ultralytics.utils import ASSETS

    model = YOLO(WEIGHTS)
    load_seconds = time.perf_counter() - started
    image = str(ASSETS / "bus.jpg")
    first_seconds = yolo_once(model, image)
    stages.append(("Sau khi nap YOLO va suy luan lan dau", peak_rss_mb()))
    for index in range(1, YOLO_RUNS + 1):
        seconds = yolo_once(model, image)
        rows.append(yolo_row("alone", index, seconds))
        print(f"  yolo alone #{index}: {seconds:.3f}s")
    stages.append(("Sau pha YOLO mot minh", peak_rss_mb()))

    print("Pha 3: YOLO chay lien tuc trong khi gui request LLM")
    stop = threading.Event()
    together = []

    def yolo_loop():
        while not stop.is_set():
            together.append(yolo_once(model, image))

    worker = threading.Thread(target=yolo_loop, daemon=True)
    worker.start()
    run_llm(account_id, token, "together", rows)
    stop.set()
    worker.join()
    for index, seconds in enumerate(together, start=1):
        rows.append(yolo_row("together", index, seconds))
    stages.append(("Sau pha YOLO + LLM dong thoi", peak_rss_mb()))

    stamp = f"{datetime.now(timezone.utc):%Y%m%d-%H%M%S}"
    base.RESULTS_DIR.mkdir(exist_ok=True)
    csv_path = base.RESULTS_DIR / f"contention-{stamp}.csv"
    with open(csv_path, "w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)

    neurons = sum(r["neurons"] for r in rows if r["neurons"] != "")
    summary = [
        f"- Phien ban: ultralytics {version('ultralytics')}, torch {version('torch')}, so luong thread torch {torch.get_num_threads()}",
        f"- Model LLM: {cfg.PRIMARY_MODEL.model_id}; model YOLO: yolov8n.pt, anh bus.jpg, imgsz {IMAGE_SIZE}, CPU",
        f"- Import thu vien va nap YOLO: {load_seconds:.2f}s; suy luan YOLO lan dau (da gom khoi tao): {first_seconds:.2f}s",
        "",
        "| Loai | Pha | Thanh cong | p50 (s) | p95 (s) | Trung binh (s) |",
        "|---|---|---|---|---|---|",
        describe(rows, "llm", "alone"),
        describe(rows, "llm", "together"),
        describe(rows, "yolo", "alone"),
        describe(rows, "yolo", "together"),
        "",
        "| Moc do RAM dinh (ru_maxrss) | MB |",
        "|---|---|",
    ] + [f"| {label} | {value:.0f} |" for label, value in stages]
    summary_path = base.RESULTS_DIR / f"contention-{stamp}-summary.md"
    summary_path.write_text("\n".join(summary) + "\n", encoding="utf-8")

    print()
    print("\n".join(summary))
    print(f"\nTong Neurons cua lan chay nay: {neurons:.2f}")
    print(f"Da luu: {csv_path}")
    print(f"Da luu: {summary_path}")


if __name__ == "__main__":
    main()