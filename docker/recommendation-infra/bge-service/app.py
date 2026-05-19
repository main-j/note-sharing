import os
from typing import List

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer


MODEL_NAME = os.getenv("BGE_MODEL_NAME", "BAAI/bge-base-zh-v1.5")
DEVICE = os.getenv("BGE_DEVICE", "cpu")
NORMALIZE = os.getenv("BGE_NORMALIZE", "true").lower() == "true"
BATCH_SIZE = int(os.getenv("BGE_BATCH_SIZE", "32"))

app = FastAPI(title="bge-embedding-service", version="1.0.0")
model: SentenceTransformer | None = None


class EmbedRequest(BaseModel):
    texts: List[str] = Field(..., min_length=1, max_length=128)
    normalize: bool | None = None


class EmbedResponse(BaseModel):
    model: str
    dimension: int
    embeddings: List[List[float]]


@app.on_event("startup")
def load_model() -> None:
    global model
    model = SentenceTransformer(MODEL_NAME, device=DEVICE)


@app.get("/health")
def health() -> dict[str, object]:
    return {
        "status": "ok",
        "model": MODEL_NAME,
        "device": DEVICE,
        "loaded": model is not None,
        "dimension": model.get_sentence_embedding_dimension() if model else None,
    }


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest) -> EmbedResponse:
    if model is None:
        raise HTTPException(status_code=503, detail="model is not loaded")

    normalize = NORMALIZE if request.normalize is None else request.normalize
    embeddings = model.encode(
        request.texts,
        normalize_embeddings=normalize,
        batch_size=min(BATCH_SIZE, len(request.texts)),
        show_progress_bar=False,
    )
    return EmbedResponse(
        model=MODEL_NAME,
        dimension=model.get_sentence_embedding_dimension(),
        embeddings=embeddings.tolist(),
    )
