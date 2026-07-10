#!/usr/bin/env python3
"""Evaluate candidate model and emit gate report."""

import argparse
import json
from pathlib import Path

import numpy as np
import mlflow
import mlflow.lightgbm
import mlflow.sklearn
import pandas as pd
from mlflow import MlflowClient
from sklearn.metrics import average_precision_score
from sklearn.metrics import log_loss
from sklearn.metrics import roc_auc_score

FEATURE_COLUMNS = [
    "views",
    "likes",
    "favorites",
    "comments",
    "answers",
    "tag_match_score",
    "recall_score",
]


def _predict_prob(model, x: pd.DataFrame, model_type: str):
    if model_type == "lightgbm":
        if hasattr(model, "predict_proba"):
            return model.predict_proba(x)[:, 1]
        raw = np.asarray(model.predict(x), dtype=float)
        if raw.max() <= 1.0 and raw.min() >= 0.0:
            return raw
        return 1.0 / (1.0 + np.exp(-raw))
    return model.predict_proba(x)[:, 1]


def _metrics(y_true, prob):
    prob = np.clip(np.asarray(prob, dtype=float), 1e-7, 1 - 1e-7)
    return {
        "auc": float(roc_auc_score(y_true, prob)),
        "pr_auc": float(average_precision_score(y_true, prob)),
        "log_loss": float(log_loss(y_true, prob)),
    }


def _load_stage_model(tracking_uri: str, model_name: str, stage: str, model_type: str):
    uri = f"models:/{model_name}/{stage}"
    if model_type == "lightgbm":
        return mlflow.lightgbm.load_model(uri)
    return mlflow.sklearn.load_model(uri)


def evaluate(
    tracking_uri: str,
    model_name: str,
    candidate_stage: str,
    production_stage: str,
    samples_path: Path,
    report_path: Path,
    model_type: str,
    min_auc: float,
    min_pr_auc: float,
) -> None:
    mlflow.set_tracking_uri(tracking_uri)
    client = MlflowClient(tracking_uri=tracking_uri)
    df = pd.read_parquet(samples_path)
    for col in FEATURE_COLUMNS:
        if col not in df.columns:
            df[col] = 0.0
    x = df[FEATURE_COLUMNS]
    y = df["label"]

    candidate = _load_stage_model(tracking_uri, model_name, candidate_stage, model_type)
    cand_prob = _predict_prob(candidate, x, model_type)
    candidate_metrics = _metrics(y, cand_prob)
    rule_baseline = _metrics(y, x["recall_score"].clip(0.0, 1.0))

    production_metrics = None
    production_available = False
    try:
        versions = client.get_latest_versions(model_name, [production_stage])
        production_available = bool(versions)
        if production_available:
            production = _load_stage_model(tracking_uri, model_name, production_stage, model_type)
            production_prob = _predict_prob(production, x, model_type)
            production_metrics = _metrics(y, production_prob)
    except Exception:
        production_available = False

    reasons = []
    passed = True
    if candidate_metrics["auc"] < min_auc:
        passed = False
        reasons.append(f"auc<{min_auc}")
    if candidate_metrics["pr_auc"] < min_pr_auc:
        passed = False
        reasons.append(f"pr_auc<{min_pr_auc}")
    if candidate_metrics["auc"] < rule_baseline["auc"]:
        passed = False
        reasons.append("auc<rule_baseline")
    if production_metrics and candidate_metrics["auc"] < production_metrics["auc"]:
        passed = False
        reasons.append("auc<production")

    report = {
        "passed": passed,
        "reasons": reasons,
        "sample_count": int(len(df)),
        "positive_rate": float(y.mean()),
        "candidate_stage": candidate_stage,
        "production_stage": production_stage,
        "production_available": production_available,
        "model_type": model_type,
        "candidate_metrics": candidate_metrics,
        "rule_baseline_metrics": rule_baseline,
        "production_metrics": production_metrics,
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"wrote evaluation report to {report_path}")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tracking-uri", default="http://localhost:5001")
    parser.add_argument("--model-name", default="recommend_coarse_rank")
    parser.add_argument("--candidate-stage", default="Staging")
    parser.add_argument("--production-stage", default="Production")
    parser.add_argument("--samples", default="data/samples.parquet")
    parser.add_argument("--report", default="data/pipeline/evaluation_report.json")
    parser.add_argument("--model-type", default="lightgbm", choices=["lightgbm", "logistic"])
    parser.add_argument("--min-auc", type=float, default=0.60)
    parser.add_argument("--min-pr-auc", type=float, default=0.15)
    args = parser.parse_args()
    evaluate(
        args.tracking_uri,
        args.model_name,
        args.candidate_stage,
        args.production_stage,
        Path(args.samples),
        Path(args.report),
        args.model_type,
        args.min_auc,
        args.min_pr_auc,
    )
