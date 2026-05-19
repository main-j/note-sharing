#!/usr/bin/env python3
"""Export ONNX model from MLflow registered model (LightGBM/Sklearn)."""

import argparse
from pathlib import Path

import mlflow
import mlflow.lightgbm
import mlflow.sklearn
import onnxmltools
from onnxmltools.convert.common.data_types import FloatTensorType
from skl2onnx import convert_sklearn

FEATURE_DIM = 7

def export_model(tracking_uri: str, model_name: str, model_stage: str, output: Path, model_type: str) -> None:
    mlflow.set_tracking_uri(tracking_uri)
    model_uri = f"models:/{model_name}/{model_stage}"
    initial_types = [("input", FloatTensorType([None, FEATURE_DIM]))]
    if model_type == "lightgbm":
        booster = mlflow.lightgbm.load_model(model_uri)
        onnx_model = onnxmltools.convert_lightgbm(booster, initial_types=initial_types, target_opset=13)
    else:
        model = mlflow.sklearn.load_model(model_uri)
        onnx_model = convert_sklearn(model, initial_types=initial_types, target_opset=13)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as f:
        f.write(onnx_model.SerializeToString())
    print(f"Exported ONNX model to {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tracking-uri", default="http://localhost:5001")
    parser.add_argument("--model-name", default="recommend_coarse_rank")
    parser.add_argument("--model-stage", default="Staging")
    parser.add_argument("--model-type", default="lightgbm", choices=["lightgbm", "logistic"])
    parser.add_argument("--output", default="Login_api/models/recommend_coarse_rank.onnx")
    args = parser.parse_args()
    export_model(args.tracking_uri, args.model_name, args.model_stage, Path(args.output), args.model_type)
