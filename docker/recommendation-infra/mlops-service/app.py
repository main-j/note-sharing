import os
import subprocess
import sys
import threading
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel


WORKSPACE = Path(os.getenv("MLOPS_WORKSPACE", "/workspace"))
DEFAULT_TRACKING_URI = os.getenv("MLFLOW_TRACKING_URI", "http://mlflow:5000")
DEFAULT_MODEL_NAME = os.getenv("MLOPS_MODEL_NAME", "recommend_coarse_rank")
DEFAULT_MODEL_TYPE = os.getenv("MLOPS_MODEL_TYPE", "lightgbm")

app = FastAPI(title="recommend-mlops-service", version="1.0.0")
lock = threading.Lock()
active_thread: Optional[threading.Thread] = None
last_job: dict[str, object] = {
    "jobId": None,
    "status": "idle",
    "startedAt": None,
    "finishedAt": None,
    "returnCode": None,
    "logTail": "",
}


class TrainRequest(BaseModel):
    trackingUri: Optional[str] = None
    modelName: Optional[str] = None
    modelType: Optional[str] = None
    runExport: bool = False


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def command_for(request: TrainRequest) -> list[str]:
    cmd = [
        sys.executable,
        "recommendation_offline/automation/auto_pipeline.py",
        "--tracking-uri",
        request.trackingUri or DEFAULT_TRACKING_URI,
        "--model-name",
        request.modelName or DEFAULT_MODEL_NAME,
        "--model-type",
        request.modelType or DEFAULT_MODEL_TYPE,
    ]
    if request.runExport:
        cmd.append("--run-export")
    return cmd


def run_job(job_id: str, request: TrainRequest) -> None:
    global last_job
    cmd = command_for(request)
    with lock:
        last_job.update(
            {
                "jobId": job_id,
                "status": "running",
                "startedAt": utc_now(),
                "finishedAt": None,
                "returnCode": None,
                "command": cmd,
                "logTail": "",
            }
        )
    try:
        process = subprocess.run(
            cmd,
            cwd=WORKSPACE,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=None,
        )
        output = process.stdout or ""
        with lock:
            last_job.update(
                {
                    "status": "succeeded" if process.returncode == 0 else "failed",
                    "finishedAt": utc_now(),
                    "returnCode": process.returncode,
                    "logTail": output[-8000:],
                }
            )
    except Exception as exc:
        with lock:
            last_job.update(
                {
                    "status": "failed",
                    "finishedAt": utc_now(),
                    "returnCode": -1,
                    "logTail": str(exc),
                }
            )


@app.get("/health")
def health() -> dict[str, object]:
    return {"status": "ok", "workspace": str(WORKSPACE)}


@app.post("/internal/recommend/train")
def trigger_train(request: TrainRequest) -> dict[str, object]:
    global active_thread
    with lock:
        if active_thread is not None and active_thread.is_alive():
            raise HTTPException(status_code=409, detail="recommend mlops job is already running")
        job_id = str(uuid.uuid4())
        active_thread = threading.Thread(target=run_job, args=(job_id, request), daemon=True)
        active_thread.start()
        return {"accepted": True, "jobId": job_id, "status": "running"}


@app.get("/internal/recommend/status")
def status() -> dict[str, object]:
    with lock:
        return dict(last_job)
