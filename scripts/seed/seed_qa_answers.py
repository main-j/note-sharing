#!/usr/bin/env python3
"""Backfill answers (with nested comments/replies) for seeded QA questions."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import pymysql
from pymongo import MongoClient

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from qa_answer_seeder import seed_answers_for_questions

try:
    from deepseek_client import DeepSeekClient
except ImportError:
    DeepSeekClient = None  # type: ignore[misc, assignment]

MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "ebook_admin",
    "password": "ebook_123456",
    "database": "ebook_platform",
    "charset": "utf8mb4",
}

MONGO_URI = "mongodb://localhost:27017/note_db"


def fetch_seed_user_ids(conn) -> list[int]:
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM users WHERE email LIKE %s ORDER BY id", ("%@seed.local",))
        return [row[0] for row in cur.fetchall()]


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed QA answers with nested comments/replies")
    parser.add_argument("--force", action="store_true", help="Replace answers even if they already exist")
    parser.add_argument("--limit", type=int, default=None, help="Only process first N questions")
    parser.add_argument(
        "--content-source",
        choices=["deepseek", "template"],
        default="deepseek",
        help="Answer content source (default: deepseek)",
    )
    parser.add_argument("--refresh-cache", action="store_true", help="Ignore DeepSeek cache")
    args = parser.parse_args()

    conn = pymysql.connect(**MYSQL_CONFIG)
    mongo = MongoClient(MONGO_URI)
    db = mongo.get_default_database()

    user_ids = fetch_seed_user_ids(conn)
    if not user_ids:
        print("No seed users found. Run seed_cs_demo_data.py first.")
        return

    deepseek = None
    if args.content_source == "deepseek":
        if DeepSeekClient is None:
            raise RuntimeError("deepseek_client unavailable")
        deepseek = DeepSeekClient(refresh_cache=args.refresh_cache)
        print(f"DeepSeek model: {deepseek.model}", flush=True)

    stats = seed_answers_for_questions(
        db,
        user_ids,
        deepseek=deepseek,
        force=args.force,
        limit=args.limit,
    )

    print("\nQA answer seed completed:")
    for key, value in stats.items():
        print(f"  {key}: {value}")


if __name__ == "__main__":
    main()
