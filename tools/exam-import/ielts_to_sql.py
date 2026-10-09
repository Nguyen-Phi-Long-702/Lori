"""Chuyen 1 de IELTS Academic (Listening + Reading) cua repo LuchoBazz/ielts-ai-dataset (CC BY 4.0) thanh file SQL.

Cach dung (xem INFRA.md muc 11):
  python ielts_to_sql.py \
      --listening ielts-ai-dataset/datasets/practice-drills/listening/ielts-listening-band-8.0-test-001/manifest.json \
      --reading ielts-ai-dataset/datasets/practice-drills/reading/ielts-reading-academic-band-8.0-test-001.json \
      --audio-base https://<ref>.supabase.co/storage/v1/object/public/lori-audio \
      --title "IELTS Academic Practice Test 1 (Band 8)" --folder ielts-test-1 --out-dir out

Quy uoc chuyen sang 4 question_type cua DB:
  true-false-not-given                      -> TRUE_FALSE_NOT_GIVEN
  yes-no-not-given                          -> MULTIPLE_CHOICE (options YES / NO / NOT GIVEN)
  multiple-choice                           -> MULTIPLE_CHOICE
  matching-headings / -information / -sentence-endings -> MATCHING (dap an la chu cai A, B, ...)
  sentence-/table-/summary-completion, short-answer -> FILL_BLANK
"""
import argparse
import json
import os
import re
import sys

from exam_sql import ExamSqlBuilder

OPTION_PREFIX = re.compile(r"^[A-Z]\.\s*")
ROMAN_PREFIX = re.compile(r"^([ivxlc]+)\.\s*", re.I)
GAP = re.compile(r"\{\{gap_[^}]*?_(\d+)\}\}")
BLANK = "________"


def minutes(value):
    m = re.match(r"\s*(\d+)\s*min", str(value))
    if not m:
        raise SystemExit(f"Khong doc duoc thoi luong: {value!r}")
    return int(m.group(1))


def strip_prefix(options, pattern=OPTION_PREFIX):
    return [pattern.sub("", o["text"] if isinstance(o, dict) else o).strip() for o in options]


def with_instruction(group, text):
    return f"{group['instructions'].strip()}\n\n{text.strip()}"


def convert_group(builder, section, group):
    """Them tat ca cau hoi cua 1 nhom (question_group) vao section."""
    kind = group["question_type"]
    questions = group["questions"]

    # Dung chung cho ca nhom
    word_bank = group.get("word_bank") or []
    heading_letters = {}
    if kind == "matching-headings":
        for index, heading in enumerate(word_bank):
            m = ROMAN_PREFIX.match(heading)
            if not m:
                raise SystemExit(f"Heading khong co so La Ma: {heading}")
            heading_letters[m.group(1).lower()] = chr(65 + index)
    summary_numbers = [q["question_order"] for q in questions]
    summary_text = questions[0]["text"] if kind == "summary-completion" else None

    for q in questions:
        order = q["question_order"]
        answer = (q.get("answer") or "").strip()
        text = (q.get("text") or "").strip()
        if kind == "true-false-not-given":
            builder.add_question(section, order, "TRUE_FALSE_NOT_GIVEN", text, None, answer.upper())
        elif kind == "yes-no-not-given":
            letter = {"YES": "A", "NO": "B", "NOT GIVEN": "C"}.get(answer.upper())
            if not letter:
                raise SystemExit(f"Cau {order}: dap an Y/N/NG la '{answer}'")
            builder.add_question(section, order, "MULTIPLE_CHOICE", text, ["YES", "NO", "NOT GIVEN"], letter)
        elif kind == "multiple-choice":
            builder.add_question(section, order, "MULTIPLE_CHOICE", text, strip_prefix(q["options"]), answer.upper())
        elif kind == "matching-headings":
            letter = heading_letters.get(answer.lower())
            if not letter:
                raise SystemExit(f"Cau {order}: dap an heading '{answer}' khong co trong danh sach")
            builder.add_question(section, order, "MATCHING", with_instruction(group, text),
                                 strip_prefix(word_bank, ROMAN_PREFIX), letter)
        elif kind == "matching-information":
            letters = [p["right"] for p in q["matching_pairs"]]
            builder.add_question(section, order, "MATCHING", with_instruction(group, text),
                                 [f"Paragraph {x}" for x in letters], answer.upper())
        elif kind == "matching-sentence-endings":
            builder.add_question(section, order, "MATCHING", with_instruction(group, text),
                                 strip_prefix(q["options"]), answer.upper())
        elif kind == "short-answer" or kind == "sentence-completion":
            builder.add_question(section, order, "FILL_BLANK", with_instruction(group, text), None, answer)
        elif kind == "table-completion":
            filled = [g for g in q["completion_gaps"] if g["answer"]]
            if len(filled) != 1:
                raise SystemExit(f"Cau {order}: hang bang phai co dung 1 o can dien, dang co {len(filled)}")
            cells = [BLANK if g is filled[0] else g["gap_text"] for g in q["completion_gaps"]]
            row = f"{text}: " + " | ".join(cells)
            builder.add_question(section, order, "FILL_BLANK", with_instruction(group, row), None, filled[0]["answer"])
        elif kind == "summary-completion":
            numbered = GAP.sub(lambda m: f"({summary_numbers[int(m.group(1))]}) {BLANK}", summary_text)
            if len(GAP.findall(summary_text)) != len(questions):
                raise SystemExit(f"Cau {order}: so cho trong khong khop so cau")
            body = f"Word box: {', '.join(word_bank)}\n\n{numbered}\n\nFill in blank ({order})."
            builder.add_question(section, order, "FILL_BLANK", with_instruction(group, body), None, answer)
        else:
            raise SystemExit(f"Dang cau hoi chua ho tro: {kind}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--listening", required=True)
    ap.add_argument("--reading", required=True)
    ap.add_argument("--audio-base", required=True)
    ap.add_argument("--title", default="IELTS Academic Practice Test 1 (Band 8)")
    ap.add_argument("--folder", default="ielts-test-1")
    ap.add_argument("--out-dir", default="out")
    args = ap.parse_args()

    with open(args.listening, encoding="utf-8") as f:
        listening = json.load(f)
    with open(args.reading, encoding="utf-8") as f:
        reading = json.load(f)

    duration = minutes(listening["duration"]) + minutes(reading["duration"])
    builder = ExamSqlBuilder("IELTS", args.title, "HARD", duration)
    base = args.audio_base.rstrip("/")
    order = 0

    for sec in sorted(listening["sections"], key=lambda s: int(s["section_number"])):
        order += 1
        number = int(sec["section_number"])
        section = builder.add_section(
            "LISTENING", f"Listening - Section {number}: {sec['title']}", order,
            audio_url=f"{base}/{args.folder}/section-{number}.mp3")
        for group in sorted(sec["question_groups"], key=lambda g: g["group_order"]):
            convert_group(builder, section, group)

    for passage in sorted(reading["passages"], key=lambda p: p["passage_number"]):
        order += 1
        section = builder.add_section(
            "READING", f"Reading - Passage {passage['passage_number']}: {passage['title']}", order,
            passage_text=passage["content"].strip())
        for group in sorted(passage["question_groups"], key=lambda g: g["group_order"]):
            convert_group(builder, section, group)

    totals = builder.totals()
    if totals != {"LISTENING": 40, "READING": 40}:
        raise SystemExit(f"Moi ky nang phai co 40 cau, dang la {totals}")

    os.makedirs(args.out_dir, exist_ok=True)
    sql_path = os.path.join(args.out_dir, f"{args.folder}.sql")
    with open(sql_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(builder.render())
    print(f"OK: {len(builder.sections)} section, 80 cau (Listening 40, Reading 40), thoi luong {duration} phut")
    print(f"SQL : {sql_path}")


if __name__ == "__main__":
    sys.exit(main())