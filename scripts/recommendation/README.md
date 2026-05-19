# Recommendation System (Final Stack)

## Architecture

Client → Spring Boot Event API → Kafka → Flink → Redis online features → Feast Feature Server → Spring HomeFeedService → Feed response.

Optional components: Milvus vector recall, ONNX coarse rank, Triton fine rank.

## Local startup order

1. Core backend deps (WSL Redis, MySQL, …): `scripts/backend-deps/start-backend-deps.ps1`
2. Recommendation infra: copy `docker/recommendation-infra/.env.example` → `.env`, set `FEAST_REDIS_CONNECTION` to your WSL IP (same as `spring.redis.host`), then `docker compose up -d`
3. Export raw data and build offline features:

```bash
pip install -r recommendation_offline/requirements.txt
python recommendation_offline/spark/export_mysql_tables.py --mysql-url "mysql+pymysql://ebook_admin:ebook_123456@localhost:3306/ebook_platform"
python recommendation_offline/spark/export_kafka_events.py
python recommendation_offline/spark/build_offline_features.py
# Windows: point materialize at WSL Redis
export FEAST_REDIS_CONNECTION=172.24.198.86:6379   # or set in .env / PowerShell $env:FEAST_REDIS_CONNECTION
python recommendation_offline/feast/materialize_online.py
```

4. Train and export model:

```bash
python recommendation_offline/spark/build_training_samples.py
python recommendation_offline/mlflow/train_and_register.py --model-type lightgbm
python recommendation_offline/mlflow/evaluate_model.py --candidate-stage Staging --production-stage Production
python recommendation_offline/mlflow/export_onnx.py --model-type lightgbm
python recommendation_offline/triton/package_model.py
```

5. Run Spring Boot `Login_api`
6. Run Flink job `com.project.login.service.flink.BehaviorSearchJob` with `FLINK_EXECUTION_TARGET=local`
7. Optional Milvus index: `python scripts/recommendation/init_milvus_index.py`

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/recommend/feed?userId=&pageSize=` | Home feed (`requestId` + `items`) |
| POST | `/api/v1/recommend/events/interaction` | Interaction events → Kafka |
| POST | `/api/v1/search/notes` | Authenticated request uses recommendation ranking (anonymous keeps ES fallback) |
| GET | `/api/v1/search/questions` | Authenticated request uses recommendation ranking (anonymous keeps ES fallback) |
| GET | `/api/v1/recommend/notes` | Legacy keyword recommend |
| GET | `/api/v1/recommend/QAs` | Legacy QA keyword recommend |

## Automated local loop

Run one command to execute sample generation -> LightGBM training -> evaluation gate -> ONNX export -> rollout state update:

```bash
python recommendation_offline/automation/auto_pipeline.py \
  --tracking-uri http://localhost:5001 \
  --model-name recommend_coarse_rank \
  --model-type lightgbm
```

Rollout state is read by Spring from `recommendation.experiment.state-file` (default `data/pipeline/rollout_state.json`) to support 0%/5%/20%/50%/100% model traffic progression.

## MLOps service mode

For production-like local development, run the pipeline through the independent Python MLOps service instead of the Spring JVM:

```powershell
cd C:\Users\35445\Desktop\note-sharing\docker\recommendation-infra
docker compose build recommend-mlops-service
docker compose up -d recommend-mlops-service
```

Trigger through Spring:

```http
POST /internal/recommend/train?runExport=false
GET /internal/recommend/status
```

Spring only proxies the request; the Python MLOps service executes `recommendation_offline/automation/auto_pipeline.py`.

## Enable advanced components

```yaml
recommendation:
  infra:
    milvus:
      enabled: true
    onnx:
      enabled: true
      model-path: models/recommend_coarse_rank.onnx
    triton:
      enabled: true
      endpoint: http://localhost:8000
    feast:
      enabled: true
      server-url: http://localhost:6566
```
