# Recommendation infrastructure (Docker)

Kafka, Milvus, MLflow, BGE embedding service, recommendation MLOps service, Feast Feature Server, and Triton for local development.

**Redis is not included** — use WSL Redis from `scripts/backend-deps` (same instance as Spring Boot).

## Start / stop

```powershell
cd C:\Users\35445\Desktop\note-sharing\docker\recommendation-infra
copy .env.example .env
# Edit .env: set FEAST_REDIS_CONNECTION to your WSL IP (wsl hostname -I)
docker compose up -d
docker compose ps
```

Stop:

```powershell
docker compose down
```

## Ports

| Service | Host port | Notes |
|--------|-----------|--------|
| Kafka | 9092 | `bootstrap.servers=localhost:9092` |
| BGE embedding service | 8088 | `POST /embed` text embeddings (`BAAI/bge-base-zh-v1.5` by default) |
| Recommend MLOps service | 8090 | `POST /internal/recommend/train` triggers offline pipeline |
| Feast Feature Server | 6566 | `recommendation.infra.feast.server-url` |
| Milvus gRPC | 19530 | Vector recall |
| Milvus Web UI | 9091 | http://127.0.0.1:9091/webui/ |
| Milvus MinIO API | 19000 | Separate from app MinIO |
| MLflow UI | 5001 | Model registry |
| Triton HTTP | 8000 | Fine-rank inference |
| Triton gRPC | 8001 | Optional SDK path |
| Triton metrics | 8002 | Health/metrics |

| Redis (WSL) | 6379 | `spring.redis.host` / Feast online store — **not** in this compose file |

## BGE embedding service

The BGE service is a reusable text-to-vector service for Milvus indexing and future semantic recall. It does not train a model; it loads a pretrained embedding model and returns normalized vectors.

Start only this service:

```powershell
cd C:\Users\35445\Desktop\note-sharing\docker\recommendation-infra
docker compose build bge-embedding-service
docker compose up -d bge-embedding-service
```

Health check:

```powershell
curl http://localhost:8088/health
curl -X POST http://localhost:8088/embed -H "Content-Type: application/json" -d "{\"texts\":[\"Spring Boot 推荐系统笔记\"]}"
```

`BAAI/bge-base-zh-v1.5` returns 768-dimensional embeddings. Keep `recommendation.infra.milvus.vector-dim` aligned when enabling Milvus vector recall.

## Recommend MLOps service

The MLOps service is an independent Python service that runs the offline automation pipeline outside the Spring JVM.

Start only this service:

```powershell
cd C:\Users\35445\Desktop\note-sharing\docker\recommendation-infra
docker compose build recommend-mlops-service
docker compose up -d recommend-mlops-service
```

Trigger training directly:

```powershell
curl -X POST http://localhost:8090/internal/recommend/train -H "Content-Type: application/json" -d "{\"runExport\":false}"
curl http://localhost:8090/internal/recommend/status
```

Spring proxies the same operation through `POST /internal/recommend/train`.

## Feast + WSL Redis

Feast (container) and `materialize_online.py` (host) both read `FEAST_REDIS_CONNECTION` via `recommendation_offline/feast_repo/feature_store.yaml`.

| Where you run | Typical `FEAST_REDIS_CONNECTION` |
|---------------|----------------------------------|
| Feast Docker container | WSL IP, e.g. `172.24.198.86:6379` (set in `.env`) |
| `materialize_online.py` on Windows | Same WSL IP |
| `materialize_online.py` inside WSL | `localhost:6379` |

If `host.docker.internal:6379` reaches your WSL Redis from the container, you can use that instead of the WSL IP in `.env`.

Ensure WSL Redis listens on `0.0.0.0` (or the WSL interface), not only `127.0.0.1`, so Docker can connect.

## Recommended workflow

1. Start WSL backend deps (Redis, MySQL, …): `scripts/backend-deps/start-backend-deps.ps1`
2. Copy `.env` and set `FEAST_REDIS_CONNECTION` to your WSL IP
3. Start this compose stack
4. Materialize Feast features (from repo root, with env set):

```powershell
$env:FEAST_REDIS_CONNECTION="172.24.198.86:6379"
python recommendation_offline/feast/materialize_online.py
```

5. Train/register/export ONNX, then package Triton model
6. Run Spring Boot with feature flags enabled in `application.yml`

## Triton model packaging

```bash
python recommendation_offline/mlflow/export_onnx.py
python recommendation_offline/triton/package_model.py
docker compose restart triton
```

## Flyway note

If your DB previously applied old migrations `V4_create_note_stat_table.sql` / `V5_create_user_follow_table.sql`, repair `flyway_schema_history` before deploying `V4__` / `V5__` versions.
