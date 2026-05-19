# Recommendation Offline Pipeline

End-to-end offline pipeline for Spark samples, Feast features, MLflow registry, LightGBM training, ONNX export, and Triton packaging.

## Layout

- `spark/` – MySQL/Kafka export, sample builder, offline features, point-in-time join
- `feast_repo/` – Feast entities, sources, feature views, feature services
- `feast/materialize_online.py` – apply + materialize to **WSL Redis** online store (`FEAST_REDIS_CONNECTION`)
- `mlflow/` – train/register (LightGBM/logistic), evaluate gates, export ONNX, promote model version
- `triton/package_model.py` – copy ONNX into Triton model repository
- `automation/` – local automation loop (`auto_pipeline.py`) and gate configs

## Redis (WSL)

Feast online store uses the same WSL Redis as Spring Boot (`spring.redis.host`). Set before `materialize_online.py` or in `docker/recommendation-infra/.env` for the Feast container:

```bash
export FEAST_REDIS_CONNECTION=172.24.198.86:6379   # wsl hostname -I
```

Configured in `feast_repo/feature_store.yaml` as `${FEAST_REDIS_CONNECTION:localhost:6379}`.

## Quick start

```bash
pip install -r recommendation_offline/requirements.txt

python recommendation_offline/spark/export_mysql_tables.py \
  --mysql-url "mysql+pymysql://ebook_admin:ebook_123456@localhost:3306/ebook_platform"

python recommendation_offline/spark/export_kafka_events.py
python recommendation_offline/spark/build_offline_features.py
python recommendation_offline/feast/materialize_online.py

python recommendation_offline/spark/build_training_samples.py
python recommendation_offline/mlflow/train_and_register.py --model-type lightgbm
python recommendation_offline/mlflow/evaluate_model.py --candidate-stage Staging --production-stage Production
python recommendation_offline/mlflow/export_onnx.py --model-stage Staging --model-type lightgbm
python recommendation_offline/triton/package_model.py
```

Serving feature columns (7-dim): `views`, `likes`, `favorites`, `comments`, `answers`, `tag_match_score`, `recall_score`.

## Automated loop (rule -> train -> gray rollout)

Use the local orchestrator to run sample generation, training, evaluation gates, ONNX export, and rollout-state update:

```bash
python recommendation_offline/automation/auto_pipeline.py \
  --tracking-uri http://localhost:5001 \
  --model-name recommend_coarse_rank \
  --model-type lightgbm
```

Key files:

- `recommendation_offline/automation/gates.yaml`
- `data/pipeline/pipeline_state.json`
- `data/pipeline/rollout_state.json`
- `data/pipeline/evaluation_report.json`
