#!/usr/bin/env python3
"""Automated local recommendation loop for sample->train->eval->export->rollout."""

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any

import pandas as pd
import yaml
from mlflow import MlflowClient


def run_cmd(args: list[str]) -> None:
    cmd = [sys.executable if arg == "python" else arg for arg in args]
    print(">>>", " ".join(cmd))
    subprocess.check_call(cmd)


def load_yaml(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    return yaml.safe_load(path.read_text(encoding="utf-8")) or {}


def load_json(path: Path, default: dict[str, Any]) -> dict[str, Any]:
    if not path.exists():
        return default
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return default


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def sample_gate(samples_path: Path, gate_cfg: dict[str, Any]) -> tuple[bool, dict[str, Any]]:
    df = pd.read_parquet(samples_path)
    total = len(df)
    positive = int(df["label"].sum()) if "label" in df.columns else 0
    positive_rate = (positive / total) if total else 0.0
    sample_cfg = gate_cfg.get("sample", {})
    min_sample_count = int(sample_cfg.get("min_sample_count", 500))
    min_positive_count = int(sample_cfg.get("min_positive_count", 50))
    min_positive_rate = float(sample_cfg.get("min_positive_rate", 0.01))
    max_positive_rate = float(sample_cfg.get("max_positive_rate", 0.80))
    passed = (
        total >= min_sample_count
        and positive >= min_positive_count
        and min_positive_rate <= positive_rate <= max_positive_rate
    )
    return passed, {
        "sample_count": total,
        "positive_count": positive,
        "positive_rate": positive_rate,
        "min_sample_count": min_sample_count,
        "min_positive_count": min_positive_count,
        "min_positive_rate": min_positive_rate,
        "max_positive_rate": max_positive_rate,
    }


def latest_model_version(client: MlflowClient, model_name: str) -> str | None:
    versions = client.search_model_versions(f"name='{model_name}'")
    if not versions:
        return None
    versions_sorted = sorted(versions, key=lambda v: int(v.version), reverse=True)
    return versions_sorted[0].version


def set_stage(client: MlflowClient, model_name: str, version: str, stage: str) -> None:
    client.transition_model_version_stage(
        name=model_name,
        version=version,
        stage=stage,
        archive_existing_versions=False,
    )


def next_rollout_ratio(current: float, progression: list[float]) -> float:
    for ratio in progression:
        if ratio > current + 1e-9:
            return ratio
    return current


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tracking-uri", default="http://localhost:5001")
    parser.add_argument("--model-name", default="recommend_coarse_rank")
    parser.add_argument("--model-type", default="lightgbm", choices=["lightgbm", "logistic"])
    parser.add_argument("--samples", default="data/samples.parquet")
    parser.add_argument("--gates", default="recommendation_offline/automation/gates.yaml")
    parser.add_argument("--state", default="data/pipeline/pipeline_state.json")
    parser.add_argument("--rollout-state", default="data/pipeline/rollout_state.json")
    parser.add_argument("--eval-report", default="data/pipeline/evaluation_report.json")
    parser.add_argument("--online-metrics", default="data/pipeline/online_metrics.json")
    parser.add_argument("--run-export", action="store_true", help="Run mysql/kafka export before training")
    args = parser.parse_args()

    gates = load_yaml(Path(args.gates))
    pipeline_state = load_json(Path(args.state), default={})
    rollout_state = load_json(
        Path(args.rollout_state),
        default={
            "enabled": True,
            "experimentId": "recommend_rank_v1",
            "controlVariant": "control_rule",
            "treatmentVariant": "treatment_model",
            "modelRankRatio": 0.0,
            "modelEnabled": False,
            "modelVersion": "pending",
            "previousModelVersion": None,
        },
    )

    try:
        if args.run_export:
            run_cmd(["python", "recommendation_offline/spark/export_mysql_tables.py"])
            run_cmd(["python", "recommendation_offline/spark/export_kafka_events.py"])
        run_cmd(["python", "recommendation_offline/spark/build_training_samples.py"])

        samples_ok, sample_info = sample_gate(Path(args.samples), gates)
        pipeline_state["sample_gate"] = sample_info
        if not samples_ok:
            pipeline_state["status"] = "sample_gate_failed"
            write_json(Path(args.state), pipeline_state)
            print("sample gate failed, skip training")
            return

        run_cmd(
            [
                "python",
                "recommendation_offline/mlflow/train_and_register.py",
                "--tracking-uri",
                args.tracking_uri,
                "--model-name",
                args.model_name,
                "--model-type",
                args.model_type,
                "--samples",
                args.samples,
            ]
        )

        client = MlflowClient(tracking_uri=args.tracking_uri)
        version = latest_model_version(client, args.model_name)
        if version is None:
            raise RuntimeError("no registered model version found after training")
        set_stage(client, args.model_name, version, "Staging")

        eval_cfg = gates.get("evaluation", {})
        min_auc = float(eval_cfg.get("min_auc", 0.60))
        min_pr_auc = float(eval_cfg.get("min_pr_auc", 0.15))
        run_cmd(
            [
                "python",
                "recommendation_offline/mlflow/evaluate_model.py",
                "--tracking-uri",
                args.tracking_uri,
                "--model-name",
                args.model_name,
                "--candidate-stage",
                "Staging",
                "--production-stage",
                "Production",
                "--samples",
                args.samples,
                "--report",
                args.eval_report,
                "--model-type",
                args.model_type,
                "--min-auc",
                str(min_auc),
                "--min-pr-auc",
                str(min_pr_auc),
            ]
        )
        eval_report = load_json(Path(args.eval_report), default={"passed": False})
        if not eval_report.get("passed", False):
            pipeline_state["status"] = "evaluation_failed"
            pipeline_state["evaluation_report"] = eval_report
            write_json(Path(args.state), pipeline_state)
            print("evaluation failed, rollback rollout ratio")
            rollout_cfg = gates.get("rollout", {})
            rollback_ratio = float(rollout_cfg.get("rollback_ratio", 0.0))
            rollout_state["modelRankRatio"] = rollback_ratio
            rollout_state["modelEnabled"] = rollback_ratio > 0
            write_json(Path(args.rollout_state), rollout_state)
            return

        run_cmd(
            [
                "python",
                "recommendation_offline/mlflow/promote_and_export.py",
                "--tracking-uri",
                args.tracking_uri,
                "--model-name",
                args.model_name,
                "--version",
                version,
                "--target-stage",
                "Production",
                "--model-type",
                args.model_type,
                "--output",
                "Login_api/models/recommend_coarse_rank.onnx",
            ]
        )

        online_metrics = load_json(Path(args.online_metrics), default={})
        current_error_rate = float(online_metrics.get("error_rate", 0.0))
        rollout_cfg = gates.get("rollout", {})
        max_error_rate = float(rollout_cfg.get("max_error_rate", 0.05))
        if current_error_rate > max_error_rate:
            pipeline_state["status"] = "online_metrics_failed"
            rollout_state["modelRankRatio"] = float(rollout_cfg.get("rollback_ratio", 0.0))
            rollout_state["modelEnabled"] = rollout_state["modelRankRatio"] > 0
            write_json(Path(args.rollout_state), rollout_state)
            write_json(Path(args.state), pipeline_state)
            print("online metrics failed, rollback ratio")
            return

        progression = [float(v) for v in rollout_cfg.get("progression", [0.0, 0.05, 0.2, 0.5, 1.0])]
        current_ratio = float(rollout_state.get("modelRankRatio", 0.0))
        rollout_state["previousModelVersion"] = rollout_state.get("modelVersion")
        rollout_state["modelVersion"] = version
        rollout_state["modelRankRatio"] = next_rollout_ratio(current_ratio, progression)
        rollout_state["modelEnabled"] = rollout_state["modelRankRatio"] > 0
        write_json(Path(args.rollout_state), rollout_state)

        pipeline_state["status"] = "success"
        pipeline_state["model_version"] = version
        pipeline_state["evaluation_report"] = eval_report
        pipeline_state["rollout_ratio"] = rollout_state["modelRankRatio"]
        write_json(Path(args.state), pipeline_state)
        print("automation pipeline finished successfully")
    except Exception as ex:
        pipeline_state["status"] = "failed"
        pipeline_state["error"] = str(ex)
        write_json(Path(args.state), pipeline_state)
        raise


if __name__ == "__main__":
    main()
