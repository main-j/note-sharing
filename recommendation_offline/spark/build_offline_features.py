#!/usr/bin/env python3
"""Build Feast-ready offline feature tables from raw exports."""

import argparse
from pathlib import Path

import pandas as pd
import pyarrow as pa
import pyarrow.parquet as pq


def write_feast_parquet(df: pd.DataFrame, path: Path) -> None:
    """Write parquet with pa.string() columns (Feast 0.47 rejects large_string)."""
    table = pa.Table.from_pandas(df, preserve_index=False)
    columns = []
    for name in table.column_names:
        col = table.column(name)
        if pa.types.is_large_string(col.type):
            columns.append((name, pa.array(col.to_pylist(), type=pa.string())))
        else:
            columns.append((name, col))
    pq.write_table(pa.table(dict(columns)), path)


def build_features(input_dir: Path, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    events = pd.read_parquet(input_dir / "events.parquet")
    stats = pd.read_parquet(input_dir / "note_stats.parquet")

    now = pd.Timestamp.utcnow()
    user_features = (
        events.groupby("user_id", as_index=False)
        .agg(recent_search_count=("keyword", lambda s: s.notna().sum()))
        .assign(top_tag_1="general", top_tag_2="recommend", active_days_7d=1, event_timestamp=now)
    )
    item_features = stats.rename(columns={"note_id": "item_id"}).assign(
        hot_score=lambda x: x["likes"] * 1.0 + x["favorites"] * 1.4 + x["comments"] * 1.8,
        event_timestamp=now,
    )
    author_features = (
        events.dropna(subset=["author_id"])
        .assign(author_id=lambda x: x["author_id"].astype("int64"))
        .groupby("author_id", as_index=False)
        .agg(author_recent_posts=("item_id", "nunique"))
        .assign(author_quality_score=0.5, event_timestamp=now)
    )
    cross_features = (
        events.assign(user_item_key=lambda x: x["user_id"].astype(str) + "_" + x["item_id"].astype(str))
        .groupby("user_item_key", as_index=False)
        .agg(tag_match_score=("tagMatchScore", "max"))
        .assign(is_followee_author=0, event_timestamp=now)
    )

    write_feast_parquet(user_features, output_dir / "user_features.parquet")
    write_feast_parquet(item_features, output_dir / "item_features.parquet")
    write_feast_parquet(author_features, output_dir / "author_features.parquet")
    write_feast_parquet(cross_features, output_dir / "cross_features.parquet")
    print(f"wrote feast feature tables to {output_dir}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", default="data/raw")
    parser.add_argument("--output-dir", default="data/feast")
    args = parser.parse_args()
    build_features(Path(args.input_dir), Path(args.output_dir))
