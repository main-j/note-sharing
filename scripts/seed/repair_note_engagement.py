#!/usr/bin/env python3
"""Rebuild note engagement or remarks for seed data."""

from __future__ import annotations

import argparse
import re
import sys
from datetime import datetime, timedelta
from pathlib import Path

import pymysql
import redis
from pymongo import MongoClient

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from note_engagement import rebuild_all_engagement, rebuild_note_remarks

DEFAULT_REDIS_HOST = "172.24.198.86"
DEFAULT_REDIS_PORT = 6379


def load_mysql_config() -> dict:
    yml = Path(__file__).resolve().parents[2] / "Login_api" / "src" / "main" / "resources" / "application.yml"
    text = yml.read_text(encoding="utf-8")
    host, port, database = re.search(r"url:\s*jdbc:mysql://([^:/]+):(\d+)/([^?]+)", text).groups()
    user = re.search(r"username:\s*(\S+)", text).group(1)
    password = re.search(r"password:\s*(\S+)", text).group(1)
    return {
        "host": host,
        "port": int(port),
        "user": user,
        "password": password,
        "database": database,
        "charset": "utf8mb4",
        "autocommit": False,
    }


def create_deepseek(refresh_cache: bool):
    try:
        from deepseek_client import DeepSeekClient
    except ImportError as exc:
        raise RuntimeError("deepseek_client unavailable") from exc
    return DeepSeekClient(refresh_cache=refresh_cache)


def fix_mongo_datetime_offset(db, hours: int = 8) -> int:
    delta = timedelta(hours=hours)
    updated = 0

    def shift(value):
        if isinstance(value, datetime):
            return value - delta
        return value

    def walk_answer(answer: dict) -> dict:
        answer["createdAt"] = shift(answer.get("createdAt"))
        for comment in answer.get("comments") or []:
            comment["createdAt"] = shift(comment.get("createdAt"))
            for reply in comment.get("replies") or []:
                reply["createdAt"] = shift(reply.get("createdAt"))
        return answer

    for doc in db.questions.find({}):
        created = doc.get("createdAt")
        if not isinstance(created, datetime):
            continue
        answers = [walk_answer(dict(a)) for a in (doc.get("answers") or [])]
        db.questions.update_one(
            {"_id": doc["_id"]},
            {"$set": {"createdAt": shift(created), "answers": answers}},
        )
        updated += 1
    return updated


def main() -> None:
    parser = argparse.ArgumentParser(description="Repair seed note engagement / remarks")
    parser.add_argument("--comments-only", action="store_true", help="Only regenerate remarks and sync comment counts")
    parser.add_argument(
        "--sync-comments-only",
        action="store_true",
        help="Sync note_stats.comments from Mongo remark counts without regenerating remarks",
    )
    parser.add_argument("--template", action="store_true", help="Use template remarks instead of DeepSeek")
    parser.add_argument("--refresh-cache", action="store_true", help="Ignore DeepSeek remark cache")
    parser.add_argument("--skip-redis", action="store_true", help="Do not clear note_stats Redis keys")
    parser.add_argument("--skip-mongo-time-fix", action="store_true", help="Skip QA datetime offset repair")
    parser.add_argument("--limit-notes", type=int, default=None, help="Only process first N notes")
    parser.add_argument("--redis-host", default=DEFAULT_REDIS_HOST)
    parser.add_argument("--redis-port", type=int, default=DEFAULT_REDIS_PORT)
    args = parser.parse_args()

    mysql_cfg = load_mysql_config()
    conn = pymysql.connect(**mysql_cfg)
    mongo = MongoClient("mongodb://localhost:27017/note_db")
    db = mongo.get_default_database()

    redis_client = None
    if not args.skip_redis:
        try:
            redis_client = redis.Redis(host=args.redis_host, port=args.redis_port, decode_responses=False)
            redis_client.ping()
        except Exception as exc:
            print(f"Warning: Redis unavailable ({exc}); skip cache clear.", flush=True)

    deepseek = None
    if not args.template:
        deepseek = create_deepseek(refresh_cache=args.refresh_cache)
        print(f"DeepSeek model: {deepseek.model}", flush=True)

    note_ids = None
    if args.limit_notes is not None:
        with conn.cursor() as cur:
            from note_engagement import load_seed_notes

            note_ids = [n["note_id"] for n in load_seed_notes(cur)[: args.limit_notes]]

    if args.sync_comments_only:
        from note_engagement import sync_all_comment_counts

        print("Syncing note_stats.comments from Mongo remark counts...", flush=True)
        stats = sync_all_comment_counts(conn, db, note_ids=note_ids)
        print(f"  notes={stats['notes']} fixed={stats['fixed']}", flush=True)
    elif args.comments_only:
        print("Regenerating note remarks with DeepSeek and syncing comment counts...", flush=True)
        stats = rebuild_note_remarks(conn, db, redis_client, note_ids=note_ids, deepseek=deepseek)
        print(
            f"  notes={stats['notes']} remarks={stats['remarks']} "
            f"redis_cleared={stats['redis_keys_cleared']}",
            flush=True,
        )
    else:
        print("Rebuilding full note engagement...", flush=True)
        stats = rebuild_all_engagement(conn, db, redis_client, note_ids=note_ids, deepseek=deepseek)
        print(
            f"  notes={stats['notes']} likes={stats['likes']} favorites={stats['favorites']} "
            f"remarks={stats['remarks']} deepseek_calls={stats.get('deepseek_calls', 0)} "
            f"redis_cleared={stats['redis_keys_cleared']}",
            flush=True,
        )

    if not args.skip_mongo_time_fix and not args.comments_only and not args.sync_comments_only:
        fixed = fix_mongo_datetime_offset(db)
        print(f"Adjusted QA createdAt timestamps on {fixed} questions (-8h offset fix).", flush=True)

    conn.close()
    mongo.close()
    print("Done. Run: py -3 scripts/audit/audit_note_user_relations.py", flush=True)


if __name__ == "__main__":
    main()
