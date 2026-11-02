"""Kiem thu AI Conversation qua duong that Backend (Render) -> AI Service (Vercel) -> Cloudflare Workers AI
(ke hoach Tuan 14, Ngay 7). Cach chay: xem INFRA.md muc 28.

Moi mode co 1 hoi thoai mau: AI mo dau, hoc vien noi 3 cau (True = cau co loi ngu phap), roi ket thuc de nhan nhan xet.
Phan tu dong chi kiem tra cau truc va cac dieu da biet chac (do dai cau tra loi, "Tip:" cua teacher, giam khao IELTS
co cau hoi va khong cham diem, nhan xet bang tieng Viet, "original" co that trong loi hoc vien, co/khong co loi ngu phap);
chat luong vai dien, ngu phap va giai thich do nguoi doc trong file ket qua results/conversation-<ngay>-<gio UTC>.md.
"""
import json
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import ai_test_common as api

HERE = Path(__file__).parent
PATH = "/api/ai/conversation"
# AiRateLimiter cho 20 request/phut/user; nghi 3 giay giua 2 request de khong cham gioi han
DELAY_SECONDS = 3
MAX_SENTENCES = 3
MAX_FEEDBACK_ITEMS = 3
# Chu cai co dau cua tieng Viet nam trong khoang U+00C0..U+1EF9 (dau cau nhu dau nhay cong thi khong)
VIETNAMESE_LETTER = re.compile(r"[\u00c0-\u1ef9]")

# Moi cau cua hoc vien la (noi dung, co loi ngu phap hay khong)
CASES = [
    {"id": "C1", "mode": "restaurant", "lines": [
        ("Good evening. I want a table for two, near the window.", False),
        ("I would like the grilled chicken and a orange juice.", True),
        ("How much it cost? I want to pay by card.", True)]},
    {"id": "C2", "mode": "airport", "lines": [
        ("Hello. I have a flight to Singapore at ten o'clock.", False),
        ("I has one suitcase and one small bag.", True),
        ("Where is the gate for my flight? I am very hurry.", True)]},
    {"id": "C3", "mode": "interview", "lines": [
        ("My name is Lan. I graduated from university last year.", False),
        ("I have worked in a marketing company for two years. I am good at communicate with customers.", True),
        ("I want this job because I want to learn more and growing with the company.", True)]},
    {"id": "C4", "mode": "teacher", "lines": [
        ("Hello! Yesterday I go to the park with my friends.", True),
        ("We played football and I feeled very happy.", True),
        ("My favorite subject is English because it is interesting.", False)]},
    {"id": "C5", "mode": "ielts_speaking", "lines": [
        ("I live in Ho Chi Minh City. It is a busy and modern city.", False),
        ("I usually go to work by motorbike because it is convenient.", False),
        ("I think young people spend too much time on social media.", False)]},
    {"id": "C6", "mode": "daily", "lines": [
        ("Hi! I usually wake up at six and have breakfast with my family.", False),
        ("Last weekend I visit my grandparents and play with my dog.", True),
        ("Next Sunday we are going to have a picnic by the river.", False)]},
    {"id": "C7", "mode": "partner", "lines": [
        ("Hi! I like watching movies. Do you like movies too?", False),
        ("My favorite movie are about robots and space.", True),
        ("I think robots will do many jobs in the future, so people need to learn new skill.", True)]},
]


def sentence_count(text):
    return max(1, len(re.findall(r"[.!?]+(?:\s|$)", text)))


def normalize(text):
    return " ".join(re.sub(r"[^a-z0-9' ]", " ", text.lower().replace("\u2019", "'")).split())


def check_reply(mode, body, mistake):
    """mistake: cau hoc vien ngay truoc reply nay co loi hay khong (None voi cau mo dau)."""
    reply = body.get("reply") if body.get("type") == "conversation_reply" else None
    if not isinstance(reply, str) or not reply.strip():
        return [f"khong co reply (type = {body.get('type')})"]
    problems = []
    is_tip = reply.strip().lower().startswith("tip:")
    limit = MAX_SENTENCES + 1 if is_tip else MAX_SENTENCES
    if sentence_count(reply) > limit:
        problems.append(f"reply dai {sentence_count(reply)} cau, toi da {limit}")
    if mode == "ielts_speaking":
        if "?" not in reply:
            problems.append("giam khao IELTS khong dat cau hoi")
        if re.search(r"\b(band|score)\b", reply, re.IGNORECASE):
            problems.append("giam khao IELTS nhac toi band/score")
    if mode == "teacher" and mistake is not None and is_tip != mistake:
        problems.append("cau co loi nhung reply khong bat dau bang 'Tip:'" if mistake
                        else "cau dung nhung reply van bat dau bang 'Tip:'")
    return problems


def check_feedback(lines, body):
    if body.get("type") != "conversation_feedback":
        return [f"type = {body.get('type')}"]
    problems = []
    summary = body.get("summary") or ""
    if not summary.strip():
        problems.append("summary rong")
    elif not VIETNAMESE_LETTER.search(summary):
        problems.append("summary khong phai tieng Viet")
    learner = normalize(" ".join(text for text, _ in lines))
    for key in ("grammar", "vocabulary", "naturalness", "usefulPhrases"):
        items = body.get(key)
        if not isinstance(items, list):
            problems.append(f"{key} khong phai danh sach")
            continue
        if len(items) > MAX_FEEDBACK_ITEMS:
            problems.append(f"{key} co {len(items)} muc, toi da {MAX_FEEDBACK_ITEMS}")
        for item in items:
            if key == "usefulPhrases":
                if item.get("original") is not None:
                    problems.append("usefulPhrases co original")
            elif normalize(item.get("original") or "") not in learner:
                problems.append(f"{key}: original khong nam trong loi hoc vien: {item.get('original')}")
    has_mistake = any(mistake for _, mistake in lines)
    grammar = body.get("grammar") if isinstance(body.get("grammar"), list) else []
    if has_mistake and not grammar:
        problems.append("hoc vien co loi ngu phap nhung grammar rong")
    if not has_mistake and grammar:
        problems.append("hoc vien khong co loi ngu phap nhung grammar khong rong")
    return problems


def report_step(case, label, status, elapsed, problems):
    print(f"[{'PASS' if not problems else 'FAIL'}] {case['id']} {case['mode']} {label} HTTP {status} {elapsed:.1f}s"
          + "".join(f"\n       - {problem}" for problem in problems))
    time.sleep(DELAY_SECONDS)


def run_case(case, token):
    """Choi het mot hoi thoai. Moi request gui lai toan bo lich su, giong app."""
    history, transcript, replies, problems = [], [], [], []
    for index, line in enumerate([None] + case["lines"]):
        if line is not None:
            history.append({"role": "user", "content": line[0]})
            transcript.append(("Hoc vien", line[0]))
        status, body, elapsed = api.post_json(
            PATH, {"mode": case["mode"], "messages": history, "finish": False}, token)
        label = "mo dau" if line is None else f"luot {index}"
        step = [f"HTTP {status}, ky vong 200"] if status != 200 else check_reply(
            case["mode"], body, None if line is None else line[1])
        problems += [f"{label}: {problem}" for problem in step]
        replies.append(elapsed)
        report_step(case, label, status, elapsed, step)
        reply = body.get("reply")
        if status != 200 or not isinstance(reply, str) or not reply.strip():
            return {"case": case, "transcript": transcript, "replies": replies, "feedback": None, "problems": problems}
        history.append({"role": "assistant", "content": reply})
        transcript.append((f"AI ({label}, {elapsed:.1f}s)", reply))

    status, body, elapsed = api.post_json(PATH, {"mode": case["mode"], "messages": history, "finish": True}, token)
    step = [f"HTTP {status}, ky vong 200"] if status != 200 else check_feedback(case["lines"], body)
    problems += [f"nhan xet: {problem}" for problem in step]
    report_step(case, "nhan xet", status, elapsed, step)
    return {"case": case, "transcript": transcript, "replies": replies, "problems": problems,
            "feedback": {"status": status, "elapsed": elapsed, "body": body}}


def write_report(path, started, setup_lines, results):
    passed = sum(1 for item in results if not item["problems"])
    lines = [
        "# Ket qua kiem thu AI Conversation (Tuan 14, Ngay 7)",
        "",
        f"- Bat dau: {started:%Y-%m-%d %H:%M:%S} UTC",
        f"- Backend: {api.BASE_URL}",
        *[f"- {line}" for line in setup_lines],
        f"- Tu dong: {passed}/{len(results)} hoi thoai dat",
        "",
        "| ID | Mode | Thoi gian TB moi luot (s) | Thoi gian nhan xet (s) | Tu dong |",
        "|---|---|---|---|---|",
    ]
    for item in results:
        feedback = item["feedback"]
        average = sum(item["replies"]) / len(item["replies"])
        feedback_time = f"{feedback['elapsed']:.1f}" if feedback else "-"
        lines.append(f"| {item['case']['id']} | {item['case']['mode']} | {average:.1f} | {feedback_time} "
                     f"| {'Dat' if not item['problems'] else 'KHONG DAT'} |")
    for item in results:
        lines += ["", f"## {item['case']['id']} ({item['case']['mode']})", ""]
        if item["problems"]:
            lines += ["Khong dat:", *[f"- {problem}" for problem in item["problems"]], ""]
        lines += [f"- {speaker}: {text}" for speaker, text in item["transcript"]]
        if item["feedback"]:
            lines += ["", "```json", json.dumps(item["feedback"]["body"], ensure_ascii=False, indent=2), "```"]
    path.parent.mkdir(exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    started = datetime.now(timezone.utc)
    email, token = api.register_user("convo")
    setup_lines = [f"User thu: {email} (Free, sau do mua Premium bang mock-purchase)"]

    status, body, _ = api.post_json(PATH, {"mode": "restaurant", "messages": [], "finish": False}, token)
    gate_ok = api.is_premium_blocked(status, body)
    print(f"[{'PASS' if gate_ok else 'FAIL'}] GATE user Free bi chan 403 PREMIUM_REQUIRED (HTTP {status})")
    setup_lines.append(f"Premium gate (user Free): {'Dat' if gate_ok else 'KHONG DAT, HTTP ' + str(status)}")

    api.buy_premium(token)

    results = [run_case(case, token) for case in CASES]
    report = HERE / "results" / f"conversation-{started:%Y%m%d-%H%M%S}.md"
    write_report(report, started, setup_lines, results)
    failed = sum(1 for item in results if item["problems"])
    print(f"\nTu dong: {len(results) - failed}/{len(results)} hoi thoai dat. File ket qua: {report}")
    sys.exit(1 if failed or not gate_ok else 0)


if __name__ == "__main__":
    api.require_env()
    main()