#!/usr/bin/env python3
"""Apply Feast definitions and materialize incremental features."""

from datetime import datetime, timedelta, timezone
from pathlib import Path

from feast import FeatureStore


def main() -> None:
    repo = Path(__file__).resolve().parents[1] / "feast_repo"
    store = FeatureStore(repo_path=str(repo))
    store.apply(store.list_feature_views() + store.list_entities() + store.list_feature_services())
    start = datetime.now(timezone.utc) - timedelta(days=30)
    end = datetime.now(timezone.utc)
    store.materialize(start_date=start, end_date=end)
    print(f"materialized features: {start.isoformat()} -> {end.isoformat()}")


if __name__ == "__main__":
    main()
