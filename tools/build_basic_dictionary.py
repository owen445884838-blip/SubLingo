#!/usr/bin/env python3
"""Build SubLingo's bundled 45k-word SQLite dictionary from MIT-licensed ECDICT CSV."""

import argparse
import csv
import gzip
import json
import re
import shutil
import sqlite3
from pathlib import Path

WORD = re.compile(r"^[a-z][a-z'-]*$")
TARGET_COUNT = 45_000


def integer(value: str, default: int = 0) -> int:
    try:
        return int(value or default)
    except ValueError:
        return default


def clean_text(value: str, limit: int) -> str:
    lines = []
    for raw in (value or "").replace("\\n", "\n").splitlines():
        text = raw.strip()
        if not text or text.startswith("[网络]") or text.startswith("[网络短语]"):
            continue
        lines.append(text)
    compact = "；".join(lines)
    compact = re.sub(r"\s+", " ", compact).strip("； ")
    return compact[:limit].rstrip("； ,，")


def score(row: dict[str, str]) -> tuple[int, int, int, str]:
    collins = integer(row.get("collins", ""))
    oxford = 1 if row.get("oxford", "").strip() else 0
    tags = set(row.get("tag", "").lower().split())
    tag_score = sum(
        weight
        for tag, weight in {
            "zk": 90_000,
            "gk": 85_000,
            "cet4": 80_000,
            "cet6": 75_000,
            "ky": 65_000,
            "gre": 50_000,
            "toefl": 45_000,
            "ielts": 45_000,
        }.items()
        if tag in tags
    )
    bnc = integer(row.get("bnc", ""), 999_999)
    frq = integer(row.get("frq", ""), 999_999)
    frequency_score = max(0, 120_000 - min(bnc, 120_000)) + max(0, 100_000 - min(frq, 100_000))
    rank = oxford * 1_000_000 + collins * 150_000 + tag_score + frequency_score
    phonetic_bonus = 10_000 if row.get("phonetic", "").strip() else 0
    return rank + phonetic_bonus, -min(bnc, frq), -len(row["word"]), row["word"]


def load_candidates(source: Path) -> list[dict[str, str]]:
    candidates: dict[str, dict[str, str]] = {}
    with source.open("r", encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            word = (row.get("word") or "").strip().lower()
            translation = clean_text(row.get("translation", ""), 320)
            phonetic = (row.get("phonetic") or "").strip()
            if not WORD.fullmatch(word) or not translation or not phonetic:
                continue
            row["word"] = word
            row["translation"] = translation
            previous = candidates.get(word)
            if previous is None or score(row) > score(previous):
                candidates[word] = row
    return sorted(candidates.values(), key=score, reverse=True)[:TARGET_COUNT]


def build_database(rows: list[dict[str, str]], database: Path) -> None:
    database.parent.mkdir(parents=True, exist_ok=True)
    database.unlink(missing_ok=True)
    connection = sqlite3.connect(database)
    try:
        connection.executescript(
            """
            PRAGMA journal_mode=OFF;
            PRAGMA synchronous=OFF;
            PRAGMA page_size=4096;
            CREATE TABLE entries (
                word TEXT PRIMARY KEY COLLATE NOCASE,
                phonetic TEXT,
                pos TEXT,
                definition_en TEXT,
                definition_zh TEXT NOT NULL
            ) WITHOUT ROWID;
            CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID;
            """
        )
        connection.executemany(
            "INSERT INTO entries(word, phonetic, pos, definition_en, definition_zh) VALUES (?, ?, ?, ?, ?)",
            [
                (
                    row["word"],
                    (row.get("phonetic") or "").strip() or None,
                    clean_text(row.get("pos", ""), 80) or None,
                    clean_text(row.get("definition", ""), 320) or None,
                    row["translation"],
                )
                for row in rows
            ],
        )
        metadata = {
            "source": "ECDICT",
            "license": "MIT",
            "entry_count": str(len(rows)),
            "format_version": "3",
        }
        connection.executemany("INSERT INTO metadata(key, value) VALUES (?, ?)", metadata.items())
        connection.commit()
        connection.execute("VACUUM")
    finally:
        connection.close()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    rows = load_candidates(args.source)
    if len(rows) != TARGET_COUNT:
        raise SystemExit(f"Expected {TARGET_COUNT} entries, found {len(rows)}")
    database = args.output.with_suffix("") if args.output.suffix == ".gz" else args.output
    build_database(rows, database)
    gz_path = args.output if args.output.suffix == ".gz" else Path(str(args.output) + ".gz")
    with database.open("rb") as source, gz_path.open("wb") as raw_target:
        with gzip.GzipFile(filename="basic_en_zh.sqlite", mode="wb", fileobj=raw_target, compresslevel=9, mtime=0) as target:
            shutil.copyfileobj(source, target, length=1024 * 1024)
    report = {
        "entries": len(rows),
        "sqlite_bytes": database.stat().st_size,
        "gzip_bytes": gz_path.stat().st_size,
        "first": rows[0]["word"],
        "last": rows[-1]["word"],
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
