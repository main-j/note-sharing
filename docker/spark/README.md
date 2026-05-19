# Spark (Docker)

Minimal **1 master + 1 worker** cluster using the Docker Official Image **`spark:4.0.2-python3`** (`library/spark` on Docker Hub).

## Why not Bitnami?

Tags like `bitnami/spark:4.1` / `latest` are frequently **not published** on Docker Hub (similar to the old `bitnami/kafka:4.2` issue). The **`spark`** official image is maintained and pulls reliably.

## Start / stop

```powershell
cd C:\Users\35445\Desktop\note-sharing\docker\spark
docker compose up -d
docker compose ps
```

Stop:

```powershell
docker compose down
```

## Ports

| Service    | Host port | Inside container | Purpose        |
|-----------|-----------|-------------------|----------------|
| Master    | **7077**  | 7077              | `spark://…` URI |
| Master UI | **8085**  | 8080              | Spark Master UI |
| Worker UI | **8086**  | 8081              | Worker UI       |

Open Master UI: http://127.0.0.1:8085

## Submit a job (example)

Run `spark-submit` inside the master container (paths and deps live in the container filesystem):

```powershell
docker compose exec spark-master /opt/spark/bin/spark-submit `
  --master spark://spark-master:7077 `
  --conf spark.driver.host=spark-master `
  /opt/spark/examples/src/main/python/pi.py 100
```

## Kafka on the host (Docker Desktop)

From inside Spark containers, brokers on Windows/Mac Docker Desktop are often reachable as:

`bootstrap.servers=host.docker.internal:9092`

(Only if your recommendation stack exposes Kafka on **9092** on the host.)

## Optional: Jupyter + PySpark

If you prefer a notebook instead of this cluster, pull `jupyter/pyspark-notebook` separately; it is heavier but includes Spark + Python tooling.

## Troubleshooting

- **`short read: … unexpected EOF`** while pulling — transient network or incomplete layer download. Retry `docker compose pull` / `docker compose up -d`, or restart Docker Desktop and pull again.
