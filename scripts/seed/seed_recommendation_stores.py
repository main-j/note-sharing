#!/usr/bin/env python3
"""Directly populate Kafka, Redis, Milvus, and offline parquet for recommendation pipeline."""

from __future__ import annotations

import json
import random
import uuid
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

import pandas as pd
import pymysql
import redis
from kafka import KafkaProducer
from pymilvus import Collection, CollectionSchema, DataType, FieldSchema, connections, utility

REDIS_HOST = "172.24.198.86"
REDIS_PORT = 6379
KAFKA_BOOTSTRAP = "localhost:9092"
MILVUS_HOST = "localhost"
MILVUS_PORT = 19530
MILVUS_COLLECTION = "recommend_item_vectors"
VECTOR_DIM = 128

REDIS_KEYS = {
    "hot_notes": "hot_notes",
    "fused_profile": "user_fused_profile:",
    "recent_search": "user_recent_search_terms:",
    "recent_actions": "user_recent_actions:",
    "seen_items": "user_seen_items:",
    "item_stats": "item_realtime_stats:",
}

POSITIVE_EVENTS = {"CLICK", "LIKE", "FAVORITE", "COMMENT"}
BEHAVIOR_EVENTS = {"CLICK", "VIEW", "LIKE", "FAVORITE", "COMMENT", "FOLLOW"}
SEARCH_KEYWORDS = [
    "TCP三次握手", "动态规划", "红黑树", "进程调度", "B+树索引", "Raft共识",
    "Docker容器", "JVM垃圾回收", "HTTP2多路复用", "区块链智能合约", "编译器优化",
    "微服务架构", "离散数学图论", "虚拟内存", "Spring Boot", "Kubernetes",
    "MySQL事务隔离", "分布式CAP", "操作系统死锁", "云计算Serverless",
]

EVENT_WEIGHTS = {
    "COMMENT": 4,
    "FAVORITE": 3,
    "LIKE": 2,
    "FOLLOW": 2,
    "CLICK": 1,
    "VIEW": 1,
}


def java_hashcode(text: str) -> int:
    h = 0
    for ch in text:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    if h >= 0x80000000:
        h -= 0x100000000
    return h


def build_hash_vector(terms: list[str], dim: int = VECTOR_DIM) -> list[float]:
    vec = [0.0] * dim
    for term in terms:
        if not term:
            continue
        idx = abs(java_hashcode(term)) % dim
        vec[idx] += 1.0
    return vec


def load_seed_notes(conn: pymysql.connections.Connection) -> list[dict[str, Any]]:
    sql = """
        SELECT n.id AS note_id, n.title, sp.user_id AS author_id, t.name AS tag_name
        FROM notes n
        JOIN notebooks nb ON n.notebook_id = nb.id
        JOIN note_spaces sp ON nb.space_id = sp.id
        JOIN tags t ON sp.tag_id = t.id
        JOIN users u ON sp.user_id = u.id
        WHERE u.email LIKE %s
        ORDER BY n.id
    """
    with conn.cursor(pymysql.cursors.DictCursor) as cur:
        cur.execute(sql, ("%@seed.local",))
        return list(cur.fetchall())


def load_seed_users(conn: pymysql.connections.Connection) -> list[dict[str, Any]]:
    with conn.cursor(pymysql.cursors.DictCursor) as cur:
        cur.execute(
            "SELECT id, username, email FROM users WHERE email LIKE %s ORDER BY id",
            ("%@seed.local",),
        )
        return list(cur.fetchall())


def load_seed_questions(mongo_db) -> list[dict[str, Any]]:
    return list(
        mongo_db.questions.find({"tags": "seed"}, {"questionId": 1, "title": 1, "tags": 1, "_id": 0})
    )


def make_event(
    user_id: int,
    event_type: str,
    note: dict[str, Any] | None = None,
    keyword: str | None = None,
    ts_ms: int | None = None,
) -> dict[str, Any]:
    now_ms = ts_ms or int((datetime.now() - timedelta(minutes=random.randint(1, 10000))).timestamp() * 1000)
    event: dict[str, Any] = {
        "eventId": str(uuid.uuid4()),
        "requestId": str(uuid.uuid4()),
        "userId": user_id,
        "eventType": event_type,
        "scene": "HOME",
        "experimentId": "recommend_rank_v1",
        "variant": "control_rule",
        "modelVersion": "pending",
        "ranker": "rule",
        "timestamp": now_ms,
        "tags": [],
        "tagMatchScore": round(random.uniform(0.2, 0.95), 3),
        "recall_score": round(random.uniform(0.8, 1.6), 3),
    }
    if keyword:
        event["keyword"] = keyword
    if note:
        event.update(
            {
                "itemType": "NOTE",
                "itemId": str(note["note_id"]),
                "authorId": note["author_id"],
                "source": random.choice(["HOT", "TAG", "FOLLOW", "COLD_START", "VECTOR"]),
                "position": random.randint(1, 20),
                "tags": [note["tag_name"], note["tag_name"].replace("-", " ")],
            }
        )
    return event


def generate_events(users: list[dict], notes: list[dict]) -> tuple[list[dict], list[dict], list[dict]]:
    behavior: list[dict] = []
    search: list[dict] = []
    exposure: list[dict] = []

    user_ids = [u["id"] for u in users]
    for _ in range(2200):
        user_id = random.choice(user_ids)
        note = random.choice(notes)
        exposure.append(make_event(user_id, "IMPRESSION", note))

    for _ in range(900):
        user_id = random.choice(user_ids)
        note = random.choice(notes)
        behavior.append(make_event(user_id, random.choice(["CLICK", "VIEW"]), note))

    for _ in range(260):
        user_id = random.choice(user_ids)
        note = random.choice(notes)
        behavior.append(make_event(user_id, "LIKE", note))

    for _ in range(180):
        user_id = random.choice(user_ids)
        note = random.choice(notes)
        behavior.append(make_event(user_id, "FAVORITE", note))

    for _ in range(140):
        user_id = random.choice(user_ids)
        note = random.choice(notes)
        behavior.append(make_event(user_id, "COMMENT", note))

    for _ in range(350):
        user_id = random.choice(user_ids)
        search.append(make_event(user_id, "SEARCH", keyword=random.choice(SEARCH_KEYWORDS)))

    return behavior, search, exposure


def publish_kafka(events: list[dict], topic: str, producer: KafkaProducer) -> int:
    count = 0
    for event in events:
        producer.send(topic, value=event)
        count += 1
    producer.flush()
    return count


def compute_hot_notes(notes: list[dict], events: list[dict]) -> list[int]:
    scores: Counter[int] = Counter()
    note_ids = {n["note_id"] for n in notes}
    for event in events:
        if event.get("itemType") != "NOTE":
            continue
        if event.get("eventType") == "IMPRESSION":
            continue
        note_id = int(event["itemId"])
        if note_id not in note_ids:
            continue
        weight = EVENT_WEIGHTS.get(event["eventType"], 1)
        scores[note_id] += weight
    if not scores:
        return [n["note_id"] for n in notes[:10]]
    return [note_id for note_id, _ in scores.most_common(10)]


def build_user_fused_profiles(users: list[dict], notes: list[dict], events: list[dict]) -> dict[int, dict[str, float]]:
    note_by_id = {n["note_id"]: n for n in notes}
    profiles: dict[int, dict[str, float]] = defaultdict(lambda: defaultdict(float))

    for event in events:
        user_id = event["userId"]
        if event.get("eventType") == "SEARCH" and event.get("keyword"):
            profiles[user_id][event["keyword"]] += 0.6
        elif event.get("itemType") == "NOTE" and event.get("eventType") != "IMPRESSION":
            note = note_by_id.get(int(event["itemId"]))
            if not note:
                continue
            weight = EVENT_WEIGHTS.get(event["eventType"], 1) * 0.4
            profiles[user_id][note["tag_name"]] += weight
            for token in note["title"].split("：")[-1].split():
                if len(token) >= 2:
                    profiles[user_id][token] += weight * 0.5

    return {uid: dict(scores) for uid, scores in profiles.items()}


def populate_redis(
    redis_client: redis.Redis,
    users: list[dict],
    notes: list[dict],
    all_events: list[dict],
    hot_note_ids: list[int],
) -> dict[str, int]:
    stats = {"profiles": 0, "searches": 0, "actions": 0, "seen": 0, "item_stats": 0}

    redis_client.set(REDIS_KEYS["hot_notes"], json.dumps(hot_note_ids))

    fused_profiles = build_user_fused_profiles(users, notes, all_events)
    for user_id, profile in fused_profiles.items():
        if profile:
            redis_client.set(REDIS_KEYS["fused_profile"] + str(user_id), json.dumps(profile, ensure_ascii=False))
            stats["profiles"] += 1

    for user_id in [u["id"] for u in users]:
        user_events = [e for e in all_events if e["userId"] == user_id]
        search_terms = [e["keyword"] for e in user_events if e.get("eventType") == "SEARCH" and e.get("keyword")]
        if search_terms:
            redis_client.delete(REDIS_KEYS["recent_search"] + str(user_id))
            redis_client.lpush(REDIS_KEYS["recent_search"] + str(user_id), *search_terms[:200])
            stats["searches"] += 1

        seen_keys = []
        action_entries = []
        for event in sorted(user_events, key=lambda e: e["timestamp"], reverse=True):
            if event.get("itemType") == "NOTE" and event.get("itemId"):
                item_key = f"NOTE:{event['itemId']}"
                seen_keys.append(item_key)
                if event.get("eventType") != "IMPRESSION":
                    action_entries.append(f"{event['eventType']}|{item_key}")
        if seen_keys:
            redis_client.delete(REDIS_KEYS["seen_items"] + str(user_id))
            redis_client.sadd(REDIS_KEYS["seen_items"] + str(user_id), *seen_keys[:300])
            stats["seen"] += 1
        if action_entries:
            redis_client.delete(REDIS_KEYS["recent_actions"] + str(user_id))
            redis_client.lpush(REDIS_KEYS["recent_actions"] + str(user_id), *action_entries[:200])
            stats["actions"] += 1

    hot_scores: Counter[int] = Counter()
    for event in all_events:
        if event.get("itemType") != "NOTE" or event.get("eventType") == "IMPRESSION":
            continue
        hot_scores[int(event["itemId"])] += EVENT_WEIGHTS.get(event["eventType"], 1)
    for note_id, score in hot_scores.items():
        payload = {"hotScore": score, "updatedAt": int(datetime.now().timestamp() * 1000)}
        redis_client.set(REDIS_KEYS["item_stats"] + f"NOTE:{note_id}", json.dumps(payload))
        stats["item_stats"] += 1

    return stats


def seed_milvus(notes: list[dict], questions: list[dict]) -> int:
    connections.connect(alias="default", host=MILVUS_HOST, port=MILVUS_PORT)
    if utility.has_collection(MILVUS_COLLECTION):
        utility.drop_collection(MILVUS_COLLECTION)

    fields = [
        FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
        FieldSchema(name="item_type", dtype=DataType.VARCHAR, max_length=16),
        FieldSchema(name="item_id", dtype=DataType.VARCHAR, max_length=64),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=VECTOR_DIM),
    ]
    schema = CollectionSchema(fields, description="recommend item vectors seed")
    collection = Collection(MILVUS_COLLECTION, schema)

    item_types: list[str] = []
    item_ids: list[str] = []
    embeddings: list[list[float]] = []

    for note in notes:
        terms = [note["tag_name"], note["title"]]
        item_types.append("NOTE")
        item_ids.append(str(note["note_id"]))
        embeddings.append(build_hash_vector(terms))

    for question in questions:
        tags = question.get("tags") or []
        terms = [question.get("title", ""), tags[0] if tags else "question"]
        item_types.append("QUESTION")
        item_ids.append(str(question["questionId"]))
        embeddings.append(build_hash_vector(terms))

    collection.insert([item_types, item_ids, embeddings])
    collection.flush()
    collection.create_index(
        "embedding",
        {"index_type": "IVF_FLAT", "metric_type": "IP", "params": {"nlist": 128}},
    )
    collection.load()
    return len(item_ids)


def export_raw_parquet(conn: pymysql.connections.Connection, events: list[dict], repo_root: Path) -> None:
    raw_dir = repo_root / "data" / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)

    events_df = pd.DataFrame(events)
    events_df.rename(
        columns={
            "userId": "user_id",
            "itemId": "item_id",
            "eventType": "event_type",
            "authorId": "author_id",
        },
        inplace=True,
    )
    events_df.to_parquet(raw_dir / "events.parquet", index=False)

    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT note_id, views, likes, favorites, comments, 0 AS answers
            FROM note_stats
            WHERE note_id IN (
                SELECT n.id FROM notes n
                JOIN notebooks nb ON n.notebook_id = nb.id
                JOIN note_spaces sp ON nb.space_id = sp.id
                JOIN users u ON sp.user_id = u.id
                WHERE u.email LIKE %s
            )
            """,
            ("%@seed.local",),
        )
        stats_rows = cur.fetchall()
        stats_cols = ["note_id", "views", "likes", "favorites", "comments", "answers"]
        pd.DataFrame(stats_rows, columns=stats_cols).to_parquet(raw_dir / "note_stats.parquet", index=False)

        cur.execute(
            """
            SELECT follower_id, followee_id, follow_time AS created_at
            FROM user_follow
            WHERE follower_id IN (SELECT id FROM users WHERE email LIKE %s)
            """,
            ("%@seed.local",),
        )
        follow_rows = cur.fetchall()
        pd.DataFrame(follow_rows, columns=["follower_id", "followee_id", "created_at"]).to_parquet(
            raw_dir / "user_follow.parquet", index=False
        )

        cur.execute(
            """
            SELECT n.id AS note_id, n.title, n.updated_at
            FROM notes n
            JOIN notebooks nb ON n.notebook_id = nb.id
            JOIN note_spaces sp ON nb.space_id = sp.id
            JOIN users u ON sp.user_id = u.id
            WHERE u.email LIKE %s
            """,
            ("%@seed.local",),
        )
        note_rows = cur.fetchall()
        pd.DataFrame(note_rows, columns=["note_id", "title", "updated_at"]).to_parquet(
            raw_dir / "notes.parquet", index=False
        )


def export_feast_parquet(repo_root: Path) -> None:
    raw_dir = repo_root / "data" / "raw"
    feast_dir = repo_root / "data" / "feast"
    feast_dir.mkdir(parents=True, exist_ok=True)

    events = pd.read_parquet(raw_dir / "events.parquet")
    stats = pd.read_parquet(raw_dir / "note_stats.parquet")
    now = pd.Timestamp.utcnow()

    user_features = (
        events.groupby("user_id", as_index=False)
        .agg(recent_search_count=("keyword", lambda s: s.notna().sum()))
        .merge(
            events[events["event_type"] == "SEARCH"]
            .groupby("user_id")["keyword"]
            .apply(lambda s: list(dict.fromkeys(s.dropna()))[:2])
            .reset_index(name="keywords"),
            on="user_id",
            how="left",
        )
    )
    user_features["top_tag_1"] = user_features["keywords"].apply(
        lambda ks: ks[0] if isinstance(ks, list) and ks else "programming"
    )
    user_features["top_tag_2"] = user_features["keywords"].apply(
        lambda ks: ks[1] if isinstance(ks, list) and len(ks) > 1 else "algorithms"
    )
    user_features["active_days_7d"] = 7
    user_features["event_timestamp"] = now
    user_features = user_features[["user_id", "top_tag_1", "top_tag_2", "recent_search_count", "active_days_7d", "event_timestamp"]]
    user_features.to_parquet(feast_dir / "user_features.parquet", index=False)

    item_features = stats.rename(columns={"note_id": "item_id"}).assign(
        hot_score=lambda x: x["likes"] * 1.0 + x["favorites"] * 1.4 + x["comments"] * 1.8,
        event_timestamp=now,
    )
    item_features.to_parquet(feast_dir / "item_features.parquet", index=False)

    author_features = (
        events.dropna(subset=["author_id"])
        .groupby("author_id", as_index=False)
        .agg(author_recent_posts=("item_id", "nunique"))
        .assign(author_quality_score=0.65, event_timestamp=now)
    )
    author_features.to_parquet(feast_dir / "author_features.parquet", index=False)

    cross = events.dropna(subset=["user_id", "item_id"]).copy()
    cross["user_item_key"] = cross["user_id"].astype(str) + "_" + cross["item_id"].astype(str)
    cross_features = (
        cross.groupby("user_item_key", as_index=False)
        .agg(tag_match_score=("tagMatchScore", "max"))
        .assign(is_followee_author=0, event_timestamp=now)
    )
    cross_features.to_parquet(feast_dir / "cross_features.parquet", index=False)


def export_training_samples(repo_root: Path) -> int:
    raw_dir = repo_root / "data" / "raw"
    events = pd.read_parquet(raw_dir / "events.parquet")
    stats = pd.read_parquet(raw_dir / "note_stats.parquet")

    events = events[events["item_id"].notna()].copy()
    merged = events.merge(stats, left_on=events["item_id"].astype("Int64"), right_on="note_id", how="left")
    merged["tag_match_score"] = merged["tagMatchScore"].fillna(0.0)
    merged["recall_score"] = merged["recall_score"].fillna(1.0)
    merged["answers"] = merged["answers"].fillna(0)
    merged["label"] = merged["event_type"].isin(list(POSITIVE_EVENTS)).astype(int)

    positives = merged[merged["label"] == 1]
    impressions = merged[merged["event_type"] == "IMPRESSION"].sort_values("timestamp", ascending=False)
    negatives = impressions.groupby("user_id", group_keys=False).head(20).copy()
    negatives["label"] = 0

    samples = pd.concat([positives, negatives], ignore_index=True)
    feature_cols = ["views", "likes", "favorites", "comments", "answers", "tag_match_score", "recall_score"]
    for col in feature_cols:
        samples[col] = samples[col].fillna(0.0)

    out = samples[["user_id", "item_id", *feature_cols, "label"]]
    out_path = repo_root / "data" / "samples.parquet"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out.to_parquet(out_path, index=False)
    return len(out)


def seed_recommendation_stores(
    conn: pymysql.connections.Connection,
    mongo_db,
    repo_root: Path,
    skip_kafka: bool = False,
) -> dict[str, Any]:
    users = load_seed_users(conn)
    notes = load_seed_notes(conn)
    questions = load_seed_questions(mongo_db)
    if not users or not notes:
        raise RuntimeError("Seed users/notes not found. Run base seed first.")

    behavior, search, exposure = generate_events(users, notes)
    all_events = behavior + search + exposure

    kafka_stats = {"behavior": 0, "search": 0, "exposure": 0}
    if not skip_kafka:
        producer = KafkaProducer(
            bootstrap_servers=KAFKA_BOOTSTRAP,
            value_serializer=lambda v: json.dumps(v, ensure_ascii=False).encode("utf-8"),
        )
        kafka_stats["behavior"] = publish_kafka(behavior, "rec_user_behavior", producer)
        kafka_stats["search"] = publish_kafka(search, "rec_user_search", producer)
        kafka_stats["exposure"] = publish_kafka(exposure, "rec_user_exposure", producer)
        producer.close()

    redis_client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
    hot_note_ids = compute_hot_notes(notes, all_events)
    redis_stats = populate_redis(redis_client, users, notes, all_events, hot_note_ids)

    milvus_count = seed_milvus(notes, questions)
    export_raw_parquet(conn, all_events, repo_root)
    export_feast_parquet(repo_root)
    sample_count = export_training_samples(repo_root)

    positive_count = sum(1 for e in all_events if e.get("eventType") in POSITIVE_EVENTS)
    return {
        "events_total": len(all_events),
        "events_positive": positive_count,
        "kafka": kafka_stats,
        "redis": redis_stats,
        "milvus_vectors": milvus_count,
        "training_samples": sample_count,
        "hot_notes": hot_note_ids,
    }
