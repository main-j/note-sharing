#!/usr/bin/env python3
"""Build Feast-ready offline feature tables from raw exports."""

import argparse
from pathlib import Path

import pandas as pd


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
        events.groupby("author_id", as_index=False)
        .agg(author_recent_posts=("item_id", "nunique"))
        .assign(author_quality_score=0.5, event_timestamp=now)
    )
    cross_features = (
        events.assign(user_item_key=lambda x: x["user_id"].astype(str) + "_" + x["item_id"].astype(str))
        .groupby("user_item_key", as_index=False)
        .agg(tag_match_score=("tagMatchScore", "max"))
        .assign(is_followee_author=0, event_timestamp=now)
    )

    user_features.to_parquet(output_dir / "user_features.parquet", index=False)
    item_features.to_parquet(output_dir / "item_features.parquet", index=False)
    author_features.to_parquet(output_dir / "author_features.parquet", index=False)
    cross_features.to_parquet(output_dir / "cross_features.parquet", index=False)
    print(f"wrote feast feature tables to {output_dir}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", default="data/raw")
    parser.add_argument("--output-dir", default="data/feast")
    args = parser.parse_args()
    build_features(Path(args.input_dir), Path(args.output_dir))
