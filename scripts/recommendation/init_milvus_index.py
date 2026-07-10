#!/usr/bin/env python3
"""Initialize Milvus collection for v1 vector recall (128-dim Java hashCode vectors)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

import pymysql
from pymilvus import Collection, CollectionSchema, DataType, FieldSchema, connections, utility
from pymongo import MongoClient

SCRIPT_DIR = Path(__file__).resolve().parent
SEED_DIR = SCRIPT_DIR.parent / "seed"
if str(SEED_DIR) not in sys.path:
    sys.path.insert(0, str(SEED_DIR))

from seed_recommendation_stores import (  # noqa: E402
    MILVUS_COLLECTION,
    VECTOR_DIM,
    build_hash_vector,
    load_seed_notes,
    load_seed_questions,
)

MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "ebook_admin",
    "password": "ebook_123456",
    "database": "ebook_platform",
    "charset": "utf8mb4",
}

MONGO_URI = "mongodb://localhost:27017/note_db"

DEMO_ITEMS = [
    ("NOTE", "1", ["java", "spring", "boot"]),
    ("NOTE", "2", ["machine", "learning", "recommendation"]),
    ("QUESTION", "q-1", ["how", "to", "build", "feed", "ranking"]),
]


def build_demo_vectors(dim: int) -> list[list[float]]:
    return [build_hash_vector(terms, dim) for _, _, terms in DEMO_ITEMS]


def build_seed_vectors(notes: list[dict[str, Any]], questions: list[dict[str, Any]], dim: int):
    item_types: list[str] = []
    item_ids: list[str] = []
    embeddings: list[list[float]] = []

    for note in notes:
        terms = [note["tag_name"], note["title"]]
        item_types.append("NOTE")
        item_ids.append(str(note["note_id"]))
        embeddings.append(build_hash_vector(terms, dim))

    for question in questions:
        tags = question.get("tags") or []
        terms = [question.get("title", ""), tags[0] if tags else "question"]
        item_types.append("QUESTION")
        item_ids.append(str(question["questionId"]))
        embeddings.append(build_hash_vector(terms, dim))

    return item_types, item_ids, embeddings


def main(
    host: str,
    port: int,
    collection_name: str,
    dim: int,
    from_seed: bool,
) -> None:
    connections.connect(alias="default", host=host, port=port)
    if utility.has_collection(collection_name):
        utility.drop_collection(collection_name)

    fields = [
        FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
        FieldSchema(name="item_type", dtype=DataType.VARCHAR, max_length=16),
        FieldSchema(name="item_id", dtype=DataType.VARCHAR, max_length=64),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=dim),
    ]
    schema = CollectionSchema(fields, description="recommend item vectors v1")
    collection = Collection(collection_name, schema)

    if from_seed:
        conn = pymysql.connect(**MYSQL_CONFIG)
        mongo = MongoClient(MONGO_URI)
        try:
            notes = load_seed_notes(conn)
            questions = load_seed_questions(mongo.get_default_database())
            if not notes:
                raise RuntimeError("No seed notes found. Run scripts/seed/seed_cs_demo_data.py first.")
            item_types, item_ids, embeddings = build_seed_vectors(notes, questions, dim)
        finally:
            conn.close()
            mongo.close()
    else:
        item_types = [item[0] for item in DEMO_ITEMS]
        item_ids = [item[1] for item in DEMO_ITEMS]
        embeddings = build_demo_vectors(dim)

    collection.insert([item_types, item_ids, embeddings])
    collection.flush()
    index_params = {"index_type": "IVF_FLAT", "metric_type": "IP", "params": {"nlist": 128}}
    collection.create_index("embedding", index_params)
    collection.load()

    print(f"Initialized collection={collection_name} dim={dim} vectors={len(item_ids)} from_seed={from_seed}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=19530)
    parser.add_argument("--collection", default=MILVUS_COLLECTION)
    parser.add_argument("--dim", type=int, default=VECTOR_DIM)
    parser.add_argument(
        "--from-seed",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Load vectors from @seed.local notes/questions (default: true)",
    )
    args = parser.parse_args()
    main(args.host, args.port, args.collection, args.dim, args.from_seed)
