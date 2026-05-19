#!/usr/bin/env python3
"""Train coarse rank model and register in MLflow Model Registry."""

import argparse
from pathlib import Path

import mlflow
import mlflow.sklearn
import mlflow.lightgbm
import pandas as pd
import lightgbm as lgb
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score
from sklearn.metrics import roc_auc_score
from sklearn.metrics import log_loss
from sklearn.model_selection import train_test_split

FEATURE_COLUMNS = [
    "views",
    "likes",
    "favorites",
    "comments",
    "answers",
    "tag_match_score",
    "recall_score",
]

def train_lightgbm(x_train: pd.DataFrame, y_train: pd.Series):
    model = lgb.LGBMClassifier(
        objective="binary",
        n_estimators=300,
        learning_rate=0.05,
        num_leaves=63,
        subsample=0.9,
        colsample_bytree=0.9,
        random_state=42,
    )
    model.fit(x_train, y_train)
    return model


def train_logistic(x_train: pd.DataFrame, y_train: pd.Series):
    model = LogisticRegression(max_iter=300)
    model.fit(x_train, y_train)
    return model


def main(samples_path: Path, tracking_uri: str, model_name: str, model_type: str) -> None:
    mlflow.set_tracking_uri(tracking_uri)
    df = pd.read_parquet(samples_path)
    for col in FEATURE_COLUMNS:
        if col not in df.columns:
            df[col] = 0.0
    x = df[FEATURE_COLUMNS]
    y = df["label"]
    if y.nunique() < 2:
        raise ValueError("labels must contain both positive and negative samples")
    x_train, x_test, y_train, y_test = train_test_split(x, y, test_size=0.2, random_state=42)

    with mlflow.start_run(run_name=f"recommend_coarse_rank_{model_type}"):
        model = train_lightgbm(x_train, y_train) if model_type == "lightgbm" else train_logistic(x_train, y_train)
        prob = model.predict_proba(x_test)[:, 1]
        auc = roc_auc_score(y_test, prob)
        pr_auc = average_precision_score(y_test, prob)
        loss = log_loss(y_test, prob)
        mlflow.log_metric("auc", auc)
        mlflow.log_metric("pr_auc", pr_auc)
        mlflow.log_metric("log_loss", loss)
        mlflow.log_metric("sample_count", float(len(df)))
        mlflow.log_metric("positive_rate", float(y.mean()))
        mlflow.log_param("feature_columns", ",".join(FEATURE_COLUMNS))
        mlflow.log_param("model_type", model_type)
        if model_type == "lightgbm":
            model_info = mlflow.lightgbm.log_model(model.booster_, artifact_path="model")
        else:
            model_info = mlflow.sklearn.log_model(model, artifact_path="model")
        registered = mlflow.register_model(model_uri=model_info.model_uri, name=model_name)
        run_id = getattr(model_info, "run_id", mlflow.active_run().info.run_id)
        print(f"run_id={run_id}")
        print(
            f"registered_model={registered.name} version={registered.version} "
            f"model_type={model_type} auc={auc:.4f} pr_auc={pr_auc:.4f} log_loss={loss:.4f}"
        )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--samples", default="data/samples.parquet")
    parser.add_argument("--tracking-uri", default="http://localhost:5001")
    parser.add_argument("--model-name", default="recommend_coarse_rank")
    parser.add_argument("--model-type", default="lightgbm", choices=["lightgbm", "logistic"])
    args = parser.parse_args()
    main(Path(args.samples), args.tracking_uri, args.model_name, args.model_type)
