FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    EMBEDDING_PROVIDER=sentence-transformer \
    EMBEDDING_DEVICE=cpu \
    EMBEDDING_MODEL_CACHE_DIR=/models/huggingface

WORKDIR /app

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./requirements.txt

RUN python -m pip install --upgrade pip \
    && python -m pip install --no-cache-dir -r requirements.txt

COPY app ./app

RUN mkdir -p /models/huggingface \
    && useradd --create-home --uid 10001 appuser \
    && chown -R appuser:appuser /app /models/huggingface

USER appuser

EXPOSE 8002

HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=5 \
    CMD curl --fail --silent http://localhost:8002/ready > /dev/null || exit 1

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8002"]