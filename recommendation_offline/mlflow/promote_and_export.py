#!/usr/bin/env python3
"""Promote a model version stage and export ONNX artifact."""

import argparse
import subprocess

from mlflow import MlflowClient


def promote(tracking_uri: str, model_name: str, version: str, stage: str) -> None:
    client = MlflowClient(tracking_uri=tracking_uri)
    client.transition_model_version_stage(
        name=model_name,
        version=version,
        stage=stage,
        archive_existing_versions=False,
    )
    print(f"promoted {model_name} version={version} to stage={stage}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tracking-uri", default="http://localhost:5001")
    parser.add_argument("--model-name", default="recommend_coarse_rank")
    parser.add_argument("--version", required=True)
    parser.add_argument("--target-stage", default="Staging")
    parser.add_argument("--model-type", default="lightgbm", choices=["lightgbm", "logistic"])
    parser.add_argument("--output", default="Login_api/models/recommend_coarse_rank.onnx")
    args = parser.parse_args()

    promote(args.tracking_uri, args.model_name, args.version, args.target_stage)
    subprocess.check_call(
        [
            "python",
            "recommendation_offline/mlflow/export_onnx.py",
            "--tracking-uri",
            args.tracking_uri,
            "--model-name",
            args.model_name,
            "--model-stage",
            args.target_stage,
            "--model-type",
            args.model_type,
            "--output",
            args.output,
        ]
    )
