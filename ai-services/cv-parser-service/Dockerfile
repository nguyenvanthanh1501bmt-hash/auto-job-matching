FROM python:3.12-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONHASHSEED=0 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    MALLOC_ARENA_MAX=2 \
    PYTHONPATH=/app \
    HOME=/home/autojob

WORKDIR /app

RUN apt-get update \
    && apt-get install \
        --yes \
        --no-install-recommends \
        antiword \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd \
        --gid 10001 \
        autojob \
    && useradd \
        --uid 10001 \
        --gid autojob \
        --create-home \
        --home-dir /home/autojob \
        --shell /usr/sbin/nologin \
        autojob

COPY requirements.txt /app/requirements.txt

RUN python -m pip install \
        --no-cache-dir \
        --no-compile \
        --requirement /app/requirements.txt

COPY --chown=autojob:autojob app /app/app

RUN test -x "$(command -v antiword)" \
    && test -f /usr/share/antiword/UTF-8.txt

USER autojob:autojob

EXPOSE 8003

STOPSIGNAL SIGTERM

HEALTHCHECK \
    --interval=30s \
    --timeout=5s \
    --start-period=20s \
    --retries=3 \
    CMD ["python", "-c", "import json, urllib.request; response = urllib.request.urlopen('http://127.0.0.1:8003/ready', timeout=3); body = json.load(response); raise SystemExit(0 if body.get('status') == 'UP' else 1)"]

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8003", "--workers", "1", "--loop", "asyncio", "--http", "h11", "--no-access-log", "--no-server-header", "--timeout-keep-alive", "5"]