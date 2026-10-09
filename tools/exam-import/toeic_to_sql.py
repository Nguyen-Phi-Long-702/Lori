"""Chuyen 1 de TOEIC (file dataTest.json cua repo tmd22121999/thi_toeic) thanh file SQL nhap vao Neon.

Cach dung (xem INFRA.md muc 11):
  python toeic_to_sql.py --input thi_toeic/src/test/dataTest.json \
      --audio-base https://<ref>.supabase.co/storage/v1/object/public/lori-audio \
      --title "TOEIC Practice Test 1" --folder toeic-test-1 --out-dir out

Dau ra trong --out-dir:
  <folder>.sql              : file SQL de nap vao Neon
  <folder>_audio_urls.txt   : danh sach URL audio goc de tai ve
"""
import argparse
import json
import os
import re
import sys
from urllib.parse import urlparse

from exam_sql import ExamSqlBuilder, html_to_text

# Ma "type" trong dataTest.json -> so Part cua TOEIC
PART_BY_TYPE = {
    1636615697542: 1,
    1636615720709: 2,
    1636615725762: 3,
    1636615729794: 4,
    1636615733972: 5,
    1636615742506: 6,
    1636615746924: 7,
}
# So cau moi Part (TOEIC chuan): Listening 6+25+39+30 = 100, Reading 30+16+54 = 100
QUESTIONS_PER_PART = {1: 6, 2: 25, 3: 39, 4: 30, 5: 30, 6: 16, 7: 54}
LISTENING_PARTS = {1, 2, 3, 4}
OPTION_COUNT = {1: 4, 2: 3, 3: 4, 4: 4, 5: 4, 6: 4, 7: 4}
LABEL = re.compile(r"^\s*\(([A-D0])\)\s*(.*)$", re.S)


def parse_options(answer, part, number):
    """Tra ve (danh sach lua chon theo thu tu A,B,C..., chu cai dap an dung)."""
    raw = list(answer["choices"]) + list(answer["texts"])
    letters = "ABCD"[:OPTION_COUNT[part]]
    if len(raw) != len(letters) or len(answer["texts"]) != 1:
        raise SystemExit(f"Cau {number}: so lua chon khong dung ({len(raw)})")
    parsed = []
    for item in raw:
        m = LABEL.match(item)
        if not m:
            raise SystemExit(f"Cau {number}: lua chon khong co nhan (A)-(D): {item[:40]}")
        parsed.append([m.group(1), html_to_text(m.group(2))])
    correct = parsed[-1][0]  # phan tu cuoi la answer["texts"][0] = dap an dung
    bad = [p for p in parsed if p[0] not in letters]
    missing = [c for c in letters if c not in [p[0] for p in parsed]]
    if bad:
        # Loi du lieu goc: nhan "(0)" thay vi "(C)". Chi sua khi thieu dung 1 chu cai.
        if len(bad) != 1 or len(missing) != 1 or bad[0] is parsed[-1]:
            raise SystemExit(f"Cau {number}: nhan lua chon sai khong tu sua duoc")
        print(f"Canh bao: cau {number} co nhan '({bad[0][0]})', da doi thanh '({missing[0]})'")
        bad[0][0] = missing[0]
    if sorted(p[0] for p in parsed) != list(letters):
        raise SystemExit(f"Cau {number}: nhan lua chon bi trung hoac thieu")
    parsed.sort(key=lambda p: p[0])
    options = [text if text else label for label, text in parsed]  # Part 1-2 khong in noi dung lua chon
    return options, correct


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--audio-base", required=True, help="URL goc thu muc audio tren Supabase, khong co dau / o cuoi")
    ap.add_argument("--title", default="TOEIC Practice Test 1")
    ap.add_argument("--folder", default="toeic-test-1", help="ten thu muc con trong bucket audio")
    ap.add_argument("--out-dir", default="out")
    args = ap.parse_args()

    with open(args.input, encoding="utf-8") as f:
        items = json.load(f)

    by_part = {p: [] for p in range(1, 8)}
    for item in items:
        part = PART_BY_TYPE.get(item["type"])
        if part is None:
            raise SystemExit(f"Gap type la {item['type']}, khong phai file de TOEIC mong doi")
        by_part[part].append(item)
    for part, expected in QUESTIONS_PER_PART.items():
        got = sum(len(i["childCards"]) if i["hasChild"] else 1 for i in by_part[part])
        if got != expected:
            raise SystemExit(f"Part {part} co {got} cau, can {expected}")

    builder = ExamSqlBuilder("TOEIC", args.title, "MEDIUM", 120)
    audio_urls = []
    base = args.audio_base.rstrip("/")

    def audio_for(item):
        source = item["question"].get("sound")
        if not source:
            raise SystemExit(f"Muc {item['_id']} (Listening) khong co audio")
        audio_urls.append(source)
        return f"{base}/{args.folder}/{os.path.basename(urlparse(source).path)}"

    section_no = 0
    number = 0  # so cau theo chuan TOEIC: 1..200

    for part in range(1, 8):
        part_items = by_part[part]
        part_start = number + 1
        if part == 5:
            section_no += 1
            section = builder.add_section(
                "READING", f"Part 5 - Incomplete Sentences (Q{part_start}-Q{part_start + 29})", section_no)
        for index, item in enumerate(part_items, start=1):
            answer = item["answer"]
            if part in (1, 2, 5):  # cau don
                number += 1
                if part in (1, 2):
                    label = "Photographs" if part == 1 else "Question-Response"
                    section_no += 1
                    section = builder.add_section(
                        "LISTENING", f"Part {part} - {label} (Q{number})", section_no, audio_url=audio_for(item))
                    text = None
                else:
                    text = html_to_text(item["question"]["text"])
                    if not text:
                        raise SystemExit(f"Cau {number}: Part 5 thieu noi dung")
                options, correct = parse_options(answer, part, number)
                builder.add_question(section, number, "MULTIPLE_CHOICE", text, options, correct,
                                     html_to_text(answer["hint"]))
                continue

            # Cau nhom (Part 3, 4, 6, 7): 1 section = 1 doan hoi thoai / bai noi / bai doc
            children = item["childCards"]
            first, last = number + 1, number + len(children)
            parent_text = html_to_text(item["question"]["text"])
            parent_hint = html_to_text(answer["hint"])
            section_no += 1
            if part == 3:
                section = builder.add_section("LISTENING", f"Part 3 - Conversation {index} (Q{first}-{last})",
                                              section_no, audio_url=audio_for(item), passage_text=parent_text)
            elif part == 4:
                section = builder.add_section("LISTENING", f"Part 4 - Talk {index} (Q{first}-{last})",
                                              section_no, audio_url=audio_for(item), passage_text=parent_text)
            elif part == 6:
                section = builder.add_section("READING", f"Part 6 - Text Completion {index} (Q{first}-{last})",
                                              section_no, passage_text=parent_text)
            else:
                section = builder.add_section("READING", f"Part 7 - Reading Comprehension {index} (Q{first}-{last})",
                                              section_no, passage_text=parent_text)
            for child in children:
                number += 1
                child_answer = child["answer"]
                if part == 6:
                    if f"({number})" not in parent_text:
                        raise SystemExit(f"Part 6: khong thay '({number})' trong doan van, thu tu cau bi lech")
                    text = f"Blank ({number})"
                else:
                    text = html_to_text(child["question"]["text"])
                    if not text:
                        raise SystemExit(f"Cau {number}: thieu noi dung cau hoi")
                options, correct = parse_options(child_answer, part, number)
                explanation = html_to_text(child_answer["hint"]) or parent_hint  # Part 3-4: transcript nam o muc cha
                builder.add_question(section, number, "MULTIPLE_CHOICE", text, options, correct, explanation)

    totals = builder.totals()
    if number != 200 or totals != {"LISTENING": 100, "READING": 100}:
        raise SystemExit(f"Tong so cau sai: {number}, {totals}")

    os.makedirs(args.out_dir, exist_ok=True)
    sql_path = os.path.join(args.out_dir, f"{args.folder}.sql")
    urls_path = os.path.join(args.out_dir, f"{args.folder}_audio_urls.txt")
    with open(sql_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(builder.render())
    with open(urls_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(audio_urls) + "\n")
    print(f"OK: {len(builder.sections)} section, 200 cau (Listening 100, Reading 100), {len(audio_urls)} audio")
    print(f"SQL : {sql_path}")
    print(f"Audio URL: {urls_path}")


if __name__ == "__main__":
    sys.exit(main())