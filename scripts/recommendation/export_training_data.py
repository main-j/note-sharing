#!/usr/bin/env python3
"""Run MySQL/Kafka export using credentials from application.yml."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import quote_plus

ROOT = Path(__file__).resolve().parents[2]


def mysql_url_from_yml() -> str:
    text = (ROOT / "Login_api" / "src" / "main" / "resources" / "application.yml").read_text(
        encoding="utf-8"
    )
    host, port, database = re.search(
        r"url:\s*jdbc:mysql://([^:/]+):(\d+)/([^?]+)", text
    ).groups()
    user = re.search(r"username:\s*(\S+)", text).group(1)
    password = re.search(r"password:\s*(\S+)", text).group(1)
    return (
        f"mysql+pymysql://{quote_plus(user)}:{quote_plus(password)}"
        f"@{host}:{port}/{database}"
    )


def main() -> int:
    mysql_url = mysql_url_from_yml()
    cmds = [
        [
            sys.executable,
            str(ROOT / "recommendation_offline" / "spark" / "export_mysql_tables.py"),
            "--mysql-url",
            mysql_url,
            "--output-dir",
            str(ROOT / "data" / "raw"),
        ],
        [
            sys.executable,
            str(ROOT / "recommendation_offline" / "spark" / "export_kafka_events.py"),
            "--output",
            str(ROOT / "data" / "raw" / "events.parquet"),
        ],
    ]
    for cmd in cmds:
        print(">>>", " ".join(cmd[:2]), "...", flush=True)
        subprocess.check_call(cmd, cwd=ROOT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
