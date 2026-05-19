FROM python:3.11-slim-bookworm

WORKDIR /workspace

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential curl \
    && rm -rf /var/lib/apt/lists/*

COPY recommendation_offline/requirements.txt /tmp/recommendation_offline_requirements.txt
RUN pip install --no-cache-dir -r /tmp/recommendation_offline_requirements.txt

COPY docker/recommendation-infra/mlops-service/requirements.txt /tmp/mlops_service_requirements.txt
RUN pip install --no-cache-dir -r /tmp/mlops_service_requirements.txt

COPY docker/recommendation-infra/mlops-service/app.py /app/app.py

EXPOSE 8090

CMD ["uvicorn", "app:app", "--app-dir", "/app", "--host", "0.0.0.0", "--port", "8090"]
