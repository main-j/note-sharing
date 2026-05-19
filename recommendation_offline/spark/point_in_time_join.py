#!/usr/bin/env python3
"""Simple point-in-time join helper for user/item features and events."""

import argparse
from pathlib import Path

import pandas as pd


def point_in_time_join(events_path: Path, user_feat: Path, item_feat: Path, output: Path) -> None:
    events = pd.read_parquet(events_path)
    users = pd.read_parquet(user_feat)
    items = pd.read_parquet(item_feat)

    merged = events.merge(users, on="user_id", how="left").merge(items, on="item_id", how="left")
    output.parent.mkdir(parents=True, exist_ok=True)
    merged.to_parquet(output, index=False)
    print(f"wrote joined dataset to {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--events", default="data/raw/events.parquet")
    parser.add_argument("--user-features", default="data/feast/user_features.parquet")
    parser.add_argument("--item-features", default="data/feast/item_features.parquet")
    parser.add_argument("--output", default="data/joined/training_joined.parquet")
    args = parser.parse_args()
    point_in_time_join(Path(args.events), Path(args.user_features), Path(args.item_features), Path(args.output))
