import json
import os
from datetime import datetime, timezone
from pathlib import Path

import pytest

from config.model_config import PRIMARY_MODEL
from guardrails.s2s_auth import SECRET_ENV_NAME
from live_client import BASE_URL_ENV, LiveClient

RESULTS_DIR = Path(__file__).parent / "results"

_live = None
_outcomes = {}


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


def pytest_sessionfinish(session):
    if _live is None or not _outcomes:
        return
    latencies = [call["latency_ms"] for call in _live.calls]
    passed = sum(outcome == "passed" for outcome in _outcomes.values())
    # Cac truong dau trung ten cot cua bang ai_evaluation_result (V5__ai_learning_tables.sql)
    run = {
        "run_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "model_version": PRIMARY_MODEL.model_id,
        "total_cases": len(_outcomes),
        "pass_count": passed,
        "fail_count": len(_outcomes) - passed,
        "avg_latency_ms": round(sum(latencies) / len(latencies)) if latencies else 0,
        "cases": [
            {"test": test, "outcome": outcome, "calls": [c for c in _live.calls if c["test"] == test]}
            for test, outcome in _outcomes.items()
        ],
    }
    RESULTS_DIR.mkdir(exist_ok=True)
    path = RESULTS_DIR / f"evaluation-{datetime.now(timezone.utc):%Y%m%d-%H%M%S}.json"
    path.write_text(json.dumps(run, ensure_ascii=False, indent=2), encoding="utf-8")
    print(
        f"\nEvaluation: {run['pass_count']}/{run['total_cases']} dat, "
        f"avg_latency_ms={run['avg_latency_ms']}, model_version={run['model_version']} -> {path.name}"
    )