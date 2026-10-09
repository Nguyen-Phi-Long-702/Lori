"""Tien ich dung chung cho cac script tao SQL nhap de thi vao Neon.

Script CHI chua code, KHONG chua du lieu de thi. Du lieu de thi nam ngoai repo
(thu muc lam viec tren may Dev) va chi duoc nap vao database bang SQL.

Schema dich: backend/src/main/resources/db/migration/V2__exam_tables.sql
"""
import json
import re
import uuid
from html.parser import HTMLParser

QUESTION_TYPES = {"MULTIPLE_CHOICE", "TRUE_FALSE_NOT_GIVEN", "FILL_BLANK", "MATCHING"}
SKILLS = {"LISTENING", "READING"}
DIFFICULTIES = {"EASY", "MEDIUM", "HARD"}
TFNG_ANSWERS = {"TRUE", "FALSE", "NOT GIVEN"}


class _TextExtractor(HTMLParser):
    """Doi HTML don gian (br, p, h4, table...) thanh van ban thuan, de hien thi bang TextView."""

    BLOCK_END = {"p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "div", "tr"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts = []

    def handle_starttag(self, tag, attrs):
        if tag == "br":
            self.parts.append("\n")

    def handle_endtag(self, tag):
        if tag in self.BLOCK_END:
            self.parts.append("\n")
        elif tag in ("td", "th"):
            self.parts.append(" | ")

    def handle_data(self, data):
        # Theo quy tac HTML: xuong dong/khoang trang lien tiep trong ma nguon chi la 1 dau cach
        self.parts.append(re.sub(r"\s+", " ", data))


def html_to_text(value):
    """HTML -> van ban thuan. Chuoi rong/None -> ''."""
    if not value:
        return ""
    parser = _TextExtractor()
    parser.feed(value)
    parser.close()
    text = "".join(parser.parts).replace("\xa0", " ")
    lines = []
    for line in text.split("\n"):
        line = re.sub(r"[ \t]+", " ", line).strip()
        line = re.sub(r"\s*\|$", "", line).strip()  # bo dau | thua o cuoi dong bang
        lines.append(line)
    text = "\n".join(lines)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def sql_literal(value):
    """Python -> literal SQL (NULL, so nguyen, hoac chuoi trong nhay don)."""
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    text = str(value)
    if "\x00" in text:
        raise ValueError("Chuoi chua ky tu NUL, khong the dua vao SQL")
    return "'" + text.replace("'", "''") + "'"


class ExamSqlBuilder:
    """Gom 1 de (paper -> sections -> questions), kiem tra rang buoc cua V2, roi sinh file SQL."""

    def __init__(self, exam_type, title, difficulty, duration_minutes):
        if exam_type not in ("TOEIC", "IELTS"):
            raise ValueError("exam_type phai la TOEIC hoac IELTS")
        if difficulty not in DIFFICULTIES:
            raise ValueError("difficulty phai la EASY / MEDIUM / HARD")
        if duration_minutes <= 0:
            raise ValueError("duration_minutes phai > 0")
        self.paper = {
            "id": str(uuid.uuid4()),
            "exam_type": exam_type,
            "title": title,
            "difficulty": difficulty,
            "duration_minutes": duration_minutes,
        }
        self.sections = []

    def add_section(self, skill, title, order_index, audio_url=None, passage_text=None):
        if skill not in SKILLS:
            raise ValueError(f"skill khong hop le: {skill}")
        if any(s["order_index"] == order_index for s in self.sections):
            raise ValueError(f"Trung order_index section: {order_index}")
        section = {
            "id": str(uuid.uuid4()),
            "skill": skill,
            "title": title,
            "order_index": order_index,
            "audio_url": audio_url,
            "passage_text": passage_text or None,
            "questions": [],
        }
        self.sections.append(section)
        return section

    def add_question(self, section, order_index, question_type, question_text, options, correct_answer, explanation=None):
        if question_type not in QUESTION_TYPES:
            raise ValueError(f"question_type khong hop le: {question_type}")
        if any(q["order_index"] == order_index for q in section["questions"]):
            raise ValueError(f"Trung order_index cau hoi {order_index} trong section '{section['title']}'")
        correct_answer = (correct_answer or "").strip()
        if not correct_answer or len(correct_answer) > 255:
            raise ValueError(f"correct_answer rong hoac qua 255 ky tu o cau {order_index}")
        if question_type in ("MULTIPLE_CHOICE", "MATCHING"):
            if not options or len(options) < 2:
                raise ValueError(f"Cau {order_index}: thieu options")
            if len(correct_answer) != 1 or not ("A" <= correct_answer <= "Z"):
                raise ValueError(f"Cau {order_index}: correct_answer phai la 1 chu cai, dang la '{correct_answer}'")
            if ord(correct_answer) - 64 > len(options):
                raise ValueError(f"Cau {order_index}: dap an {correct_answer} nam ngoai {len(options)} lua chon")
        elif question_type == "TRUE_FALSE_NOT_GIVEN":
            if options is not None:
                raise ValueError(f"Cau {order_index}: TRUE_FALSE_NOT_GIVEN khong co options")
            if correct_answer not in TFNG_ANSWERS:
                raise ValueError(f"Cau {order_index}: dap an '{correct_answer}' khong thuoc TRUE/FALSE/NOT GIVEN")
        else:  # FILL_BLANK
            if options is not None:
                raise ValueError(f"Cau {order_index}: FILL_BLANK khong co options")
        section["questions"].append({
            "id": str(uuid.uuid4()),
            "order_index": order_index,
            "question_type": question_type,
            "question_text": question_text or None,
            "options": json.dumps(options, ensure_ascii=False) if options is not None else None,
            "correct_answer": correct_answer,
            "explanation": explanation or None,
        })

    def totals(self):
        result = {"LISTENING": 0, "READING": 0}
        for section in self.sections:
            result[section["skill"]] += len(section["questions"])
        return result

    def render(self):
        for section in self.sections:
            if not section["questions"]:
                raise ValueError(f"Section '{section['title']}' khong co cau hoi")
        p = self.paper
        lines = [
            f"-- {p['title']} ({p['exam_type']}) - file SQL tu dong sinh, KHONG commit len repo.",
            "SET client_encoding = 'UTF8';",
            "BEGIN;",
            "",
            "INSERT INTO exam_papers (id, exam_type, title, difficulty, duration_minutes) VALUES",
            f"  ({sql_literal(p['id'])}, {sql_literal(p['exam_type'])}, {sql_literal(p['title'])}, "
            f"{sql_literal(p['difficulty'])}, {p['duration_minutes']});",
            "",
            "INSERT INTO exam_sections (id, paper_id, skill, title, order_index, audio_url, passage_text) VALUES",
        ]
        rows = []
        for s in sorted(self.sections, key=lambda x: x["order_index"]):
            rows.append(
                f"  ({sql_literal(s['id'])}, {sql_literal(p['id'])}, {sql_literal(s['skill'])}, {sql_literal(s['title'])}, "
                f"{s['order_index']}, {sql_literal(s['audio_url'])}, {sql_literal(s['passage_text'])})")
        lines.append(",\n".join(rows) + ";")
        lines.append("")
        lines.append("INSERT INTO exam_questions "
                     "(id, section_id, order_index, question_type, question_text, options, correct_answer, explanation) VALUES")
        rows = []
        for s in sorted(self.sections, key=lambda x: x["order_index"]):
            for q in sorted(s["questions"], key=lambda x: x["order_index"]):
                rows.append(
                    f"  ({sql_literal(q['id'])}, {sql_literal(s['id'])}, {q['order_index']}, "
                    f"{sql_literal(q['question_type'])}, {sql_literal(q['question_text'])}, {sql_literal(q['options'])}, "
                    f"{sql_literal(q['correct_answer'])}, {sql_literal(q['explanation'])})")
        lines.append(",\n".join(rows) + ";")
        lines.append("")
        lines.append("COMMIT;")
        return "\n".join(lines) + "\n"