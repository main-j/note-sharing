#!/usr/bin/env python3
"""Seed Kafka events and offline parquet for recommendation training."""

from __future__ import annotations

import re
import sys
from pathlib import Path

import pymysql
from pymongo import MongoClient

SCRIPT_DIR = Path(__file__).resolve().parent
SEED_DIR = SCRIPT_DIR.parent / "seed"
if str(SEED_DIR) not in sys.path:
    sys.path.insert(0, str(SEED_DIR))

from seed_recommendation_stores import seed_recommendation_stores  # noqa: E402


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


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    mysql_cfg = load_mysql_config()
    conn = pymysql.connect(**mysql_cfg)
    mongo = MongoClient("mongodb://localhost:27017/note_db")
    db = mongo.get_default_database()

    stats = seed_recommendation_stores(conn, db, repo_root, skip_kafka=False)
    conn.close()
    mongo.close()

    print("Recommendation training data seeded:")
    for key, value in stats.items():
        print(f"  {key}: {value}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
