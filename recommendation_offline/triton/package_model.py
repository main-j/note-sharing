#!/usr/bin/env python3
"""Copy exported ONNX model into Triton model repository layout."""

import argparse
import shutil
from pathlib import Path


def package(onnx_path: Path, repo_dir: Path) -> None:
    model_dir = repo_dir / "recommend_coarse_rank" / "1"
    model_dir.mkdir(parents=True, exist_ok=True)
    target = model_dir / "model.onnx"
    shutil.copy2(onnx_path, target)
    print(f"copied {onnx_path} -> {target}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--onnx", default="../Login_api/models/recommend_coarse_rank.onnx")
    parser.add_argument(
        "--repo",
        default="../docker/recommendation-infra/triton/model_repository",
    )
    args = parser.parse_args()
    package(Path(args.onnx), Path(args.repo))
