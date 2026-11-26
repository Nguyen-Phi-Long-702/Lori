import importlib.util
from collections import Counter
from pathlib import Path

from evaluation.case_loader import load_cases

# Kiem tra phan tinh toan cua tools/ai-eval-report khong can mang va khong ton Neurons
TOOL_DIR = Path(__file__).resolve().parents[2] / "tools" / "ai-eval-report"
spec = importlib.util.spec_from_file_location("eval_report", TOOL_DIR / "eval_report.py")
eval_report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(eval_report)


def fake_entries(status_of=lambda case: 200):
    return {
        case["id"]: {
            "case": case,
            "outcome": "passed",
            "reason": None,
            "call": {"status": status_of(case), "latency_ms": 1, "output": {"answer": "ok"}},
        }
        for case in load_cases()
    }


def test_sample_is_fixed_and_covers_text_groups_only():
    entries = fake_entries()
    sample = eval_report.pick_sample(entries)
    assert sample == eval_report.pick_sample(entries)
    counts = Counter(entries[case_id]["case"]["group"] for case_id in sample)
    size = eval_report.SAMPLE_PER_GROUP
    assert counts == {"grammar": size, "vocabulary": size, "quiz_exam": size, "personalization": size, "guardrails": 5}
    assert all(entries[case_id]["case"]["kind"] == "on_topic" for case_id in sample if case_id.startswith("GUARD"))


def test_sample_skips_requests_that_failed_on_infrastructure():
    sample = eval_report.pick_sample(fake_entries(lambda case: 502 if case["group"] == "grammar" else 200))
    assert not any(case_id.startswith("GRAM") for case_id in sample)


def test_parse_scores_accepts_1_to_5_and_reports_invalid_lines(tmp_path):
    sheet = tmp_path / "sheet.md"
    sheet.write_text(
        "## GRAM-01 [grammar]\n> ## GRAM-99 trong cau tra loi\n- Relevance (1-5): 4\n"
        "## GRAM-02 [grammar]\n- Relevance (1-5): 6\n"
        "## GRAM-03 [grammar]\n- Relevance (1-5): \n",
        encoding="utf-8",
    )
    scores, problems = eval_report.parse_scores(sheet)
    assert scores == {"GRAM-01": 4}
    assert len(problems) == 1 and "GRAM-02" in problems[0]


def test_measure_compares_only_valid_runs():
    same = [{"verdict": "pass", "text": "abc"}, {"verdict": "pass", "text": "abc"}, {"verdict": None, "text": "x"}]
    assert eval_report.measure(same) == {"runs": 2, "verdict_same": True, "similarity": 1.0}
    changed = [{"verdict": "pass", "text": "abcd"}, {"verdict": "fail", "text": "abxy"}]
    result = eval_report.measure(changed)
    assert result["verdict_same"] is False and 0 < result["similarity"] < 1
    assert eval_report.measure([{"verdict": "pass", "text": "a"}, {"verdict": None, "text": ""}]) is None
