#!/usr/bin/env python3
"""Initialize Milvus collection and seed simple vectors for v1 vector recall."""

import argparse
import hashlib

from pymilvus import Collection, CollectionSchema, DataType, FieldSchema, connections, utility


def hash_vector(text: str, dim: int) -> list[float]:
    vec = [0.0] * dim
    for token in text.split():
        h = int(hashlib.md5(token.encode("utf-8")).hexdigest(), 16)
        vec[h % dim] += 1.0
    return vec


def main(host: str, port: int, collection_name: str, dim: int) -> None:
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

    seed_items = [
        ("NOTE", "1", "java spring boot"),
        ("NOTE", "2", "machine learning recommendation"),
        ("QUESTION", "q-1", "how to build feed ranking"),
    ]
    vectors = [hash_vector(text, dim) for _, _, text in seed_items]
    collection.insert(
        [
            [item[0] for item in seed_items],
            [item[1] for item in seed_items],
            vectors,
        ]
    )
    collection.flush()
    index_params = {"index_type": "IVF_FLAT", "metric_type": "IP", "params": {"nlist": 128}}
    collection.create_index("embedding", index_params)
    print(f"Initialized collection={collection_name} with {len(seed_items)} seed vectors")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=19530)
    parser.add_argument("--collection", default="recommend_item_vectors")
    parser.add_argument("--dim", type=int, default=128)
    args = parser.parse_args()
    main(args.host, args.port, args.collection, args.dim)
