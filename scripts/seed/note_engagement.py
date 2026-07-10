#!/usr/bin/env python3
"""Build note engagement (likes/favorites/views/comments) backed by real user relations."""

from __future__ import annotations

import random
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Any

import pymysql
from bson import ObjectId
from pymongo.database import Database

COMMENT_TEMPLATES = [
    "讲得很清楚，尤其是核心概念部分，收藏了。",
    "有个小问题：能否补一个实际代码示例？",
    "和教材上的定义对照了一下，结构很完整。",
    "这部分我复习时也卡住了，感谢整理。",
    "建议再补充常见面试追问点。",
    "边界条件写得不错，建议加复杂度分析。",
    "已转发给同学，适合考前快速过一遍。",
    "能否对比一下不同实现方案的优缺点？",
]

REPLY_TEMPLATES = [
    "同感，例子再具体一点会更好。",
    "可以参考官方文档里的示例。",
    "我上次作业就是在这里踩坑的。",
    "后面章节有延伸，可以连着看。",
]


@dataclass
class NoteEngagement:
    note_id: int
    author_id: int
    author_name: str
    note_title: str
    note_created_at: datetime
    liker_ids: list[int]
    favoriter_ids: list[int]
    viewer_ids: list[int]
    top_level_comments: int


def _rng(note_id: int) -> random.Random:
    return random.Random(note_id * 9973 + 17)


def template_remark_thread(comment_count: int) -> list[dict[str, Any]]:
    rng = _rng(comment_count * 31 + 7)
    comments: list[dict[str, Any]] = []
    for _ in range(comment_count):
        replies = [{"content": rng.choice(REPLY_TEMPLATES)} for _ in range(rng.randint(0, 2))]
        comments.append({"content": rng.choice(COMMENT_TEMPLATES), "replies": replies})
    return comments


def resolve_remark_thread(
    *,
    note_title: str,
    author_name: str,
    comment_count: int,
    deepseek: Any | None,
) -> list[dict[str, Any]]:
    if comment_count <= 0:
        return []
    if deepseek is not None:
        try:
            return deepseek.generate_note_remark_thread(
                note_title=note_title,
                author_name=author_name,
                comment_count=comment_count,
            )
        except Exception as exc:
            print(
                f"  DeepSeek remarks failed for note '{note_title[:32]}': {exc}; using template.",
                flush=True,
            )
    return template_remark_thread(comment_count)


def plan_engagement(
    note_id: int,
    author_id: int,
    author_name: str,
    note_title: str,
    note_created_at: datetime,
    candidate_user_ids: list[int],
) -> NoteEngagement:
    rng = _rng(note_id)
    pool = [uid for uid in candidate_user_ids if uid != author_id]
    if not pool:
        pool = candidate_user_ids[:]

    like_count = rng.randint(4, min(18, len(pool)))
    favorite_count = rng.randint(1, min(10, len(pool)))
    liker_ids = rng.sample(pool, k=min(like_count, len(pool)))
    remaining = [uid for uid in pool if uid not in liker_ids]
    favoriter_ids = rng.sample(remaining or pool, k=min(favorite_count, len(remaining or pool)))

    extra_viewers = rng.randint(6, 20)
    viewer_pool = list(set(liker_ids) | set(favoriter_ids))
    extra = rng.sample(pool, k=min(extra_viewers, len(pool)))
    viewer_ids = list(dict.fromkeys(viewer_pool + extra))

    top_level = rng.randint(2, min(5, len(pool)))

    return NoteEngagement(
        note_id=note_id,
        author_id=author_id,
        author_name=author_name,
        note_title=note_title,
        note_created_at=note_created_at,
        liker_ids=liker_ids,
        favoriter_ids=favoriter_ids,
        viewer_ids=viewer_ids,
        top_level_comments=top_level,
    )


def _action_time(base: datetime, rng: random.Random, hours_offset: int) -> datetime:
    return base + timedelta(hours=hours_offset, minutes=rng.randint(0, 50))


def _fmt_remark_time(dt: datetime) -> str:
    return dt.replace(microsecond=0).isoformat(sep="T")


def count_note_remarks(db: Database, note_id: int) -> int:
    """Backend increments note_stats.comments for every remark, including replies."""
    return int(db.remark.count_documents({"note_id": note_id}))


def sync_note_comment_count(cur: pymysql.cursors.Cursor, db: Database, note_id: int) -> int:
    total = count_note_remarks(db, note_id)
    cur.execute(
        "UPDATE note_stats SET comments = %s, updated_at = NOW() WHERE note_id = %s",
        (total, note_id),
    )
    return total


def sync_all_comment_counts(
    conn: pymysql.connections.Connection,
    db: Database,
    *,
    note_ids: list[int] | None = None,
) -> dict[str, int]:
    stats = {"notes": 0, "fixed": 0}
    with conn.cursor() as cur:
        if note_ids is None:
            cur.execute("SELECT note_id, comments FROM note_stats")
            rows = [(int(r[0]), int(r[1])) for r in cur.fetchall()]
        else:
            placeholders = ",".join(["%s"] * len(note_ids))
            cur.execute(
                f"SELECT note_id, comments FROM note_stats WHERE note_id IN ({placeholders})",
                note_ids,
            )
            rows = [(int(r[0]), int(r[1])) for r in cur.fetchall()]

        for note_id, old_total in rows:
            synced = sync_note_comment_count(cur, db, note_id)
            stats["notes"] += 1
            if synced != old_total:
                stats["fixed"] += 1
        conn.commit()
    return stats


def apply_engagement_mysql(
    cur: pymysql.cursors.Cursor,
    engagement: NoteEngagement,
    *,
    clear_existing: bool,
) -> None:
    note_id = engagement.note_id
    rng = _rng(note_id)

    if clear_existing:
        cur.execute("DELETE FROM user_like_note WHERE note_id = %s", (note_id,))
        cur.execute("DELETE FROM user_favorite_note WHERE note_id = %s", (note_id,))

    for idx, user_id in enumerate(engagement.liker_ids):
        like_time = _action_time(engagement.note_created_at, rng, 2 + idx)
        cur.execute(
            """
            INSERT INTO user_like_note (user_id, note_id, like_time)
            VALUES (%s, %s, %s)
            ON DUPLICATE KEY UPDATE like_time = VALUES(like_time)
            """,
            (user_id, note_id, like_time),
        )

    for idx, user_id in enumerate(engagement.favoriter_ids):
        fav_time = _action_time(engagement.note_created_at, rng, 4 + idx)
        cur.execute(
            """
            INSERT INTO user_favorite_note (user_id, note_id, favorite_time)
            VALUES (%s, %s, %s)
            ON DUPLICATE KEY UPDATE favorite_time = VALUES(favorite_time)
            """,
            (user_id, note_id, fav_time),
        )

    last_activity = _action_time(
        engagement.note_created_at,
        rng,
        24 + len(engagement.liker_ids) + len(engagement.favoriter_ids),
    )
    cur.execute(
        """
        UPDATE note_stats
        SET views = %s,
            likes = %s,
            favorites = %s,
            last_activity_at = %s,
            updated_at = %s
        WHERE note_id = %s
        """,
        (
            len(engagement.viewer_ids),
            len(engagement.liker_ids),
            len(engagement.favoriter_ids),
            last_activity,
            last_activity,
            note_id,
        ),
    )


def apply_engagement_mongo(
    db: Database,
    engagement: NoteEngagement,
    user_names: dict[int, str],
    candidate_user_ids: list[int],
    *,
    clear_existing: bool,
    deepseek: Any | None = None,
) -> int:
    note_id = engagement.note_id
    rng = _rng(note_id)

    if clear_existing:
        db.remark.delete_many({"note_id": note_id})

    if engagement.top_level_comments <= 0:
        return 0

    pool = [uid for uid in candidate_user_ids if uid != engagement.author_id]
    thread = resolve_remark_thread(
        note_title=engagement.note_title,
        author_name=engagement.author_name,
        comment_count=engagement.top_level_comments,
        deepseek=deepseek,
    )
    commenters = rng.sample(pool, k=min(len(thread), len(pool)))
    created_base = engagement.note_created_at
    inserted = 0

    for idx, (comment_spec, user_id) in enumerate(zip(thread, commenters)):
        remark_id = str(ObjectId())
        created = _action_time(created_base, rng, 6 + idx * 3)
        top_doc = {
            "_id": remark_id,
            "note_id": note_id,
            "user_id": user_id,
            "username": user_names.get(user_id, f"user_{user_id}"),
            "content": str(comment_spec.get("content", "")).strip() or rng.choice(COMMENT_TEMPLATES),
            "created_at": _fmt_remark_time(created),
            "parent_id": None,
            "is_reply": False,
            "reply_to_remark_id": None,
            "reply_to_user_name": None,
        }
        db.remark.insert_one(top_doc)
        inserted += 1

        replies = comment_spec.get("replies") or []
        reply_authors = rng.sample(
            [u for u in pool if u != user_id] or pool,
            k=min(len(replies), len(pool)),
        )
        for r_idx, reply_spec in enumerate(replies[:2]):
            replier = reply_authors[r_idx % len(reply_authors)]
            reply_created = created + timedelta(minutes=20 + r_idx * 15)
            db.remark.insert_one(
                {
                    "_id": str(ObjectId()),
                    "note_id": note_id,
                    "user_id": replier,
                    "username": user_names.get(replier, f"user_{replier}"),
                    "content": str(reply_spec.get("content", "")).strip() or rng.choice(REPLY_TEMPLATES),
                    "created_at": _fmt_remark_time(reply_created),
                    "parent_id": remark_id,
                    "is_reply": True,
                    "reply_to_remark_id": remark_id,
                    "reply_to_user_name": top_doc["username"],
                }
            )
            inserted += 1

    return inserted


def load_seed_notes(cur: pymysql.cursors.Cursor) -> list[dict[str, Any]]:
    cur.execute(
        """
        SELECT n.id AS note_id,
               n.title AS note_title,
               n.created_at AS note_created_at,
               sp.user_id AS author_id,
               u.username AS author_name
        FROM notes n
        JOIN notebooks nb ON n.notebook_id = nb.id
        JOIN note_spaces sp ON nb.space_id = sp.id
        JOIN users u ON sp.user_id = u.id
        WHERE u.email LIKE %s
        ORDER BY n.id
        """,
        ("%@seed.local",),
    )
    notes: list[dict[str, Any]] = []
    for note_id, note_title, created_at, author_id, author_name in cur.fetchall():
        notes.append(
            {
                "note_id": int(note_id),
                "note_title": str(note_title),
                "author_id": int(author_id),
                "author_name": str(author_name),
                "note_created_at": created_at,
            }
        )
    return notes


def load_seed_users(cur: pymysql.cursors.Cursor) -> tuple[list[int], dict[int, str]]:
    cur.execute("SELECT id, username FROM users WHERE email LIKE %s ORDER BY id", ("%@seed.local",))
    user_ids: list[int] = []
    names: dict[int, str] = {}
    for user_id, username in cur.fetchall():
        user_ids.append(int(user_id))
        names[int(user_id)] = str(username)
    return user_ids, names


def clear_redis_note_stats(redis_client: Any, note_ids: list[int]) -> int:
    deleted = 0
    for note_id in note_ids:
        if redis_client.delete(f"note_stats:{note_id}"):
            deleted += 1
    return deleted


def clear_redis_remark_cache(redis_client: Any, note_ids: list[int]) -> int:
    deleted = 0
    for note_id in note_ids:
        list_key = f"note_id_of_remark_list:{note_id}"
        remark_ids = redis_client.lrange(list_key, 0, -1) or []
        if redis_client.delete(list_key):
            deleted += 1
        for raw_id in remark_ids:
            remark_id = str(raw_id, "utf-8") if isinstance(raw_id, bytes) else str(raw_id)
            remark_id = remark_id.strip().strip('"')
            if not remark_id:
                continue
            for key in (
                f"remark:{remark_id}",
                f"reply_to:{remark_id}",
                f"remark_stats:{remark_id}",
                f"remark_user_like:{remark_id}",
            ):
                if redis_client.delete(key):
                    deleted += 1
    return deleted


def rebuild_note_remarks(
    conn: pymysql.connections.Connection,
    db: Database,
    redis_client: Any | None = None,
    *,
    note_ids: list[int] | None = None,
    deepseek: Any | None = None,
) -> dict[str, int]:
    stats = {"notes": 0, "remarks": 0, "comment_mismatches_fixed": 0, "redis_keys_cleared": 0}
    with conn.cursor() as cur:
        notes = load_seed_notes(cur)
        if note_ids is not None:
            allowed = set(note_ids)
            notes = [n for n in notes if n["note_id"] in allowed]
        user_ids, user_names = load_seed_users(cur)
        if not notes or not user_ids:
            return stats

        target_ids = [n["note_id"] for n in notes]
        db.remark.delete_many({"note_id": {"$in": target_ids}})

        for index, note in enumerate(notes, start=1):
            print(
                f"Generating remarks {index}/{len(notes)}: {note['note_title'][:48]}",
                flush=True,
            )
            engagement = plan_engagement(
                note_id=note["note_id"],
                author_id=note["author_id"],
                author_name=note["author_name"],
                note_title=note["note_title"],
                note_created_at=note["note_created_at"],
                candidate_user_ids=user_ids,
            )
            inserted = apply_engagement_mongo(
                db,
                engagement,
                user_names,
                user_ids,
                clear_existing=False,
                deepseek=deepseek,
            )
            synced = sync_note_comment_count(cur, db, note["note_id"])
            stats["notes"] += 1
            stats["remarks"] += inserted
            if synced != inserted:
                stats["comment_mismatches_fixed"] += 1

        conn.commit()

    if redis_client is not None:
        stats["redis_keys_cleared"] = clear_redis_note_stats(redis_client, target_ids)
        stats["remark_cache_cleared"] = clear_redis_remark_cache(redis_client, target_ids)

    return stats


def rebuild_all_engagement(
    conn: pymysql.connections.Connection,
    db: Database,
    redis_client: Any | None = None,
    *,
    note_ids: list[int] | None = None,
    deepseek: Any | None = None,
) -> dict[str, int]:
    stats = {
        "notes": 0,
        "likes": 0,
        "favorites": 0,
        "remarks": 0,
        "redis_keys_cleared": 0,
        "deepseek_calls": 0,
    }
    with conn.cursor() as cur:
        notes = load_seed_notes(cur)
        if note_ids is not None:
            allowed = set(note_ids)
            notes = [n for n in notes if n["note_id"] in allowed]
        user_ids, user_names = load_seed_users(cur)

        if not notes or not user_ids:
            return stats

        target_ids = [n["note_id"] for n in notes]
        if target_ids:
            placeholders = ",".join(["%s"] * len(target_ids))
            cur.execute(f"DELETE FROM user_like_note WHERE note_id IN ({placeholders})", target_ids)
            cur.execute(f"DELETE FROM user_favorite_note WHERE note_id IN ({placeholders})", target_ids)
            db.remark.delete_many({"note_id": {"$in": target_ids}})

        for index, note in enumerate(notes, start=1):
            print(
                f"Building engagement {index}/{len(notes)}: {note['note_title'][:48]}",
                flush=True,
            )
            engagement = plan_engagement(
                note_id=note["note_id"],
                author_id=note["author_id"],
                author_name=note["author_name"],
                note_title=note["note_title"],
                note_created_at=note["note_created_at"],
                candidate_user_ids=user_ids,
            )
            apply_engagement_mysql(cur, engagement, clear_existing=False)
            inserted = apply_engagement_mongo(
                db,
                engagement,
                user_names,
                user_ids,
                clear_existing=False,
                deepseek=deepseek,
            )
            sync_note_comment_count(cur, db, note["note_id"])
            stats["notes"] += 1
            stats["likes"] += len(engagement.liker_ids)
            stats["favorites"] += len(engagement.favoriter_ids)
            stats["remarks"] += inserted
            if deepseek is not None and engagement.top_level_comments > 0:
                stats["deepseek_calls"] += 1

        conn.commit()

    if redis_client is not None:
        stats["redis_keys_cleared"] = clear_redis_note_stats(redis_client, target_ids)
        stats["remark_cache_cleared"] = clear_redis_remark_cache(redis_client, target_ids)

    return stats
