#!/usr/bin/env python3
"""Re-upload seed PDF notes in MinIO using cached Markdown + CJK PDF builder."""

from __future__ import annotations

import io
import sys
from pathlib import Path

import pymysql
from minio import Minio

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from pdf_builder import build_pdf_bytes, paragraphs_from_markdown

MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "ebook_admin",
    "password": "ebook_123456",
    "database": "ebook_platform",
    "charset": "utf8mb4",
}

MINIO_CONFIG = {
    "endpoint": "localhost:9000",
    "access_key": "name",
    "secret_key": "password",
    "bucket": "notesharing",
    "secure": False,
}

CACHE_DIR = SCRIPT_DIR / "cache" / "notes"


def load_markdown_by_title(title: str) -> str | None:
    if not CACHE_DIR.exists():
        return None
    needle = f"# {title}"
    for path in CACHE_DIR.glob("*.md"):
        text = path.read_text(encoding="utf-8")
        if text.startswith(needle) or needle in text[:200]:
            return text
    return None


def main() -> None:
    conn = pymysql.connect(**MYSQL_CONFIG)
    minio = Minio(
        MINIO_CONFIG["endpoint"],
        access_key=MINIO_CONFIG["access_key"],
        secret_key=MINIO_CONFIG["secret_key"],
        secure=MINIO_CONFIG["secure"],
    )
    bucket = MINIO_CONFIG["bucket"]

    sql = """
        SELECT n.id, n.title, n.filename
        FROM notes n
        JOIN notebooks nb ON n.notebook_id = nb.id
        JOIN note_spaces sp ON nb.space_id = sp.id
        JOIN users u ON sp.user_id = u.id
        WHERE u.email LIKE %s AND n.file_type = 'pdf' AND n.filename IS NOT NULL
        ORDER BY n.id
    """
    repaired = 0
    missing_cache = 0
    with conn.cursor(pymysql.cursors.DictCursor) as cur:
        cur.execute(sql, ("%@seed.local",))
        rows = cur.fetchall()
        print(f"Found {len(rows)} seed PDF notes")
        for row in rows:
            title = row["title"]
            filename = row["filename"]
            markdown = load_markdown_by_title(title)
            if not markdown:
                missing_cache += 1
                markdown = f"# {title}\n\n该笔记为种子数据 PDF，正文缓存未找到。"
            paragraphs = paragraphs_from_markdown(markdown)
            pdf_bytes = build_pdf_bytes(title, paragraphs)
            minio.put_object(
                bucket,
                filename,
                io.BytesIO(pdf_bytes),
                length=len(pdf_bytes),
                content_type="application/pdf",
            )
            repaired += 1
            print(f"repaired note_id={row['id']} file={filename}")

    conn.close()
    print(f"done: repaired={repaired}, missing_cache={missing_cache}")


if __name__ == "__main__":
    main()
