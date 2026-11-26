"""Tong hop AI Evaluation Report (ke hoach Tuan 17, Ngay 6). Cach chay: xem INFRA.md muc 41.

  python -u eval_report.py sample <run.json>       chon mau case, ghi phieu cham Relevance (khong goi mang)
  python -u eval_report.py consistency <run.json>  chay lai moi case trong mau them 2 lan tren AI Service that
  python -u eval_report.py summarize <run.json>    doc phieu cham va ket qua Consistency, ghi bao cao va cau INSERT (khong goi mang)

<run.json> la ai-service/evaluation/results/evaluation-<ngay>-<gio UTC>.json cua lan chay day du 170 case.
Moi file sinh ra nam o tools/ai-eval-report/results/ va dung chung phan <ngay>-<gio UTC> cua <run.json>.
"""
import json
import math
import os
import random
import re
import statistics
import sys
from difflib import SequenceMatcher
from itertools import combinations
from pathlib import Path

from evaluation.case_checks import CHECKS
from evaluation.case_loader import build_request, load_cases
from evaluation.live_client import BASE_URL_ENV, LiveClient
from guardrails.pii_redactor import redact_value
from guardrails.s2s_auth import SECRET_ENV_NAME
from guardrails.topic_classifier import OFF_TOPIC_REPLY

RESULTS_DIR = Path(__file__).resolve().parent / "results"
# Giong PROVIDER_ERROR_STATUSES trong ai-service/evaluation/conftest.py: loi do AI Service hoac Cloudflare, khong phai dap an sai cua model
PROVIDER_ERROR_STATUSES = (0, 502, 503)
# Case co cau tra loi that cua LLM va doc de cham Relevance; nhom guardrails chi lay case on_topic
RELEVANCE_KINDS = {"grammar", "vocabulary", "quiz", "exam", "recommend", "chat_context", "on_topic"}
SAMPLE_PER_GROUP = 6
SAMPLE_SEED = 20261205
EXTRA_RUNS = 2
FLAG_BELOW_PERCENT = 90
SCORE_LINE = re.compile(r"^- Relevance \(1-5\): *(.*?) *$")
ITEM_HEADING = re.compile(r"^## (\S+)")


def out_path(run_path: Path, prefix: str, suffix: str) -> Path:
    return RESULTS_DIR / f"{prefix}-{run_path.stem.removeprefix('evaluation-')}.{suffix}"


def load_run(run_path: Path) -> tuple[dict, dict]:
    run = json.loads(run_path.read_text(encoding="utf-8"))
    cases = {case["id"]: case for case in load_cases()}
    entries = {}
    for item in run["cases"]:
        case_id = item["test"].rsplit("[", 1)[-1].rstrip("]")
        if case_id not in cases:
            sys.exit(f"Case {case_id} trong file kết quả không có trong dataset: đây không phải file kết quả của Evaluation Dataset")
        entries[case_id] = {
            "case": cases[case_id],
            "outcome": item["outcome"],
            "reason": item["reason"],
            "call": item["calls"][0] if item["calls"] else None,
        }
    if set(entries) != set(cases):
        sys.exit(f"File kết quả có {len(entries)} case, dataset có {len(cases)}: đây chưa phải lần chạy đầy đủ")
    return run, entries


def is_infra_error(entry: dict) -> bool:
    return bool(entry["call"]) and entry["call"]["status"] in PROVIDER_ERROR_STATUSES


def text_of(body: dict) -> str:
    return body.get("answer") or json.dumps(body, ensure_ascii=False, indent=2)


def pick_sample(entries: dict) -> list[str]:
    rng = random.Random(SAMPLE_SEED)
    sample = []
    for group in sorted({entry["case"]["group"] for entry in entries.values()}):
        eligible = sorted(
            case_id
            for case_id, entry in entries.items()
            if entry["case"]["group"] == group
            and entry["case"]["kind"] in RELEVANCE_KINDS
            and entry["call"]
            and entry["call"]["status"] == 200
            and entry["call"]["output"].get("answer") != OFF_TOPIC_REPLY
        )
        sample += sorted(rng.sample(eligible, min(SAMPLE_PER_GROUP, len(eligible))))
    return sample


def describe_input(case: dict) -> str:
    kind = case["kind"]
    if kind in ("grammar", "vocabulary"):
        return case["text"]
    if kind in ("quiz", "exam"):
        return json.dumps(case["payload"], ensure_ascii=False)
    if kind == "recommend":
        return json.dumps(case["weakTopics"], ensure_ascii=False)
    context = case.get("context")
    return case["message"] + (f"\n(Learning Context: {json.dumps(context, ensure_ascii=False)})" if context else "")


def quote(text: str) -> str:
    return "\n".join(f"> {line}" if line else ">" for line in text.splitlines())


def build_sheet(run: dict, entries: dict, sample: list[str]) -> str:
    lines = [
        f"# Phiếu chấm Relevance {run['run_at']}",
        "",
        f"Model `{run['model_version']}`. Mẫu {len(sample)} case (mỗi nhóm tối đa {SAMPLE_PER_GROUP}, chọn ngẫu nhiên cố định, nhóm guardrails chỉ lấy câu hỏi tiếng Anh hợp lệ).",
        "",
        "Điền một số nguyên từ 1 đến 5 vào dòng `- Relevance (1-5): ` dưới mỗi câu trả lời; không sửa các dòng khác.",
        "Relevance đo AI có trả lời đúng trọng tâm câu hỏi hay không, không đo đúng/sai về nội dung (đã đo ở Correctness).",
        "",
        "- 5: đúng trọng tâm, đủ ý cần thiết, không lan man.",
        "- 4: đúng trọng tâm nhưng thiếu hoặc thừa nhẹ.",
        "- 3: đúng một phần, có ý lạc hoặc bỏ sót ý chính.",
        "- 2: phần lớn lạc trọng tâm.",
        "- 1: không liên quan hoặc không dùng được.",
        "",
    ]
    for case_id in sample:
        entry = entries[case_id]
        lines += [
            f"## {case_id} [{entry['case']['group']}]",
            "",
            "Đầu vào:",
            "",
            quote(describe_input(entry["case"])),
            "",
            "Câu trả lời của AI:",
            "",
            quote(text_of(entry["call"]["output"])),
            "",
            "- Relevance (1-5): ",
            "- Ghi chú: ",
            "",
        ]
    return "\n".join(lines)


def parse_scores(sheet_path: Path) -> tuple[dict[str, int], list[str]]:
    scores, problems = {}, []
    item_id = None
    for number, line in enumerate(sheet_path.read_text(encoding="utf-8-sig").splitlines(), 1):
        if line.startswith(">"):
            continue
        heading = ITEM_HEADING.match(line)
        if heading:
            item_id = heading.group(1)
            continue
        match = SCORE_LINE.match(line)
        if match and item_id and match.group(1):
            if match.group(1) in ("1", "2", "3", "4", "5"):
                scores[item_id] = int(match.group(1))
            else:
                problems.append(f"Dòng {number}: \"{match.group(1)}\" không hợp lệ ({item_id}), chỉ nhận số nguyên từ 1 đến 5")
    return scores, problems


def make_sample(case: dict, status: int, body: dict, latency_ms: int) -> dict:
    infra = status in PROVIDER_ERROR_STATUSES
    verdict = None if infra else ("fail" if CHECKS[case["kind"]](case, status, body) else "pass")
    return {"status": status, "latency_ms": latency_ms, "verdict": verdict, "text": text_of(redact_value(body))}


def measure(samples: list[dict]) -> dict | None:
    # Lan loi ha tang khong co noi dung de so sanh nen bi loai; can it nhat 2 lan hop le
    valid = [sample for sample in samples if sample["verdict"] is not None]
    if len(valid) < 2:
        return None
    ratios = [SequenceMatcher(None, a["text"], b["text"], autojunk=False).ratio() for a, b in combinations(valid, 2)]
    return {"runs": len(valid), "verdict_same": len({sample["verdict"] for sample in valid}) == 1, "similarity": statistics.mean(ratios)}


def consistency(run_path: Path) -> None:
    base_url, secret = os.environ.get(BASE_URL_ENV, ""), os.environ.get(SECRET_ENV_NAME, "")
    if not (base_url and secret):
        sys.exit(f"Thiếu biến môi trường {BASE_URL_ENV} hoặc {SECRET_ENV_NAME}")
    run, entries = load_run(run_path)
    client = LiveClient(base_url, secret)
    results = []
    for case_id in pick_sample(entries):
        entry = entries[case_id]
        case, call = entry["case"], entry["call"]
        # Lan 1 la lan trong lan chay day du (da che thong tin nhay cam); verdict lay tu ket qua pytest cua lan do
        samples = [{"status": call["status"], "latency_ms": call["latency_ms"], "verdict": "pass" if entry["outcome"] == "passed" else "fail", "text": text_of(call["output"])}]
        path, payload, _ = build_request(case)
        for _ in range(EXTRA_RUNS):
            response = client.request("POST", path, payload)
            samples.append(make_sample(case, response.status, response.body, response.latency_ms))
        result = {"id": case_id, "group": case["group"], "samples": samples, "measure": measure(samples)}
        results.append(result)
        print(f"{case_id}: " + ", ".join(f"HTTP {s['status']} {s['verdict']}" for s in samples) + (f" | giống {100 * result['measure']['similarity']:.1f}%" if result["measure"] else " | không đo được"))

    data = {"run_at": run["run_at"], "model_version": run["model_version"], "results": results}
    RESULTS_DIR.mkdir(exist_ok=True)
    path_out = out_path(run_path, "consistency", "json")
    path_out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\nĐã lưu: {path_out}")


def percent(part: int, whole: int) -> str:
    return f"{100 * part / whole:.1f}%" if whole else "-"


def percentile(sorted_values: list[int], pct: int) -> int:
    # Nearest-rank, giong tools/perf-test/ai_load_test.py
    return sorted_values[max(math.ceil(pct / 100 * len(sorted_values)), 1) - 1]


def group_names(entries: dict) -> list[str]:
    return sorted({entry["case"]["group"] for entry in entries.values()})


def build_report(run: dict, entries: dict, scores: dict[str, int], consistency_data: dict) -> tuple[str, str]:
    groups = group_names(entries)
    by_group = {group: [e for e in entries.values() if e["case"]["group"] == group] for group in groups}
    infra_total = sum(is_infra_error(e) for e in entries.values())
    lines = [
        f"# Báo cáo AI Evaluation {run['run_at']}",
        "",
        f"Model `{run['model_version']}`. {run['total_cases']} case: đạt {run['pass_count']}, không đạt {run['fail_count']}.",
        "",
        "## 1. Correctness (đạt theo `case_checks.py`)",
        "",
        f"| Nhóm | Tổng | Đạt | Lỗi hạ tầng (HTTP 0/502/503) | Đạt / đã trả lời | Đạt / tổng | Dưới {FLAG_BELOW_PERCENT}% |",
        "|---|---|---|---|---|---|---|",
    ]
    for group, items in by_group.items():
        passed = sum(e["outcome"] == "passed" for e in items)
        infra = sum(is_infra_error(e) for e in items)
        low = "có" if 100 * passed < FLAG_BELOW_PERCENT * len(items) else "không"
        lines.append(f"| {group} | {len(items)} | {passed} | {infra} | {percent(passed, len(items) - infra)} | {percent(passed, len(items))} | {low} |")
    lines += [
        f"| **Tổng** | {len(entries)} | {run['pass_count']} | {infra_total} | {percent(run['pass_count'], len(entries) - infra_total)} | {percent(run['pass_count'], len(entries))} | |",
        "",
        "Request lỗi hạ tầng (HTTP 0/502/503, loại C như mục 32) không có câu trả lời để chấm nên cột \"Đạt / đã trả lời\" loại chúng ra; nguyên nhân từng lỗi (quá 25 giây, Cloudflare báo lỗi hay model trả kết quả bị từ chối) không suy ra được từ file kết quả. Cột \"Đạt / tổng\" tính chúng là không đạt và là cột dùng để đánh dấu nhóm thấp.",
        "",
        "### Case không đạt",
        "",
        "| Case | Nhóm | Loại tự động | Lý do |",
        "|---|---|---|---|",
    ]
    failed = [(case_id, e) for case_id, e in entries.items() if e["outcome"] != "passed"]
    for case_id, e in failed:
        kind = "hạ tầng" if is_infra_error(e) else "nội dung"
        lines.append(f"| {case_id} | {e['case']['group']} | {kind} | {(e['reason'] or '').replace('|', chr(92) + '|')} |")
    if not failed:
        lines.append("| (không có) | | | |")

    lines += ["", "## 2. Relevance (thang 1-5, chấm tay trên mẫu)", "", "| Nhóm | Số case chấm | Trung bình | 1 | 2 | 3 | 4 | 5 |", "|---|---|---|---|---|---|---|---|"]
    for group in groups:
        values = [score for case_id, score in scores.items() if entries[case_id]["case"]["group"] == group]
        if values:
            lines.append(f"| {group} | {len(values)} | {statistics.mean(values):.2f} | " + " | ".join(str(values.count(n)) for n in range(1, 6)) + " |")
    all_scores = list(scores.values())
    relevance_mean = statistics.mean(all_scores)
    lines += [f"| **Tổng** | {len(all_scores)} | {relevance_mean:.2f} | " + " | ".join(str(all_scores.count(n)) for n in range(1, 6)) + " |", ""]

    lines += ["## 3. Safety (nhóm guardrails)", "", "| Loại case | Tổng | Đạt | Tỉ lệ đạt | Ý nghĩa |", "|---|---|---|---|---|"]
    meanings = {
        "blocked": "Lớp 3 (Sanitizer) chặn, HTTP 400",
        "off_topic": "Lớp 4 (Topic classifier) trả câu từ chối cố định",
        "refuse": "Qua LLM nhưng không được trả lời nội dung cấm / lộ SYSTEM_PROMPT",
        "on_topic": "Câu hỏi tiếng Anh hợp lệ không bị chặn nhầm",
    }
    for kind, meaning in meanings.items():
        items = [e for e in by_group.get("guardrails", []) if e["case"]["kind"] == kind]
        passed = sum(e["outcome"] == "passed" for e in items)
        lines.append(f"| {kind} | {len(items)} | {passed} | {percent(passed, len(items))} | {meaning} |")
    lines.append("")

    lines += [
        "## 4. Latency (ms, request đã được AI Service trả lời: HTTP 200 hoặc 400)",
        "",
        "| Nhóm | Số request | Trung bình | p50 | p95 | Lớn nhất |",
        "|---|---|---|---|---|---|",
    ]

    def latency_line(label: str, items: list[dict]) -> str:
        values = sorted(e["call"]["latency_ms"] for e in items if e["call"] and not is_infra_error(e))
        if not values:
            return f"| {label} | 0 | - | - | - | - |"
        return f"| {label} | {len(values)} | {round(statistics.mean(values))} | {percentile(values, 50)} | {percentile(values, 95)} | {values[-1]} |"

    for group, items in by_group.items():
        lines.append(latency_line(group, items))
    lines.append(latency_line("**Tổng**", list(entries.values())))
    infra_latencies = sorted(e["call"]["latency_ms"] for e in entries.values() if is_infra_error(e))
    lines += [
        "",
        f"- `avg_latency_ms` ghi vào `ai_evaluation_result` (gộp mọi request, kể cả lỗi hạ tầng): {run['avg_latency_ms']}.",
        f"- Request lỗi hạ tầng: {infra_total}" + (f", latency trung vị {percentile(infra_latencies, 50)} ms." if infra_latencies else "."),
        "- Latency đo từ máy Dev tới Vercel rồi tới Cloudflare; báo cáo riêng, không gộp với latency CRUD của Backend.",
        "",
    ]

    lines += ["## 5. Consistency (mỗi case trong mẫu chạy 3 lần: lần trong lần chạy đầy đủ + 2 lần chạy lại)", "", "| Nhóm | Đo được / mẫu | Kết luận đạt/không đạt giống nhau cả lần | Độ giống văn bản TB |", "|---|---|---|---|"]
    measures = [(r["id"], r["group"], r["measure"]) for r in consistency_data["results"]]
    for group in groups:
        in_group = [(case_id, m) for case_id, g, m in measures if g == group]
        if not in_group:
            continue
        measured = [m for _, m in in_group if m]
        same = sum(m["verdict_same"] for m in measured)
        similarity = f"{100 * statistics.mean(m['similarity'] for m in measured):.1f}%" if measured else "-"
        lines.append(f"| {group} | {len(measured)}/{len(in_group)} | {same}/{len(measured)} ({percent(same, len(measured))}) | {similarity} |")
    all_measured = [m for _, _, m in measures if m]
    same_total = sum(m["verdict_same"] for m in all_measured)
    similarity_total = 100 * statistics.mean(m["similarity"] for m in all_measured) if all_measured else 0.0
    lines += [
        f"| **Tổng** | {len(all_measured)}/{len(measures)} | {same_total}/{len(all_measured)} ({percent(same_total, len(all_measured))}) | {similarity_total:.1f}% |",
        "",
        "Độ giống văn bản = tỉ lệ giống nhau (`difflib.SequenceMatcher.ratio`) giữa các cặp lần chạy của cùng một case, 100% là giống hệt. Lần chạy lỗi hạ tầng bị loại; case còn dưới 2 lần hợp lệ là \"không đo được\".",
        "",
        "### Case có kết luận thay đổi giữa các lần chạy",
        "",
    ]
    changed = [case_id for case_id, _, m in measures if m and not m["verdict_same"]]
    lines += [f"- {case_id}" for case_id in changed] or ["- (không có)"]
    unmeasured = [case_id for case_id, _, m in measures if not m]
    lines += ["", "### Case không đo được Consistency", ""] + ([f"- {case_id}" for case_id in unmeasured] or ["- (không có)"]) + [""]

    notes = (
        f"{run['notes']}; Relevance TB {relevance_mean:.2f}/5 (chấm {len(all_scores)} case); "
        f"Consistency: kết luận giống nhau {same_total}/{len(all_measured)} case, độ giống văn bản TB {similarity_total:.1f}%"
    )
    sql = (
        "INSERT INTO ai_evaluation_result (run_at, model_version, total_cases, pass_count, fail_count, avg_latency_ms, notes)\n"
        f"VALUES ('{run['run_at']}', '{run['model_version']}', {run['total_cases']}, {run['pass_count']}, "
        f"{run['fail_count']}, {run['avg_latency_ms']}, '{notes.replace(chr(39), chr(39) * 2)}');\n"
    )
    return "\n".join(lines), sql


def sample_cmd(run_path: Path) -> None:
    run, entries = load_run(run_path)
    sample = pick_sample(entries)
    RESULTS_DIR.mkdir(exist_ok=True)
    path_out = out_path(run_path, "relevance", "md")
    path_out.write_text(build_sheet(run, entries, sample), encoding="utf-8")
    print(f"Mẫu {len(sample)} case. Đã lưu phiếu chấm: {path_out}")


def summarize(run_path: Path) -> None:
    run, entries = load_run(run_path)
    sample = pick_sample(entries)
    sheet_path, consistency_path = out_path(run_path, "relevance", "md"), out_path(run_path, "consistency", "json")
    missing = [path.name for path in (sheet_path, consistency_path) if not path.exists()]
    if missing:
        sys.exit(f"Thiếu file: {', '.join(missing)} (chạy lần lượt lệnh sample và consistency trước)")
    scores, problems = parse_scores(sheet_path)
    problems += [f"{case_id}: chưa chấm" for case_id in sample if case_id not in scores]
    problems += [f"{case_id}: không thuộc mẫu" for case_id in scores if case_id not in sample]
    consistency_data = json.loads(consistency_path.read_text(encoding="utf-8"))
    if [result["id"] for result in consistency_data["results"]] != sample:
        problems.append("Kết quả Consistency không khớp mẫu hiện tại (chạy lại lệnh consistency)")
    if problems:
        sys.exit("Chưa tổng hợp được:\n" + "\n".join(f"- {problem}" for problem in problems))
    report, sql = build_report(run, entries, scores, consistency_data)
    out_path(run_path, "report", "md").write_text(report, encoding="utf-8")
    out_path(run_path, "insert", "sql").write_text(sql, encoding="utf-8")
    print(report)
    print(f"Đã lưu: {out_path(run_path, 'report', 'md')}\nĐã lưu: {out_path(run_path, 'insert', 'sql')}")


if __name__ == "__main__":
    commands = {"sample": sample_cmd, "consistency": consistency, "summarize": summarize}
    if len(sys.argv) == 3 and sys.argv[1] in commands:
        commands[sys.argv[1]](Path(sys.argv[2]))
    else:
        sys.exit(__doc__)
