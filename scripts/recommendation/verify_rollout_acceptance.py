#!/usr/bin/env python3
"""Verify recommendation rollout acceptance (phase 6/7).

Checks Milvus, Feast, model inference backend, rollout ratio, feed stability,
and Kafka exposure write-back after sample feed requests.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_STATE = ROOT / "data" / "pipeline" / "rollout_state.json"
DEFAULT_APP_YML = ROOT / "Login_api" / "src" / "main" / "resources" / "application.yml"


def http_json(method: str, url: str, body: dict | None = None, timeout: float = 8.0) -> tuple[int, dict | list | str]:
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            try:
                return resp.status, json.loads(raw)
            except json.JSONDecodeError:
                return resp.status, raw
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            return exc.code, json.loads(raw)
        except json.JSONDecodeError:
            return exc.code, raw


def load_rollout_state(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def parse_simple_yaml_flags(path: Path) -> dict[str, bool]:
    flags: dict[str, bool] = {}
    current: str | None = None
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        for key in ("milvus", "feast", "onnx", "triton"):
            if stripped.startswith(f"{key}:"):
                current = key
                break
        if current and stripped.startswith("enabled:"):
            value = stripped.split(":", 1)[1].strip()
            if value in ("true", "false"):
                flags[current] = value == "true"
    return flags


def check_milvus(host: str, port: int, collection: str) -> tuple[bool, str]:
    try:
        from pymilvus import MilvusClient
    except ImportError:
        return False, "pymilvus not installed (pip install pymilvus)"
    try:
        client = MilvusClient(uri=f"http://{host}:{port}")
        names = client.list_collections()
        if collection not in names:
            return False, f"collection missing: {collection} (found {names})"
        return True, f"collection {collection} present"
    except Exception as exc:  # noqa: BLE001
        return False, str(exc)


def check_feast(server_url: str) -> tuple[bool, str]:
    status, payload = http_json(
        "POST",
        f"{server_url.rstrip('/')}/get-online-features",
        {
            "features": ["user_features:top_tag_1", "item_features:views"],
            "entities": {"user_id": [1], "item_id": [334]},
        },
    )
    if status != 200 or not isinstance(payload, dict):
        return False, f"feast call failed: HTTP {status}"
    names = payload.get("metadata", {}).get("feature_names", [])
    if "top_tag_1" not in names or "views" not in names:
        return False, f"unexpected feature names: {names}"
    return True, "user/item online features reachable"


def check_model_backend(flags: dict, triton_url: str, onnx_path: Path) -> tuple[bool, str]:
    onnx_on = flags.get("onnx", False)
    triton_on = flags.get("triton", False)
    if onnx_on and triton_on:
        return False, "both onnx.enabled and triton.enabled are true"
    if triton_on:
        status, _ = http_json("GET", f"{triton_url.rstrip('/')}/v2/models/recommend_coarse_rank/ready")
        if status != 200:
            return False, f"triton not ready: HTTP {status}"
        return True, "triton model ready"
    if onnx_on:
        if not onnx_path.is_file():
            return False, f"onnx file missing: {onnx_path}"
        return True, f"onnx file present: {onnx_path.name}"
    return False, "no model backend enabled (onnx/triton both false)"


def sample_feed(base_url: str, user_ids: list[int], page_size: int) -> tuple[list[dict], list[str]]:
    results: list[dict] = []
    errors: list[str] = []
    for user_id in user_ids:
        url = f"{base_url.rstrip('/')}/api/v1/recommend/feed?userId={user_id}&pageSize={page_size}"
        status, payload = http_json("GET", url, timeout=15.0)
        if status != 200 or not isinstance(payload, dict):
            errors.append(f"userId={user_id}: HTTP {status}")
            continue
        data = payload.get("data") or {}
        items = data.get("items") or []
        results.append(
            {
                "userId": user_id,
                "variant": data.get("variant"),
                "modelEnabled": data.get("modelEnabled"),
                "modelVersion": data.get("modelVersion"),
                "itemCount": len(items),
                "rankers": Counter(item.get("ranker") for item in items),
                "reasons": Counter(
                    token
                    for item in items
                    for token in str(item.get("reason") or "").split(",")
                    if token
                ),
            }
        )
        if not items:
            errors.append(f"userId={user_id}: empty feed")
    return results, errors


def check_kafka_exposure(kafka_container: str, topic: str, min_messages: int) -> tuple[bool, str]:
    import subprocess

    cmd = [
        "docker",
        "exec",
        kafka_container,
        "/opt/kafka/bin/kafka-console-consumer.sh",
        "--bootstrap-server",
        "localhost:9092",
        "--topic",
        topic,
        "--from-beginning",
        "--max-messages",
        str(min_messages),
        "--timeout-ms",
        "5000",
    ]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=20, check=False)
    except Exception as exc:  # noqa: BLE001
        return False, str(exc)
    lines = [line for line in proc.stdout.splitlines() if line.strip()]
    if len(lines) < 1:
        return False, f"no messages on topic {topic} (stderr: {proc.stderr.strip()[:200]})"
    parsed = 0
    for line in lines[-min_messages:]:
        try:
            json.loads(line)
            parsed += 1
        except json.JSONDecodeError:
            pass
    if parsed == 0:
        return False, f"topic {topic} has lines but none are valid JSON"
    return True, f"topic {topic}: read {parsed} JSON message(s)"


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify recommendation rollout acceptance.")
    parser.add_argument("--state-file", type=Path, default=DEFAULT_STATE)
    parser.add_argument("--app-yml", type=Path, default=DEFAULT_APP_YML)
    parser.add_argument("--api-base", default="http://localhost:8080")
    parser.add_argument("--feast-url", default="http://localhost:6566")
    parser.add_argument("--triton-url", default="http://localhost:8000")
    parser.add_argument("--milvus-host", default="localhost")
    parser.add_argument("--milvus-port", type=int, default=19530)
    parser.add_argument("--milvus-collection", default="recommend_item_vectors")
    parser.add_argument("--kafka-container", default="rec-kafka")
    parser.add_argument("--exposure-topic", default="rec_user_exposure")
    parser.add_argument("--expected-ratio", type=float, default=None, help="Expected modelRankRatio in state file")
    parser.add_argument("--sample-users", default="1,2,3,4,5,10,20,50,99")
    parser.add_argument("--page-size", type=int, default=10)
    parser.add_argument("--warmup-feed-user", type=int, default=1, help="Call feed once before Kafka check")
    args = parser.parse_args()

    checks: list[tuple[str, bool, str]] = []
    state = load_rollout_state(args.state_file)
    ratio = float(state.get("modelRankRatio", 0.0))
    flags = parse_simple_yaml_flags(args.app_yml)

    if args.expected_ratio is not None and abs(ratio - args.expected_ratio) > 1e-9:
        checks.append(("rollout ratio", False, f"expected {args.expected_ratio}, got {ratio}"))
    else:
        checks.append(("rollout ratio", True, f"modelRankRatio={ratio}, modelVersion={state.get('modelVersion')}"))

    ok, msg = check_milvus(args.milvus_host, args.milvus_port, args.milvus_collection)
    checks.append(("milvus", ok and flags.get("milvus", False), msg if ok else msg))

    ok, msg = check_feast(args.feast_url)
    checks.append(("feast", ok and flags.get("feast", False), msg))

    ok, msg = check_model_backend(flags, args.triton_url, ROOT / "Login_api" / "models" / "recommend_coarse_rank.onnx")
    checks.append(("model backend", ok, msg))

    user_ids = [int(x.strip()) for x in args.sample_users.split(",") if x.strip()]
    if args.warmup_feed_user:
        http_json(
            "GET",
            f"{args.api_base.rstrip('/')}/api/v1/recommend/feed?userId={args.warmup_feed_user}&pageSize={args.page_size}",
            timeout=15.0,
        )
        time.sleep(1.5)

    feeds, feed_errors = sample_feed(args.api_base, user_ids, args.page_size)
    treatment = sum(1 for row in feeds if row.get("modelEnabled"))
    control = sum(1 for row in feeds if not row.get("modelEnabled"))
    empty = sum(1 for row in feeds if row.get("itemCount", 0) == 0)
    feed_ok = not feed_errors and len(feeds) == len(user_ids)
    checks.append(
        (
            "feed api",
            feed_ok,
            f"samples={len(feeds)}, treatment={treatment}, control={control}, empty={empty}, errors={len(feed_errors)}",
        ),
    )

    ok, msg = check_kafka_exposure(args.kafka_container, args.exposure_topic, min_messages=1)
    checks.append(("kafka exposure", ok, msg))

    print("Rollout acceptance report")
    print("=" * 60)
    passed = 0
    for name, ok, detail in checks:
        status = "PASS" if ok else "FAIL"
        print(f"[{status}] {name}: {detail}")
        if ok:
            passed += 1
    print("-" * 60)
    if feeds:
        print("Feed samples:")
        for row in feeds[:5]:
            print(
                f"  userId={row['userId']} variant={row['variant']} "
                f"modelEnabled={row['modelEnabled']} modelVersion={row['modelVersion']} "
                f"items={row['itemCount']} rankers={dict(row['rankers'])} "
                f"vectorHits={row['reasons'].get('VECTOR', 0)}"
            )
    if feed_errors:
        print("Feed errors:")
        for err in feed_errors:
            print(f"  - {err}")

    print("-" * 60)
    print(f"{passed}/{len(checks)} checks passed")
    if ratio >= 1.0:
        print("Rollback: set data/pipeline/rollout_state.json modelRankRatio to 0.0 and modelEnabled to false.")
        print("Also disable recommendation.infra.feast/milvus/triton in application.yml if needed.")
    return 0 if passed == len(checks) else 1


if __name__ == "__main__":
    sys.exit(main())
