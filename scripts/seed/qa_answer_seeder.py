#!/usr/bin/env python3
"""Seed answers with nested comments/replies for existing MongoDB questions."""

from __future__ import annotations

import random
from datetime import datetime, timedelta
from typing import Any
from zoneinfo import ZoneInfo

ANSWERS_PER_QUESTION = 3
LOCAL_TZ = ZoneInfo("Asia/Shanghai")


def template_answer_thread(question_title: str, question_content: str, answer_count: int = ANSWERS_PER_QUESTION) -> list[dict[str, Any]]:
    answers: list[dict[str, Any]] = []
    for idx in range(answer_count):
        answers.append(
            {
                "content": (
                    f"关于「{question_title}」，可以从定义、适用场景和常见误区三个角度理解。"
                    f"结合问题描述「{question_content[:80]}…」，建议先梳理核心概念，再对照实例验证。"
                    f"这是第 {idx + 1} 条回答，供参考。"
                ),
                "comments": [
                    {
                        "content": "补充一点：实际项目中还要考虑边界条件和性能开销。",
                        "replies": [
                            {"content": "同意，我上次踩坑就是因为忽略了边界情况。"},
                            {"content": "可以结合单元测试和 profiling 一起验证。"},
                        ],
                    },
                    {
                        "content": "有没有推荐的学习资料或开源实现？",
                        "replies": [
                            {"content": "官方文档 + 经典教材章节通常足够入门。"},
                        ],
                    },
                ],
            }
        )
    return answers


class IdFactory:
    def __init__(self, base_ms: int | None = None) -> None:
        self._next = base_ms or int(datetime.now(LOCAL_TZ).timestamp() * 1000)

    def next_id(self) -> int:
        value = self._next
        self._next += 1
        return value


def pick_users(candidates: list[int], count: int, exclude: set[int] | None = None) -> list[int]:
    pool = [user_id for user_id in candidates if user_id not in (exclude or set())]
    if len(pool) < count:
        pool = candidates[:]
    return random.sample(pool, k=min(count, len(pool)))


def build_answer_documents(
    *,
    question: dict[str, Any],
    user_ids: list[int],
    thread: list[dict[str, Any]],
    id_factory: IdFactory,
) -> list[dict[str, Any]]:
    author_id = int(question["authorId"])
    question_created = question.get("createdAt") or datetime.now(LOCAL_TZ)
    answerers = pick_users(user_ids, len(thread), exclude={author_id})

    documents: list[dict[str, Any]] = []
    for offset, (answer_spec, answerer_id) in enumerate(zip(thread, answerers)):
        answer_created = question_created + timedelta(hours=2 + offset * 5)
        comment_docs: list[dict[str, Any]] = []
        comments = answer_spec.get("comments") or []
        for comment_idx, comment_spec in enumerate(comments[:2]):
            comment_created = answer_created + timedelta(hours=1 + comment_idx)
            reply_docs: list[dict[str, Any]] = []
            replies = comment_spec.get("replies") or []
            reply_authors = pick_users(user_ids, len(replies[:2]), exclude={answerer_id})
            for reply_idx, reply_spec in enumerate(replies[:2]):
                reply_author = reply_authors[reply_idx % len(reply_authors)]
                reply_docs.append(
                    {
                        "replyId": id_factory.next_id(),
                        "authorId": reply_author,
                        "content": str(reply_spec.get("content", "")).strip(),
                        "createdAt": comment_created + timedelta(minutes=20 + reply_idx * 15),
                        "likes": random.sample(user_ids, k=min(2, len(user_ids))),
                    }
                )
            comment_author = pick_users(user_ids, 1, exclude={answerer_id})[0]
            comment_docs.append(
                {
                    "commentId": id_factory.next_id(),
                    "authorId": comment_author,
                    "content": str(comment_spec.get("content", "")).strip(),
                    "createdAt": comment_created,
                    "likes": random.sample(user_ids, k=min(2, len(user_ids))),
                    "replies": reply_docs,
                }
            )

        documents.append(
            {
                "answerId": id_factory.next_id(),
                "authorId": answerer_id,
                "content": str(answer_spec.get("content", "")).strip(),
                "createdAt": answer_created,
                "likes": random.sample(user_ids, k=min(4, len(user_ids))),
                "comments": comment_docs,
            }
        )
    return documents


def seed_answers_for_questions(
    db,
    user_ids: list[int],
    *,
    deepseek=None,
    force: bool = False,
    limit: int | None = None,
) -> dict[str, int]:
    query: dict[str, Any] = {}
    if not force:
        query = {"$or": [{"answers": {"$exists": False}}, {"answers": {"$size": 0}}]}

    questions = list(db.questions.find(query).sort("createdAt", 1))
    if limit is not None:
        questions = questions[:limit]

    stats = {"questions": 0, "answers": 0, "comments": 0, "replies": 0, "deepseek_calls": 0}
    id_factory = IdFactory()

    for index, question in enumerate(questions, start=1):
        title = str(question.get("title", "")).strip()
        content = str(question.get("content", "")).strip()
        if not title:
            continue

        print(f"Seeding answers {index}/{len(questions)}: {title[:48]}", flush=True)
        if deepseek is not None:
            thread = deepseek.generate_answer_thread(
                question_title=title,
                question_content=content,
                answer_count=ANSWERS_PER_QUESTION,
            )
            stats["deepseek_calls"] += 1
        else:
            thread = template_answer_thread(title, content, ANSWERS_PER_QUESTION)

        answer_docs = build_answer_documents(
            question=question,
            user_ids=user_ids,
            thread=thread,
            id_factory=id_factory,
        )
        db.questions.update_one({"_id": question["_id"]}, {"$set": {"answers": answer_docs}})
        stats["questions"] += 1
        stats["answers"] += len(answer_docs)
        for answer in answer_docs:
            stats["comments"] += len(answer.get("comments") or [])
            for comment in answer.get("comments") or []:
                stats["replies"] += len(comment.get("replies") or [])

    return stats
