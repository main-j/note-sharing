#!/usr/bin/env python3
"""Export recommendation Kafka topics to parquet for offline training."""

import argparse
import json
from pathlib import Path

import pandas as pd
from kafka import KafkaConsumer


def export(bootstrap: str, topics: list[str], output: Path, max_records: int) -> None:
    consumer = KafkaConsumer(
        *topics,
        bootstrap_servers=bootstrap,
        value_deserializer=lambda v: json.loads(v.decode("utf-8")),
        auto_offset_reset="earliest",
        enable_auto_commit=False,
        consumer_timeout_ms=5000,
    )
    rows = []
    for message in consumer:
        rows.append(message.value)
        if len(rows) >= max_records:
            break
    consumer.close()
    if not rows:
        raise RuntimeError("No events consumed from Kafka topics.")
    df = pd.DataFrame(rows)
    output.parent.mkdir(parents=True, exist_ok=True)
    df.to_parquet(output, index=False)
    print(f"exported {len(df)} records to {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--bootstrap", default="localhost:9092")
    parser.add_argument(
        "--topics",
        nargs="+",
        default=["rec_user_behavior", "rec_user_search", "rec_user_exposure", "rec_content_event"],
    )
    parser.add_argument("--output", default="data/raw/events.parquet")
    parser.add_argument("--max-records", type=int, default=200000)
    args = parser.parse_args()
    export(args.bootstrap, args.topics, Path(args.output), args.max_records)
