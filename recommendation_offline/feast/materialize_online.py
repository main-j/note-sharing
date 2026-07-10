#!/usr/bin/env python3
"""Apply Feast definitions and materialize incremental features."""

import os
import subprocess
from datetime import datetime, timedelta, timezone
from pathlib import Path

from feast import FeatureStore


def main() -> None:
    default_repo = Path(__file__).resolve().parents[1] / "feast_repo"
    repo = Path(os.environ.get("FEAST_REPO_PATH", default_repo))
    repo_path = str(repo)
    subprocess.run(["feast", "-c", repo_path, "apply"], check=True)

    store = FeatureStore(repo_path=repo_path)
    start = datetime.now(timezone.utc) - timedelta(days=30)
    end = datetime.now(timezone.utc)
    store.materialize(start_date=start, end_date=end)
    print(f"materialized features: {start.isoformat()} -> {end.isoformat()}")


if __name__ == "__main__":
    main()
