"""Xuat ten bai ngu phap va ten chu de tu vung tu dictionary.db ra file JSON cho Backend (LearningContextService)."""
import json
import os
import sqlite3

DB_PATH = "app/src/main/assets/databases/dictionary.db"
OUT_PATH = "backend/src/main/resources/ai/learning-names.json"

with sqlite3.connect(DB_PATH) as db:
    grammar_lessons = {str(row[0]): row[1] for row in db.execute("SELECT id, title FROM grammar_lessons ORDER BY id")}
    vocab_topics = {str(row[0]): row[1] for row in db.execute("SELECT id, name_vi FROM topics ORDER BY id")}

os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
with open(OUT_PATH, "w", encoding="utf-8", newline="\n") as out:
    json.dump({"grammarLessons": grammar_lessons, "vocabTopics": vocab_topics}, out, ensure_ascii=False, indent=2)
    out.write("\n")

print(f"Da ghi {OUT_PATH}: {len(grammar_lessons)} bai ngu phap, {len(vocab_topics)} chu de tu vung")