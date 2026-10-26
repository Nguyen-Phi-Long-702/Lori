import sqlite3
from contextlib import closing

import pytest

from context.dictionary_lookup import DB_PATH, MAX_DEFINITIONS, MAX_RELATED, lookup_word

SCHEMA = """
CREATE TABLE words (id INTEGER PRIMARY KEY, word TEXT NOT NULL, lang_code TEXT NOT NULL);
CREATE TABLE definitions (id INTEGER PRIMARY KEY, definition TEXT NOT NULL, pos TEXT);
CREATE TABLE word_definitions (id INTEGER PRIMARY KEY, word_id INTEGER NOT NULL, definition_id INTEGER NOT NULL, example TEXT);
CREATE TABLE pronunciations (id INTEGER PRIMARY KEY, word_id INTEGER NOT NULL, ipa TEXT NOT NULL, region TEXT);
CREATE TABLE word_relations (id INTEGER PRIMARY KEY, word_id INTEGER NOT NULL, related_word TEXT NOT NULL, relation_type TEXT NOT NULL);
"""


@pytest.fixture
def db_path(tmp_path):
    path = tmp_path / "dictionary.db"
    with closing(sqlite3.connect(path)) as db:
        db.executescript(SCHEMA)
        db.executemany("INSERT INTO words VALUES (?, ?, ?)", [(1, "buy", "en"), (2, "mua", "vi"), (3, "empty", "en")])
        db.executemany(
            "INSERT INTO definitions VALUES (?, ?, ?)", [(i, f"Nghia {i}", "V") for i in range(1, MAX_DEFINITIONS + 3)]
        )
        db.executemany(
            "INSERT INTO word_definitions (word_id, definition_id, example) VALUES (1, ?, ?)",
            [(1, "I buy bread."), (2, ""), (3, None)] + [(i, None) for i in range(4, MAX_DEFINITIONS + 3)],
        )
        db.executemany(
            "INSERT INTO pronunciations (word_id, ipa, region) VALUES (1, ?, ?)", [("/baɪ/", None), ("baɪ", "US")]
        )
        db.executemany(
            "INSERT INTO word_relations (word_id, related_word, relation_type) VALUES (1, ?, ?)",
            [("purchase", "s"), ("sell", "a"), ("get", "s")] + [(f"extra{i}", "s") for i in range(MAX_RELATED)],
        )
        db.commit()
    return path


def test_returns_first_pronunciation_and_definitions_in_order(db_path):
    entry = lookup_word("buy", db_path)
    assert entry["ipa"] == "/baɪ/"
    assert entry["definitions"][0] == {"pos": "V", "meaning": "Nghia 1", "example": "I buy bread."}


def test_empty_example_becomes_none(db_path):
    assert [d["example"] for d in lookup_word("buy", db_path)["definitions"][1:3]] == [None, None]


def test_definitions_are_limited(db_path):
    assert len(lookup_word("buy", db_path)["definitions"]) == MAX_DEFINITIONS


def test_synonyms_and_antonyms_are_split_by_relation_type_and_limited(db_path):
    entry = lookup_word("buy", db_path)
    assert entry["antonyms"] == ["sell"]
    assert entry["synonyms"][:2] == ["purchase", "get"]
    assert len(entry["synonyms"]) == MAX_RELATED


def test_word_without_data_has_empty_fields(db_path):
    assert lookup_word("empty", db_path) == {"ipa": None, "definitions": [], "synonyms": [], "antonyms": []}


@pytest.mark.parametrize("term", ["unknown", "mua", "BUY"])
def test_unknown_word_or_non_english_word_or_other_case_returns_none(db_path, term):
    assert lookup_word(term, db_path) is None


def test_bundled_dictionary_has_common_words():
    entry = lookup_word("affect", DB_PATH)
    assert entry["ipa"] and entry["definitions"]