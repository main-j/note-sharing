#!/usr/bin/env python3
"""Bulk index seeded notes and questions into Elasticsearch for search, hot list, and recommend."""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

import pymysql
from pymongo import MongoClient

MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "ebook_admin",
    "password": "ebook_123456",
    "database": "ebook_platform",
    "charset": "utf8mb4",
}

MONGO_URI = "mongodb://localhost:27017/note_db"
ES_BASE = "http://localhost:9200"


def es_request(method: str, path: str, body: dict | None = None) -> dict[str, Any]:
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(f"{ES_BASE}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Elasticsearch {method} {path} failed ({exc.code}): {detail[:500]}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(
            f"Cannot reach Elasticsearch at {ES_BASE}. Start it via scripts/backend-deps/start-backend-deps.ps1"
        ) from exc


def ensure_indices() -> None:
    for index, props in (
        ("notes", {"id": {"type": "long"}, "title": {"type": "text"}, "contentSummary": {"type": "text"}}),
        (
            "questions",
            {
                "questionId": {"type": "keyword"},
                "title": {"type": "text"},
                "content": {"type": "text"},
                "tags": {"type": "text"},
            },
        ),
    ):
        try:
            es_request("HEAD", f"/{index}")
        except RuntimeError:
            es_request(
                "PUT",
                f"/{index}",
                {"mappings": {"properties": props}},
            )


def load_seed_notes(conn) -> list[dict[str, Any]]:
    sql = """
        SELECT n.id AS note_id, n.title, t.name AS tag_name
        FROM notes n
        JOIN notebooks nb ON n.notebook_id = nb.id
        JOIN note_spaces sp ON nb.space_id = sp.id
        JOIN tags t ON sp.tag_id = t.id
        JOIN users u ON sp.user_id = u.id
        WHERE u.email LIKE %s
        ORDER BY n.id
    """
    with conn.cursor(pymysql.cursors.DictCursor) as cur:
        cur.execute(sql, ("%@seed.local",))
        return list(cur.fetchall())


def load_seed_questions(mongo_db) -> list[dict[str, Any]]:
    return list(
        mongo_db.questions.find(
            {"tags": "seed"},
            {"questionId": 1, "title": 1, "content": 1, "tags": 1, "_id": 0},
        )
    )


def bulk_index(index: str, lines: list[str]) -> int:
    if not lines:
        return 0
    payload = "\n".join(lines) + "\n"
    req = urllib.request.Request(
        f"{ES_BASE}/_bulk",
        data=payload.encode("utf-8"),
        headers={"Content-Type": "application/x-ndjson"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            result = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Bulk index failed: {detail[:500]}") from exc

    if result.get("errors"):
        first_error = next(
            (item.get("index", {}).get("error") for item in result.get("items", []) if item.get("index", {}).get("error")),
            None,
        )
        raise RuntimeError(f"Bulk index reported errors: {first_error}")
    return len(lines) // 2


def index_notes(notes: list[dict[str, Any]]) -> int:
    lines: list[str] = []
    for note in notes:
        tag = note.get("tag_name") or ""
        title = note.get("title") or ""
        summary = " ".join(part for part in (tag, title) if part).strip() or title
        doc = {"id": note["note_id"], "title": title, "contentSummary": summary}
        lines.append(json.dumps({"index": {"_index": "notes", "_id": str(note["note_id"])}}, ensure_ascii=False))
        lines.append(json.dumps(doc, ensure_ascii=False))
    return bulk_index("notes", lines)


def index_questions(questions: list[dict[str, Any]]) -> int:
    lines: list[str] = []
    for question in questions:
        tags = question.get("tags") or []
        tag_text = ", ".join(str(t) for t in tags)
        doc = {
            "questionId": question["questionId"],
            "title": question.get("title") or "",
            "content": question.get("content") or "",
            "tags": tag_text,
        }
        lines.append(
            json.dumps({"index": {"_index": "questions", "_id": question["questionId"]}}, ensure_ascii=False)
        )
        lines.append(json.dumps(doc, ensure_ascii=False))
    return bulk_index("questions", lines)


def main() -> None:
    parser = argparse.ArgumentParser(description="Index seed notes/questions into Elasticsearch")
    parser.add_argument("--recreate-indices", action="store_true", help="Delete and recreate ES indices first")
    args = parser.parse_args()

    if args.recreate_indices:
        for index in ("notes", "questions"):
            try:
                es_request("DELETE", f"/{index}")
            except RuntimeError:
                pass

    ensure_indices()

    conn = pymysql.connect(**MYSQL_CONFIG)
    mongo = MongoClient(MONGO_URI)
    try:
        notes = load_seed_notes(conn)
        questions = load_seed_questions(mongo.get_default_database())
        if not notes:
            print("No seed notes found.")
            return

        note_count = index_notes(notes)
        question_count = index_questions(questions)
        print(f"Indexed notes: {note_count}")
        print(f"Indexed questions: {question_count}")
        print(f"ES notes count: {es_request('GET', '/notes/_count').get('count')}")
        print(f"ES questions count: {es_request('GET', '/questions/_count').get('count')}")
    finally:
        conn.close()
        mongo.close()


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as exc:
        print(exc, file=sys.stderr)
        sys.exit(1)
