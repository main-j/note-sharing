#!/usr/bin/env python3
"""Export recommendation-relevant tables from MySQL into parquet."""

import argparse
from pathlib import Path

import pandas as pd
from sqlalchemy import create_engine


def export(mysql_url: str, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    engine = create_engine(mysql_url)
    tables = {
        "note_stats.parquet": """
            SELECT note_id, views, likes, favorites, comments, 0 AS answers
            FROM note_stats
        """,
        "user_follow.parquet": """
            SELECT follower_id, followee_id, created_at
            FROM user_follow
        """,
        "notes.parquet": """
            SELECT id AS note_id, title, updated_at
            FROM notes
        """,
    }
    for filename, sql in tables.items():
        df = pd.read_sql(sql, engine)
        target = output_dir / filename
        df.to_parquet(target, index=False)
        print(f"exported {target}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--mysql-url", required=True, help="e.g. mysql+pymysql://user:pwd@localhost:3306/ebook_platform")
    parser.add_argument("--output-dir", default="data/raw")
    args = parser.parse_args()
    export(args.mysql_url, Path(args.output_dir))
