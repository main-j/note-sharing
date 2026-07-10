import re
from pathlib import Path

import pymysql
from pymongo import MongoClient

yml = Path(__file__).resolve().parents[2] / "Login_api" / "src" / "main" / "resources" / "application.yml"
text = yml.read_text(encoding="utf-8")
host, port, database = re.search(r"url:\s*jdbc:mysql://([^:/]+):(\d+)/([^?]+)", text).groups()
user = re.search(r"username:\s*(\S+)", text).group(1)
password = re.search(r"password:\s*(\S+)", text).group(1)

conn = pymysql.connect(
    host=host,
    port=int(port),
    user=user,
    password=password,
    database=database,
    charset="utf8mb4",
)
cur = conn.cursor()

print("=== MySQL audit ===")
cur.execute("SELECT COUNT(*) FROM user_favorite_note")
print("user_favorite_note rows:", cur.fetchone()[0])
cur.execute("SELECT COUNT(*) FROM note_stats WHERE favorites > 0")
print("note_stats favorites>0:", cur.fetchone()[0])
cur.execute("SELECT COUNT(*) FROM note_stats WHERE likes > 0")
print("note_stats likes>0:", cur.fetchone()[0])
cur.execute("SELECT COALESCE(SUM(likes),0) FROM note_stats")
print("total likes in note_stats:", cur.fetchone()[0])
cur.execute(
    """
    SELECT COUNT(*) FROM (
      SELECT ns.note_id FROM note_stats ns
      LEFT JOIN (
        SELECT note_id, COUNT(*) rel_cnt FROM user_favorite_note GROUP BY note_id
      ) r ON ns.note_id = r.note_id
      WHERE ns.favorites != COALESCE(r.rel_cnt, 0)
    ) t
    """
)
print("favorite stats vs relation mismatches:", cur.fetchone()[0])
cur.execute(
    """
    SELECT COUNT(*) FROM (
      SELECT ns.note_id FROM note_stats ns
      LEFT JOIN (
        SELECT note_id, COUNT(*) rel_cnt FROM user_like_note GROUP BY note_id
      ) r ON ns.note_id = r.note_id
      WHERE ns.likes != COALESCE(r.rel_cnt, 0)
    ) t
    """
)
print("like stats vs relation mismatches:", cur.fetchone()[0])
cur.execute(
    """
    SELECT ns.note_id, ns.likes, COALESCE(r.rel_cnt,0)
    FROM note_stats ns
    LEFT JOIN (
      SELECT note_id, COUNT(*) rel_cnt FROM user_like_note GROUP BY note_id
    ) r ON ns.note_id = r.note_id
    WHERE ns.likes != COALESCE(r.rel_cnt, 0)
    LIMIT 5
    """
)
rows = cur.fetchall()
print("sample like mismatches:", rows if rows else "none")
cur.execute(
    """
    SELECT ns.note_id, ns.favorites, COALESCE(r.rel_cnt,0)
    FROM note_stats ns
    LEFT JOIN (
      SELECT note_id, COUNT(*) rel_cnt FROM user_favorite_note GROUP BY note_id
    ) r ON ns.note_id = r.note_id
    WHERE ns.favorites != COALESCE(r.rel_cnt, 0)
    LIMIT 10
    """
)
rows = cur.fetchall()
print("sample favorite mismatches:", rows if rows else "none")
cur.execute(
    """
    SELECT COUNT(*) FROM user_favorite_note uf
    LEFT JOIN notes n ON uf.note_id = n.id
    WHERE n.id IS NULL
    """
)
print("orphan favorites (missing note):", cur.fetchone()[0])
cur.execute(
    """
    SELECT COUNT(*) FROM user_favorite_note uf
    LEFT JOIN users u ON uf.user_id = u.id
    WHERE u.id IS NULL
    """
)
print("orphan favorites (missing user):", cur.fetchone()[0])
cur.execute("SHOW TABLES LIKE 'user_like_note'")
print("user_like_note table exists:", bool(cur.fetchone()))
conn.close()

print("\n=== MongoDB remark audit ===")
mongo = MongoClient("mongodb://localhost:27017/note_db")
db = mongo.get_default_database()
remark_counts = {
    int(doc["_id"]): int(doc["count"])
    for doc in db.remark.aggregate([{"$group": {"_id": "$note_id", "count": {"$sum": 1}}}])
}
conn = pymysql.connect(
    host=host,
    port=int(port),
    user=user,
    password=password,
    database=database,
    charset="utf8mb4",
)
cur = conn.cursor()
cur.execute("SELECT note_id, comments FROM note_stats")
mismatches = []
for note_id, comments in cur.fetchall():
    actual = remark_counts.get(int(note_id), 0)
    if int(comments) != actual:
        mismatches.append((note_id, comments, actual))
print("comment stats vs remark mismatches:", len(mismatches))
print("sample comment mismatches:", mismatches[:5] if mismatches else "none")
conn.close()
mongo.close()
