import pytest

from case_checks import CHECKS
from case_loader import build_request, load_cases


@pytest.mark.parametrize("case", load_cases(), ids=lambda case: case["id"])
def test_case(live, case):
    path, payload, image = build_request(case)
    response = live.request("POST", path, payload, image=image)
    problems = CHECKS[case["kind"]](case, response.status, response.body)
    assert not problems, "; ".join(problems)