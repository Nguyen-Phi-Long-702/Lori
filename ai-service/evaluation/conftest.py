import json
import os
from datetime import datetime, timezone
from pathlib import Path

import pytest

from config.model_config import PRIMARY_MODEL
from guardrails.s2s_auth import SECRET_ENV_NAME
from live_client import BASE_URL_ENV, LiveClient

RESULTS_DIR = Path(__file__).parent / "results"
# Request that bai do AI Service hoac Cloudflare (0 = mat ket noi), khong phai dap an sai cua model
PROVIDER_ERROR_STATUSES = (0, 502, 503)

_live = None
_outcomes = {}
_groups = {}
_reasons = {}


@pytest.fixture(scope="session")
def live():
    global _live
    base_url, secret = os.environ.get(BASE_URL_ENV, ""), os.environ.get(SECRET_ENV_NAME, "")
    if not (base_url and secret):
        pytest.exit(f"Thieu bien moi truong {BASE_URL_ENV} hoac {SECRET_ENV_NAME}", returncode=2)
    _live = LiveClient(base_url, secret)
    return _live


@pytest.fixture(autouse=True)
def _tag_calls_with_test(request, live):
    live.current_test = request.node.nodeid


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    report = (yield).get_result()
    if report.when == "call":
        _outcomes[item.nodeid] = report.outcome
        case = item.funcargs.get("case")
        _groups[item.nodeid] = case["group"] if case else "smoke"
        if call.excinfo:
            # Dong dau cua thong diep assert = cac van de do ham check tra ve
            lines = str(call.excinfo.value).splitlines()
            _reasons[item.nodeid] = lines[0] if lines else call.excinfo.typename


def _average(values):
    return round(sum(values) / len(values)) if values else 0


def _summarize_groups():
    groups = {}
    for test, outcome in _outcomes.items():
        group = groups.setdefault(_groups[test], {"total_cases": 0, "pass_count": 0, "latencies": []})
        group["total_cases"] += 1
        group["pass_count"] += outcome == "passed"
        group["latencies"] += [call["latency_ms"] for call in _live.calls if call["test"] == test]
    return {
        name: {
            "total_cases": group["total_cases"],
            "pass_count": group["pass_count"],
            "avg_latency_ms": _average(group["latencies"]),
        }
        for name, group in groups.items()
    }


def pytest_sessionfinish(session):
    if _live is None or not _outcomes:
        return
    now = datetime.now(timezone.utc)
    passed = sum(outcome == "passed" for outcome in _outcomes.values())
    groups = _summarize_groups()
    provider_errors = sum(call["status"] in PROVIDER_ERROR_STATUSES for call in _live.calls)
    notes = "; ".join(
        f"{name} {group['pass_count']}/{group['total_cases']} (avg {group['avg_latency_ms']} ms)"
        for name, group in groups.items()
    ) + f"; request loi ha tang (HTTP 0/502/503): {provider_errors}"
    # Cac truong dau trung ten cot cua bang ai_evaluation_result (V5__ai_learning_tables.sql)
    run = {
        "run_at": now.isoformat(timespec="seconds"),
        "model_version": PRIMARY_MODEL.model_id,
        "total_cases": len(_outcomes),
        "pass_count": passed,
        "fail_count": len(_outcomes) - passed,
        "avg_latency_ms": _average([call["latency_ms"] for call in _live.calls]),
        "notes": notes,
        "groups": groups,
        "provider_errors": provider_errors,
        "cases": [
            {
                "test": test,
                "group": _groups[test],
                "outcome": outcome,
                "reason": _reasons.get(test),
                "calls": [c for c in _live.calls if c["test"] == test],
            }
            for test, outcome in _outcomes.items()
        ],
    }
    RESULTS_DIR.mkdir(exist_ok=True)
    stem = RESULTS_DIR / f"evaluation-{now:%Y%m%d-%H%M%S}"
    stem.with_suffix(".json").write_text(json.dumps(run, ensure_ascii=False, indent=2), encoding="utf-8")
    sql_notes = notes.replace("'", "''")
    # Cau INSERT de dan vao Neon SQL Editor
    stem.with_suffix(".sql").write_text(
        "INSERT INTO ai_evaluation_result (run_at, model_version, total_cases, pass_count, fail_count, avg_latency_ms, notes)\n"
        f"VALUES ('{run['run_at']}', '{run['model_version']}', {run['total_cases']}, {run['pass_count']}, "
        f"{run['fail_count']}, {run['avg_latency_ms']}, '{sql_notes}');\n",
        encoding="utf-8",
    )
    print(
        f"\nEvaluation: {run['pass_count']}/{run['total_cases']} dat, "
        f"avg_latency_ms={run['avg_latency_ms']}, model_version={run['model_version']} -> {stem.name}.json / .sql\n{notes}"
    )
    for test, reason in _reasons.items():
        print(f"KHONG DAT {test.split('[')[-1].rstrip(']')}: {reason}")