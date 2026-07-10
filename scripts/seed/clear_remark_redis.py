#!/usr/bin/env python3
"""Clear remark-related Redis cache for seed notes."""

import argparse
import re
from pathlib import Path

import pymysql
import redis

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
    }


def clear_for_note_ids(redis_client, note_ids: list[int]) -> int:
    deleted = 0
    for note_id in note_ids:
        list_key = f"note_id_of_remark_list:{note_id}"
        remark_ids = redis_client.lrange(list_key, 0, -1) or []
        deleted += redis_client.delete(list_key)
        for raw_id in remark_ids:
            remark_id = raw_id.decode("utf-8") if isinstance(raw_id, bytes) else str(raw_id)
            remark_id = remark_id.strip().strip('"')
            if not remark_id:
                continue
            for key in (
                f"remark:{remark_id}",
                f"reply_to:{remark_id}",
                f"remark_stats:{remark_id}",
                f"remark_user_like:{remark_id}",
            ):
                deleted += redis_client.delete(key)
    return deleted


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--note-id", type=int, action="append", dest="note_ids")
    parser.add_argument("--seed-only", action="store_true")
    parser.add_argument("--redis-host", default=DEFAULT_REDIS_HOST)
    parser.add_argument("--redis-port", type=int, default=DEFAULT_REDIS_PORT)
    args = parser.parse_args()

    note_ids = args.note_ids or []
    if args.seed_only or not note_ids:
        conn = pymysql.connect(**load_mysql_config())
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT n.id FROM notes n
                JOIN notebooks nb ON n.notebook_id = nb.id
                JOIN note_spaces sp ON nb.space_id = sp.id
                JOIN users u ON sp.user_id = u.id
                WHERE u.email LIKE %s
                """,
                ("%@seed.local",),
            )
            note_ids = [row[0] for row in cur.fetchall()]
        conn.close()

    client = redis.Redis(host=args.redis_host, port=args.redis_port)
    deleted = clear_for_note_ids(client, note_ids)
    print(f"Cleared {deleted} remark cache keys for {len(note_ids)} notes")


if __name__ == "__main__":
    main()
