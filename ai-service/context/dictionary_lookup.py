import sqlite3
from contextlib import closing
from pathlib import Path

# Ban sao cua dictionary.db tren Android, chi doc
DB_PATH = Path(__file__).resolve().parent.parent / "data" / "dictionary.db"
MAX_DEFINITIONS = 5
MAX_RELATED = 8


def lookup_word(term: str, db_path: Path = DB_PATH) -> dict | None:
    """Tra 1 tu tieng Anh trong dictionary.db cua Lori (cung du lieu voi man Tu dien tren Android).

    term da qua sanitize_input va viet thuong. Khong co trong tu dien thi tra None.
    """
    # immutable=1: he thong file cua Vercel chi doc, SQLite khong can tao file khoa
    with closing(sqlite3.connect(f"{db_path.as_uri()}?mode=ro&immutable=1", uri=True)) as db:
        row = db.execute("SELECT id FROM words WHERE lang_code = 'en' AND word = ?", (term,)).fetchone()
        if row is None:
            return None
        word_id = row[0]
        # Lay phat am dau tien, giong DictionaryViewModel.loadWordDetail tren Android
        pronunciation = db.execute(
            "SELECT ipa FROM pronunciations WHERE word_id = ? ORDER BY id LIMIT 1", (word_id,)
        ).fetchone()
        definitions = db.execute(
            "SELECT d.pos, d.definition, wd.example FROM word_definitions wd "
            "JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = ? ORDER BY wd.id LIMIT ?",
            (word_id, MAX_DEFINITIONS),
        ).fetchall()
        relations = db.execute(
            "SELECT related_word, relation_type FROM word_relations WHERE word_id = ? ORDER BY id", (word_id,)
        ).fetchall()
    return {
        "ipa": pronunciation[0] if pronunciation else None,
        "definitions": [
            {"pos": pos, "meaning": meaning, "example": example or None} for pos, meaning, example in definitions
        ],
        # relation_type: 's' = dong nghia, 'a' = trai nghia (khop DictionaryDao tren Android)
        "synonyms": [word for word, kind in relations if kind == "s"][:MAX_RELATED],
        "antonyms": [word for word, kind in relations if kind == "a"][:MAX_RELATED],
    }