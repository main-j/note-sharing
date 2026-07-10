#!/usr/bin/env python3
"""Seed 50 CS demo users with note spaces, MD/PDF uploads, stats, QA, and follows."""

from __future__ import annotations

import argparse
import io
import random
import re
import sys
import uuid
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

import bcrypt
import pymysql
from minio import Minio
from pymongo import MongoClient

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from seed_recommendation_stores import seed_recommendation_stores
from qa_answer_seeder import seed_answers_for_questions
from note_engagement import rebuild_all_engagement

try:
    from deepseek_client import DeepSeekClient, markdown_to_pdf_paragraphs
except ImportError:
    DeepSeekClient = None  # type: ignore[misc, assignment]
    markdown_to_pdf_paragraphs = None  # type: ignore[misc, assignment]

DEFAULT_PASSWORD = "Seed123456"
USER_COUNT = 50
NOTES_PER_USER = 3
QUESTIONS_PER_USER = 2

MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "ebook_admin",
    "password": "ebook_123456",
    "database": "ebook_platform",
    "charset": "utf8mb4",
    "autocommit": False,
}

MINIO_CONFIG = {
    "endpoint": "localhost:9000",
    "access_key": "name",
    "secret_key": "password",
    "bucket": "notesharing",
    "secure": False,
}

MONGO_URI = "mongodb://localhost:27017/note_db"
LOCAL_TZ = ZoneInfo("Asia/Shanghai")

DOMAINS: list[dict[str, Any]] = [
    {
        "tag": "programming",
        "label": "编程语言",
        "topics": [
            ("Python 类型系统与动态特性", "Python 使用 duck typing，核心内置类型包括 list、dict、set。类型注解可通过 typing 模块增强可读性。"),
            ("Java 面向对象与 JVM 基础", "Java 通过 class 定义对象，JVM 负责字节码加载、验证、执行与 GC。理解 ClassLoader 有助于排查类冲突。"),
            ("C++ 内存管理与 RAII", "C++ 推荐 RAII 管理资源，智能指针 unique_ptr/shared_ptr 可避免内存泄漏。"),
        ],
    },
    {
        "tag": "algorithms",
        "label": "数据结构与算法",
        "topics": [
            ("平衡二叉搜索树 AVL 与红黑树", "AVL 严格平衡，红黑树通过颜色约束近似平衡，均保证 O(log n) 查找。"),
            ("图的最短路径 Dijkstra 与 Bellman-Ford", "Dijkstra 适用于非负权图；Bellman-Ford 可检测负权环。"),
            ("动态规划经典模型", "背包、LIS、区间 DP 是面试与工程优化常见模型，关键是状态定义与转移方程。"),
        ],
    },
    {
        "tag": "architecture",
        "label": "计算机组成原理",
        "topics": [
            ("CPU 流水线与冒险", "结构冒险、数据冒险、控制冒险可通过转发、停顿、分支预测缓解。"),
            ("Cache 映射与一致性", "直接映射、组相联、全相联影响命中率；MESI 协议维护多核 Cache 一致性。"),
            ("指令集 CISC 与 RISC", "CISC 指令复杂、RISC 指令规整，现代处理器常采用 RISC 内核 + 微码翻译。"),
        ],
    },
    {
        "tag": "os",
        "label": "操作系统",
        "topics": [
            ("进程与线程调度", "FCFS、SJF、RR、多级反馈队列是经典调度算法，需权衡响应时间与吞吐量。"),
            ("虚拟内存与页面置换", "分页结合 TLB 加速地址转换；LRU、Clock 等算法决定页面换出策略。"),
            ("死锁四个必要条件", "互斥、占有并等待、不可抢占、循环等待，破坏任一条件即可预防死锁。"),
        ],
    },
    {
        "tag": "discrete-math",
        "label": "离散数学",
        "topics": [
            ("命题逻辑与谓词逻辑", "合取、析取、蕴含、等价是逻辑推理基础；量词 ∀ 与 ∃ 描述性质。"),
            ("集合论与关系", "并交差补、笛卡尔积、等价关系与偏序关系在数据库与算法中广泛使用。"),
            ("图论基础", "连通性、欧拉路、哈密顿路、树与生成树是网络与编译优化的数学基础。"),
        ],
    },
    {
        "tag": "networks",
        "label": "计算机网络",
        "topics": [
            ("TCP 三次握手与四次挥手", "SYN、SYN-ACK、ACK 建立连接；TIME_WAIT 保证旧报文不会干扰新连接。"),
            ("HTTP/1.1 与 HTTP/2", "HTTP/2 多路复用减少队头阻塞，头部压缩降低开销。"),
            ("DNS 解析与 CDN 加速", "递归与迭代查询配合 TTL 缓存；CDN 通过边缘节点降低时延。"),
        ],
    },
    {
        "tag": "database",
        "label": "数据库系统",
        "topics": [
            ("关系代数与 SQL 优化", "选择、投影、连接是 SQL 执行核心；索引与统计信息影响执行计划。"),
            ("事务 ACID 与隔离级别", "Read Uncommitted 到 Serializable 逐级增强一致性，需平衡并发性能。"),
            ("B+ 树索引原理", "InnoDB 聚簇索引叶子节点存整行，二级索引叶子存主键，影响回表成本。"),
        ],
    },
    {
        "tag": "compiler",
        "label": "编译原理",
        "topics": [
            ("词法分析与有限自动机", "正则表达式可转换为 NFA/DFA，用于识别 token。"),
            ("语法分析与 LL/LR", "LL 自顶向下，LR 自底向上；Yacc/Bison 生成 LALR 分析器。"),
            ("中间代码与优化", "三地址码便于数据流分析；常量折叠、死代码消除、循环优化提升性能。"),
        ],
    },
    {
        "tag": "software-eng",
        "label": "软件工程",
        "topics": [
            ("敏捷开发与 Scrum", "迭代交付、每日站会、回顾会议提升团队协作与需求响应速度。"),
            ("设计模式 SOLID 原则", "单一职责、开闭原则等指导可维护架构，避免过度设计。"),
            ("CI/CD 与质量门禁", "自动化测试、静态扫描、制品晋级降低发布风险。"),
        ],
    },
    {
        "tag": "cloud",
        "label": "云计算",
        "topics": [
            ("IaaS/PaaS/SaaS 分层", "IaaS 提供虚拟机，PaaS 提供运行平台，SaaS 直接交付应用。"),
            ("容器与 Kubernetes", "Pod、Deployment、Service 是 K8s 基本对象，支持弹性伸缩与滚动升级。"),
            ("Serverless 与 FaaS", "按请求计费，适合事件驱动场景，需关注冷启动与状态管理。"),
        ],
    },
    {
        "tag": "distributed",
        "label": "分布式系统",
        "topics": [
            ("CAP 与 BASE", "CAP 不可同时满足；BASE 通过最终一致性换取可用性与分区容忍。"),
            ("Raft 共识算法", "Leader 选举、日志复制、安全性保证集群状态一致。"),
            ("分布式事务 2PC/TCC", "两阶段提交强一致但阻塞；TCC 通过 Try/Confirm/Cancel 提升可用性。"),
        ],
    },
    {
        "tag": "blockchain",
        "label": "区块链",
        "topics": [
            ("区块链结构与 Merkle 树", "区块头包含前一区块哈希与 Merkle Root，保证交易不可篡改。"),
            ("PoW 与 PoS 共识", "PoW 算力竞争，PoS 质押投票，后者更节能但需设计惩罚机制。"),
            ("智能合约与 EVM", "Solidity 编写合约，EVM 执行字节码，Gas 限制防止无限循环。"),
        ],
    },
]


def pdf_escape(text: str) -> str:
    return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def build_pdf_bytes(title: str, paragraphs: list[str]) -> bytes:
    from pdf_builder import build_pdf_bytes as build_cjk_pdf

    return build_cjk_pdf(title, paragraphs)


def build_markdown(title: str, domain_label: str, author: str, paragraphs: list[str]) -> str:
    body = "\n\n".join(f"- {p}" for p in paragraphs)
    return (
        f"# {title}\n\n"
        f"> 领域：{domain_label}  \n"
        f"> 作者：{author}  \n"
        f"> 生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n"
        f"## 摘要\n\n"
        f"本笔记用于计算机专业学习与实践，涵盖 {domain_label} 的核心概念、常见面试题与工程实践要点。\n\n"
        f"## 要点\n\n{body}\n\n"
        f"## 练习建议\n\n"
        f"1. 结合代码实现一个最小示例。\n"
        f"2. 绘制知识结构图并复述关键术语。\n"
        f"3. 与同学讨论边界场景与常见误区。\n"
    )


def sanitize_filename(name: str) -> str:
    return re.sub(r"[^a-zA-Z0-9._-]", "_", name)


def minio_object_name(original: str) -> str:
    base, ext = (original.rsplit(".", 1) + [""])[:2]
    ext = f".{ext}" if ext else ""
    return f"{uuid.uuid4()}_{sanitize_filename(base)}{ext}"


class Seeder:
    def __init__(
        self,
        reset: bool,
        skip_kafka: bool = False,
        repo_root: Path | None = None,
        content_source: str = "deepseek",
        refresh_cache: bool = False,
        limit_users: int | None = None,
        skip_stores: bool = False,
        skip_ping: bool = False,
    ) -> None:
        self.reset = reset
        self.skip_kafka = skip_kafka
        self.skip_stores = skip_stores
        self.repo_root = repo_root or Path(__file__).resolve().parents[2]
        self.content_source = content_source
        self.refresh_cache = refresh_cache
        self.user_count = limit_users if limit_users is not None else USER_COUNT
        self.deepseek: DeepSeekClient | None = None
        if content_source == "deepseek":
            if DeepSeekClient is None:
                raise RuntimeError("deepseek_client module unavailable")
            self.deepseek = DeepSeekClient(refresh_cache=refresh_cache)
            print(f"DeepSeek model: {self.deepseek.model}", flush=True)
            if not skip_ping:
                print("Testing DeepSeek connectivity...", flush=True)
                try:
                    print(f"DeepSeek ping response: {self.deepseek.ping()!r}", flush=True)
                except Exception as exc:
                    raise RuntimeError(
                        f"DeepSeek connectivity test failed: {exc}. "
                        "Check DEEPSEEK_API_KEY, model name (try deepseek-v4-pro), and network."
                    ) from exc
        self.note_generation_index = 0
        self.total_note_generations = self.user_count * NOTES_PER_USER
        self.question_generation_index = 0
        self.total_question_generations = self.user_count * QUESTIONS_PER_USER
        self.conn = pymysql.connect(**MYSQL_CONFIG)
        self.minio = Minio(
            MINIO_CONFIG["endpoint"],
            access_key=MINIO_CONFIG["access_key"],
            secret_key=MINIO_CONFIG["secret_key"],
            secure=MINIO_CONFIG["secure"],
        )
        self.mongo = MongoClient(MONGO_URI)
        self.db = self.mongo.get_default_database()
        self.password_hash = bcrypt.hashpw(DEFAULT_PASSWORD.encode(), bcrypt.gensalt(rounds=10)).decode()
        self.stats = {
            "users": 0,
            "spaces": 0,
            "notebooks": 0,
            "notes": 0,
            "md_notes": 0,
            "pdf_notes": 0,
            "questions": 0,
            "answers": 0,
            "comments": 0,
            "replies": 0,
            "follows": 0,
            "deepseek_calls": 0,
        }

    def close(self) -> None:
        self.conn.close()
        self.mongo.close()

    def ensure_bucket(self) -> None:
        bucket = MINIO_CONFIG["bucket"]
        if not self.minio.bucket_exists(bucket):
            self.minio.make_bucket(bucket)

    def fetch_seed_user_ids(self) -> list[int]:
        with self.conn.cursor() as cur:
            cur.execute("SELECT id FROM users WHERE email LIKE %s", ("%@seed.local",))
            return [row[0] for row in cur.fetchall()]

    def reset_seed_data(self) -> None:
        user_ids = self.fetch_seed_user_ids()
        if user_ids:
            placeholders = ",".join(["%s"] * len(user_ids))
            with self.conn.cursor() as cur:
                cur.execute(f"DELETE FROM users WHERE id IN ({placeholders})", user_ids)
            self.conn.commit()
            self.db.questions.delete_many({"authorId": {"$in": user_ids}})
            print(f"Removed {len(user_ids)} existing seed users and related data.")

    def get_or_create_tag(self, cur: pymysql.cursors.Cursor, name: str) -> int:
        cur.execute("SELECT id FROM tags WHERE name = %s", (name,))
        row = cur.fetchone()
        if row:
            return row[0]
        cur.execute("INSERT INTO tags (name) VALUES (%s)", (name,))
        return cur.lastrowid

    def upload_bytes(self, data: bytes, original_name: str, content_type: str) -> str:
        object_name = minio_object_name(original_name)
        self.minio.put_object(
            MINIO_CONFIG["bucket"],
            object_name,
            io.BytesIO(data),
            length=len(data),
            content_type=content_type,
        )
        return object_name

    def create_user(self, cur: pymysql.cursors.Cursor, idx: int) -> tuple[int, str]:
        username = f"cs_user_{idx:03d}"
        email = f"cs_user_{idx:03d}@seed.local"
        student_number = f"2026{idx:04d}"
        cur.execute(
            """
            INSERT INTO users (username, password_hash, enabled, role, studentNumber, email)
            VALUES (%s, %s, 1, 'User', %s, %s)
            """,
            (username, self.password_hash, student_number, email),
        )
        return cur.lastrowid, username

    def create_space(self, cur: pymysql.cursors.Cursor, user_id: int, tag_id: int, name: str) -> int:
        now = datetime.now()
        cur.execute(
            """
            INSERT INTO note_spaces (name, user_id, tag_id, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s)
            """,
            (name, user_id, tag_id, now, now),
        )
        return cur.lastrowid

    def create_notebook(self, cur: pymysql.cursors.Cursor, space_id: int, tag_id: int, name: str) -> int:
        now = datetime.now()
        cur.execute(
            """
            INSERT INTO notebooks (name, space_id, tag_id, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s)
            """,
            (name, space_id, tag_id, now, now),
        )
        return cur.lastrowid

    def create_note(
        self,
        cur: pymysql.cursors.Cursor,
        title: str,
        notebook_id: int,
        file_type: str,
        filename: str,
    ) -> int:
        now = datetime.now() - timedelta(hours=random.randint(1, 720))
        cur.execute(
            """
            INSERT INTO notes (title, filename, notebook_id, file_type, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s, %s)
            """,
            (title, filename, notebook_id, file_type, now, now),
        )
        return cur.lastrowid

    def create_note_stats(self, cur: pymysql.cursors.Cursor, note_id: int, author_name: str, note_created_at: datetime) -> None:
        cur.execute(
            """
            INSERT INTO note_stats
            (note_id, author_name, views, likes, favorites, comments, last_activity_at, version, updated_at)
            VALUES (%s, %s, 0, 0, 0, 0, %s, 0, %s)
            """,
            (note_id, author_name, note_created_at, note_created_at),
        )

    def create_follows(self, cur: pymysql.cursors.Cursor, user_ids: list[int]) -> None:
        now = datetime.now()
        for follower in user_ids:
            targets = {
                user_ids[(follower + 7) % len(user_ids)],
                user_ids[(follower + 13) % len(user_ids)],
                user_ids[(follower + 19) % len(user_ids)],
            }
            targets.discard(follower)
            for followee in targets:
                cur.execute(
                    """
                    INSERT IGNORE INTO user_follow (follower_id, followee_id, follow_time)
                    VALUES (%s, %s, %s)
                    """,
                    (follower, followee, now),
                )
                if cur.rowcount:
                    self.stats["follows"] += 1

    def create_questions(self, user_ids: list[int], usernames: dict[int, str]) -> None:
        for idx, user_id in enumerate(user_ids, start=1):
            username = usernames[user_id]
            for q_idx in range(QUESTIONS_PER_USER):
                domain = DOMAINS[(idx + q_idx) % len(DOMAINS)]
                topic_title, topic_body = domain["topics"][q_idx % len(domain["topics"])]
                if self.deepseek is not None:
                    self.question_generation_index += 1
                    print(
                        f"Generating question {self.question_generation_index}/{self.total_question_generations}: "
                        f"{domain['label']} / {topic_title}",
                        flush=True,
                    )
                    title, content = self.deepseek.generate_question(
                        domain_label=domain["label"],
                        topic_title=topic_title,
                        username=username,
                    )
                    self.stats["deepseek_calls"] += 1
                else:
                    title = f"【{domain['label']}】{topic_title} 相关问题"
                    content = (
                        f"我在学习 {domain['label']} 时遇到一个问题：{topic_title}。\n\n"
                        f"背景：{topic_body}\n\n"
                        f"想请教大家：在实际项目中应如何理解并应用这些概念？"
                    )
                self.db.questions.insert_one(
                    {
                        "questionId": str(uuid.uuid4()),
                        "authorId": user_id,
                        "title": title,
                        "content": content,
                        "tags": [domain["tag"], domain["label"], "seed"],
                        "createdAt": datetime.now(LOCAL_TZ) - timedelta(days=random.randint(1, 60)),
                        "likes": random.sample(user_ids, k=min(3, len(user_ids))),
                        "favorites": random.sample(user_ids, k=min(2, len(user_ids))),
                        "answers": [],
                    }
                )
                self.stats["questions"] += 1

    def seed_user(self, cur: pymysql.cursors.Cursor, idx: int) -> int:
        user_id, username = self.create_user(cur, idx)
        self.stats["users"] += 1

        primary_domain = DOMAINS[(idx - 1) % len(DOMAINS)]
        secondary_domain = DOMAINS[(idx + 3) % len(DOMAINS)]
        tag_id = self.get_or_create_tag(cur, primary_domain["tag"])

        space_name = f"{username} 的{primary_domain['label']}空间"
        space_id = self.create_space(cur, user_id, tag_id, space_name)
        self.stats["spaces"] += 1

        notebook_defs = [
            (primary_domain, f"{primary_domain['label']}基础"),
            (secondary_domain, f"{secondary_domain['label']}进阶"),
        ]
        notebooks: list[tuple[int, dict[str, Any]]] = []
        for domain, nb_name in notebook_defs:
            nb_tag = self.get_or_create_tag(cur, domain["tag"])
            nb_id = self.create_notebook(cur, space_id, nb_tag, nb_name)
            notebooks.append((nb_id, domain))
            self.stats["notebooks"] += 1

        note_specs: list[tuple[dict[str, Any], str, str, str]] = []
        for note_idx in range(NOTES_PER_USER):
            domain = DOMAINS[(idx - 1 + note_idx) % len(DOMAINS)]
            topic_title, topic_body = domain["topics"][note_idx % len(domain["topics"])]
            file_type = "md" if note_idx % 2 == 0 else "pdf"
            note_specs.append((domain, topic_title, topic_body, file_type))

        for note_idx, spec in enumerate(note_specs):
            domain, topic_title, topic_body, file_type = spec
            notebook_id = notebooks[note_idx % len(notebooks)][0]
            title = f"{domain['label']}：{topic_title}"

            if self.deepseek is not None:
                self.note_generation_index += 1
                print(
                    f"Generating note {self.note_generation_index}/{self.total_note_generations}: {title}",
                    flush=True,
                )
                markdown = self.deepseek.generate_note_markdown(
                    title=title,
                    domain_label=domain["label"],
                    topic_title=topic_title,
                    username=username,
                )
                self.stats["deepseek_calls"] += 1
            else:
                paragraphs = [
                    topic_body,
                    f"该主题属于 {domain['label']}，适合作为课程复习与项目实践参考。",
                    "建议结合实验、开源项目和真题进一步巩固。",
                ]
                markdown = build_markdown(title, domain["label"], username, paragraphs)

            if file_type == "MD":
                data = markdown.encode("utf-8")
                filename = self.upload_bytes(data, f"{title}.md", "text/markdown")
                self.stats["md_notes"] += 1
            else:
                pdf_paragraphs = markdown_to_pdf_paragraphs(markdown) if markdown_to_pdf_paragraphs else [title]
                data = build_pdf_bytes(title, pdf_paragraphs)
                filename = self.upload_bytes(data, f"{title}.pdf", "application/pdf")
                self.stats["pdf_notes"] += 1

            note_id = self.create_note(cur, title, notebook_id, file_type, filename)
            cur.execute("SELECT created_at FROM notes WHERE id = %s", (note_id,))
            note_created_at = cur.fetchone()[0]
            self.create_note_stats(cur, note_id, username, note_created_at)
            self.stats["notes"] += 1

        return user_id

    def run(self) -> None:
        self.ensure_bucket()
        if self.reset:
            self.reset_seed_data()

        existing = self.fetch_seed_user_ids()
        if existing and not self.reset:
            print(f"Seed users already exist ({len(existing)}). Use --reset to recreate.")
            return

        user_ids: list[int] = []
        usernames: dict[int, str] = {}

        with self.conn.cursor() as cur:
            for idx in range(1, self.user_count + 1):
                user_id = self.seed_user(cur, idx)
                user_ids.append(user_id)
                usernames[user_id] = f"cs_user_{idx:03d}"
                print(f"Seeded user {idx}/{self.user_count} (id={user_id})", flush=True)
            self.create_follows(cur, user_ids)
            self.conn.commit()

        self.create_questions(user_ids, usernames)

        answer_stats = seed_answers_for_questions(
            self.db,
            user_ids,
            deepseek=self.deepseek,
        )
        self.stats["answers"] = answer_stats["answers"]
        self.stats["comments"] = answer_stats["comments"]
        self.stats["replies"] = answer_stats["replies"]
        self.stats["deepseek_calls"] += answer_stats["deepseek_calls"]

        print("\nBuilding note engagement (likes/favorites/views/comments)...", flush=True)
        engagement_stats = rebuild_all_engagement(
            self.conn,
            self.db,
            redis_client=None,
            deepseek=self.deepseek,
        )
        self.stats["engagement_notes"] = engagement_stats["notes"]
        self.stats["engagement_likes"] = engagement_stats["likes"]
        self.stats["engagement_favorites"] = engagement_stats["favorites"]
        self.stats["engagement_remarks"] = engagement_stats["remarks"]

        store_stats: dict[str, Any] = {}
        if not self.skip_stores:
            print("\nPopulating Kafka / Redis / Milvus / offline parquet...", flush=True)
            store_stats = seed_recommendation_stores(
                self.conn,
                self.db,
                self.repo_root,
                skip_kafka=self.skip_kafka,
            )
        else:
            print("\nSkipped recommendation stores (--skip-stores).", flush=True)

        try:
            from seed_elasticsearch import main as index_elasticsearch

            print("\nIndexing seed notes/questions into Elasticsearch...", flush=True)
            index_elasticsearch()
        except Exception as exc:
            print(f"\nWarning: Elasticsearch indexing skipped/failed: {exc}", flush=True)

        print("\nSeed completed:")
        for key, value in self.stats.items():
            print(f"  {key}: {value}")
        print("\nRecommendation stores:")
        for key, value in store_stats.items():
            print(f"  {key}: {value}")
        print(f"\nDefault password for all seed users: {DEFAULT_PASSWORD}")
        print("Example login email: cs_user_001@seed.local")


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed CS demo users and notes")
    parser.add_argument("--reset", action="store_true", help="Delete existing @seed.local users before seeding")
    parser.add_argument("--skip-kafka", action="store_true", help="Skip Kafka publish, still write parquet/redis/milvus")
    parser.add_argument(
        "--content-source",
        choices=["deepseek", "template"],
        default="deepseek",
        help="Note/QA content source (default: deepseek)",
    )
    parser.add_argument("--refresh-cache", action="store_true", help="Ignore DeepSeek cache and regenerate content")
    parser.add_argument("--limit-users", type=int, default=None, help="Only seed first N users (for testing)")
    parser.add_argument("--skip-stores", action="store_true", help="Skip Kafka/Redis/Milvus/parquet after content seed")
    parser.add_argument("--skip-ping", action="store_true", help="Skip DeepSeek connectivity test before seeding")
    args = parser.parse_args()

    seeder = Seeder(
        reset=args.reset,
        skip_kafka=args.skip_kafka,
        content_source=args.content_source,
        refresh_cache=args.refresh_cache,
        limit_users=args.limit_users,
        skip_stores=args.skip_stores,
        skip_ping=args.skip_ping,
    )
    try:
        seeder.run()
    finally:
        seeder.close()


if __name__ == "__main__":
    main()
