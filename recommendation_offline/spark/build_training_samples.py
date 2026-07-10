#!/usr/bin/env python3
"""Build training samples from exported MySQL/Kafka parquet files."""

import argparse
from pathlib import Path

from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.window import Window


FEATURE_COLUMNS = [
    "views",
    "likes",
    "favorites",
    "comments",
    "answers",
    "tag_match_score",
    "recall_score",
]


def build_samples(input_dir: Path, output: Path) -> None:
    spark = (
        SparkSession.builder.master("local[*]")
        .appName("recommend-offline-sample-builder")
        .getOrCreate()
    )
    events_path = input_dir / "events.parquet"
    stats_path = input_dir / "note_stats.parquet"
    if not events_path.exists() or not stats_path.exists():
        raise FileNotFoundError(
            f"Missing required inputs: {events_path} and {stats_path}. "
            "Run export_mysql_tables.py and export_kafka_events.py first."
        )

    events = spark.read.parquet(str(events_path)).filter(F.col("user_id").isNotNull())
    stats = spark.read.parquet(str(stats_path))
    events = events.withColumn("item_id_long", F.col("item_id").cast("long"))
    if "tag_match_score" not in events.columns and "tagMatchScore" in events.columns:
        events = events.withColumnRenamed("tagMatchScore", "tag_match_score")

    base = (
        events.join(stats, events.item_id_long == stats.note_id, "left")
        .withColumn("tag_match_score", F.coalesce(F.col("tag_match_score"), F.lit(0.0)))
        .withColumn("recall_score", F.coalesce(F.col("recall_score"), F.lit(1.0)))
        .withColumn("answers", F.coalesce(F.col("answers"), F.lit(0)))
        .withColumn(
            "label",
            F.when(F.col("event_type").isin("CLICK", "LIKE", "FAVORITE", "COMMENT"), F.lit(1)).otherwise(F.lit(0)),
        )
    )

    # Generate deterministic negatives per user using recent impressions.
    impressions = base.filter(F.col("event_type") == "IMPRESSION")
    w = Window.partitionBy("user_id").orderBy(F.col("timestamp").desc())
    negatives = (
        impressions.withColumn("rn", F.row_number().over(w))
        .filter(F.col("rn") <= 20)
        .withColumn("label", F.lit(0))
    )

    positives = base.filter(F.col("label") == 1)
    samples = positives.unionByName(negatives, allowMissingColumns=True)

    for column in FEATURE_COLUMNS:
        samples = samples.withColumn(column, F.coalesce(F.col(column), F.lit(0.0)))

    final_df = samples.select(
        "user_id",
        "item_id",
        *FEATURE_COLUMNS,
        "label",
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    pdf = final_df.toPandas()
    pdf.to_parquet(output, index=False)
    print(f"Wrote {len(pdf)} training samples to {output}")
    spark.stop()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", default="data/raw")
    parser.add_argument("--output", default="data/samples.parquet")
    args = parser.parse_args()
    build_samples(Path(args.input_dir), Path(args.output))
