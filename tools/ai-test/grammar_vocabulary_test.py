"""Kiem thu module Grammar va Vocabulary qua duong that Backend (Render) -> AI Service (Vercel) -> Cloudflare Workers AI
(ke hoach Tuan 12, Ngay 7). Cach chay: xem INFRA.md muc 23.

Moi lan chay: dang ky 1 user moi (Free), kiem tra user Free bi chan 403, mua Premium bang mock-purchase, roi gui lan luot
cac cau mau toi POST /api/ai/analyze-text. Phan tu dong chi kiem tra cau truc va cac dieu da biet chac; chat luong cau
sua, lessonId va giai thich do nguoi doc trong file ket qua results/grammar-vocabulary-<ngay>-<gio UTC>.md.
Gia tri nhay cam lay tu bien moi truong BASE_URL, API_KEY, APP_SIGNATURE, khong ghi trong file.
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).parent
# AiRateLimiter cho 20 request/phut/user; nghi 3 giay giua 2 request de khong cham gioi han
DELAY_SECONDS = 3
TIMEOUT_SECONDS = 60
PASSWORD = "AiTest@12345"
GATE_PROBE = {"type": "grammar", "text": "She don't like coffee."}
BAD_INPUT_MESSAGE = "Invalid input"


def grammar(case_id, text, correct, pattern=None, min_errors=1, ref_lesson=None):
    return {"id": case_id, "type": "grammar", "text": text, "status": 200, "correct": correct,
            "pattern": pattern, "min_errors": min_errors, "ref_lesson": ref_lesson}


def vocabulary(case_id, text, words, in_dict, difference, syn=None, ant=None):
    return {"id": case_id, "type": "vocabulary", "text": text, "status": 200, "words": words,
            "in_dict": in_dict, "difference": difference, "syn": syn, "ant": ant}


def invalid(case_id, case_type, text):
    return {"id": case_id, "type": case_type, "text": text, "status": 400}


# ref_lesson la id bai ngu phap trong learning-names.json ma nguoi soan cho la hop ly nhat, chi de doi chieu bang mat
CASES = [
    grammar("G1", "She don't like coffee in the morning.", False, r"does(n.t| not) like", ref_lesson=1),
    grammar("G2", "Yesterday I go to school.", False, r"went", ref_lesson=3),
    grammar("G3", "I saw a elephant at the zoo.", False, r"an elephant", ref_lesson=7),
    grammar("G4", "She has went to Hanoi twice.", False, r"has gone", ref_lesson=11),
    grammar("G5", "If it will rain tomorrow, we will stay at home.", False, r"if it rains", ref_lesson=19),
    grammar("G6", "The book was write by a famous author.", False, r"was written", ref_lesson=17),
    grammar("G7", "This house is more cheaper than that one.", False, r"^(?!.*more cheaper).*cheaper", ref_lesson=14),
    grammar("G8", "He go to school yesterday and buyed a new book.", False, r"went.*bought", min_errors=2, ref_lesson=3),
    grammar("G9", "She doesn't like coffee in the morning.", True),
    grammar("G10", "I have been studying English for three years.", True),
    vocabulary("V1", "improve", ["improve"], [True], False),
    vocabulary("V2", "affect, effect", ["affect", "effect"], [True, True], True),
    vocabulary("V3", "make vs do", ["make", "do"], [True, True], True),
    vocabulary("V4", "borrow / lend", ["borrow", "lend"], [True, True], True),
    vocabulary("V5", "begin", ["begin"], [True], False, syn="start", ant="end"),
    vocabulary("V6", "look up", ["look up"], [False], False),
    vocabulary("V7", "written", ["written"], [False], False),
    invalid("X1", "grammar", "<b></b>"),
    invalid("X2", "vocabulary", "apple, banana, orange"),
    invalid("X3", "vocabulary", "a" * 41),
]


def call(path, payload, token=None):
    headers = {"Content-Type": "application/json", "X-API-Key": API_KEY, "X-App-Signature": APP_SIGNATURE}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(
        BASE_URL + path, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST")
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    except OSError as error:
        return 0, {"error": f"{type(error).__name__}: {error}"}, time.perf_counter() - started
    elapsed = time.perf_counter() - started
    try:
        body = json.loads(raw.decode("utf-8"))
    except ValueError:
        body = {"raw": raw.decode("utf-8", "replace")[:300]}
    return status, body, elapsed


def check_grammar(case, body):
    problems = []
    if body.get("type") != "grammar_analysis":
        problems.append(f"type = {body.get('type')}")
    if body.get("correct") is not case["correct"]:
        problems.append(f"correct = {body.get('correct')}, ky vong {case['correct']}")
    errors = body.get("errors") or []
    if case["correct"]:
        if errors or body.get("lessonId") is not None:
            problems.append("cau dung nhung errors khong rong hoac lessonId khac null")
    else:
        if len(errors) < case["min_errors"]:
            problems.append(f"chi co {len(errors)} loi, ky vong it nhat {case['min_errors']}")
        if not re.search(case["pattern"], body.get("correctedSentence") or "", re.IGNORECASE):
            problems.append(f"cau sua khong khop mau {case['pattern']}")
    return problems


def check_vocabulary(case, body):
    problems = []
    if body.get("type") != "vocabulary_analysis":
        problems.append(f"type = {body.get('type')}")
    words = body.get("words") or []
    if [item.get("word") for item in words] != case["words"]:
        return problems + [f"words = {[item.get('word') for item in words]}, ky vong {case['words']}"]
    for item, expected in zip(words, case["in_dict"]):
        name = item["word"]
        if item.get("inDictionary") is not expected:
            problems.append(f"{name}: inDictionary = {item.get('inDictionary')}, ky vong {expected}")
        if not (item.get("explanation") or "").strip():
            problems.append(f"{name}: explanation rong")
        if expected and not item.get("definitions"):
            problems.append(f"{name}: co trong tu dien nhung definitions rong")
        if not expected and (item.get("ipa") is not None or item.get("definitions")):
            problems.append(f"{name}: khong co trong tu dien nhung ipa/definitions khong rong")
    difference = body.get("difference")
    if case["difference"] and not (isinstance(difference, str) and difference.strip()):
        problems.append("thieu difference cho cap 2 tu")
    if not case["difference"] and difference is not None:
        problems.append("difference phai la null khi chi hoi 1 tu")
    if case["syn"] and case["syn"] not in (words[0].get("synonyms") or []):
        problems.append(f"synonyms khong co '{case['syn']}' tu tu dien")
    if case["ant"] and case["ant"] not in (words[0].get("antonyms") or []):
        problems.append(f"antonyms khong co '{case['ant']}' tu tu dien")
    return problems


def check(case, status, body):
    if status != case["status"]:
        return [f"HTTP {status}, ky vong {case['status']}"]
    if status == 400:
        return [] if body.get("message") == BAD_INPUT_MESSAGE else [f"message = {body.get('message')}"]
    return check_grammar(case, body) if case["type"] == "grammar" else check_vocabulary(case, body)


def write_report(path, started, setup_lines, results):
    passed = sum(1 for item in results if not item["problems"])
    lines = [
        "# Ket qua kiem thu Grammar va Vocabulary (Tuan 12, Ngay 7)",
        "",
        f"- Bat dau: {started:%Y-%m-%d %H:%M:%S} UTC",
        f"- Backend: {BASE_URL}",
        *[f"- {line}" for line in setup_lines],
        f"- Tu dong: {passed}/{len(results)} dat",
        "",
        "| ID | Loai | HTTP | Thoi gian (s) | Tu dong | lessonId (thuc te / tham khao) |",
        "|---|---|---|---|---|---|",
    ]
    for item in results:
        case, body = item["case"], item["body"]
        lesson = ""
        if case["type"] == "grammar" and item["status"] == 200:
            lesson = f"{body.get('lessonId')} / {case.get('ref_lesson')}"
        lines.append(f"| {case['id']} | {case['type']} | {item['status']} | {item['elapsed']:.1f} "
                     f"| {'Dat' if not item['problems'] else 'KHONG DAT'} | {lesson} |")
    for item in results:
        case = item["case"]
        lines += ["", f"## {case['id']} ({case['type']})", "", f"Dau vao: `{case['text']}`", ""]
        if item["problems"]:
            lines += ["Khong dat:", *[f"- {problem}" for problem in item["problems"]], ""]
        lines += ["```json", json.dumps(item["body"], ensure_ascii=False, indent=2), "```"]
    path.parent.mkdir(exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    started = datetime.now(timezone.utc)
    email = f"grammar.{int(time.time() * 1000)}@example.com"
    setup_lines = [f"User thu: {email} (Free, sau do mua Premium bang mock-purchase)"]

    status, body, _ = call("/api/auth/register", {"displayName": "AI Tester", "email": email, "password": PASSWORD})
    if status != 201:
        sys.exit(f"Dang ky that bai: HTTP {status} {body}")
    token = body["accessToken"]

    status, body, _ = call("/api/ai/analyze-text", GATE_PROBE, token)
    gate_ok = status == 403 and body.get("message") == "PREMIUM_REQUIRED"
    print(f"[{'PASS' if gate_ok else 'FAIL'}] GATE user Free bi chan 403 PREMIUM_REQUIRED (HTTP {status})")
    setup_lines.append(f"Premium gate (user Free): {'Dat' if gate_ok else 'KHONG DAT, HTTP ' + str(status)}")

    status, body, _ = call("/api/payment/mock-purchase", {"planType": "MONTHLY"}, token)
    if status != 201:
        sys.exit(f"mock-purchase that bai: HTTP {status} {body}")

    results = []
    for case in CASES:
        status, body, elapsed = call("/api/ai/analyze-text", {"type": case["type"], "text": case["text"]}, token)
        problems = check(case, status, body)
        results.append({"case": case, "status": status, "body": body, "elapsed": elapsed, "problems": problems})
        print(f"[{'PASS' if not problems else 'FAIL'}] {case['id']} {case['type']} HTTP {status} {elapsed:.1f}s"
              + (f" lessonId={body.get('lessonId')} (tham khao {case['ref_lesson']})"
                 if case["type"] == "grammar" and status == 200 and not case["correct"] else "")
              + "".join(f"\n       - {problem}" for problem in problems))
        time.sleep(DELAY_SECONDS)

    report = HERE / "results" / f"grammar-vocabulary-{started:%Y%m%d-%H%M%S}.md"
    write_report(report, started, setup_lines, results)
    failed = sum(1 for item in results if item["problems"])
    print(f"\nTu dong: {len(results) - failed}/{len(results)} dat. File ket qua: {report}")
    sys.exit(1 if failed or not gate_ok else 0)


BASE_URL = os.environ.get("BASE_URL", "").rstrip("/")
API_KEY = os.environ.get("API_KEY", "")
APP_SIGNATURE = os.environ.get("APP_SIGNATURE", "")

if __name__ == "__main__":
    if not (BASE_URL and API_KEY and APP_SIGNATURE):
        sys.exit("Thieu bien moi truong BASE_URL, API_KEY hoac APP_SIGNATURE")
    main()