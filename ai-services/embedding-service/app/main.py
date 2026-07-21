import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI, HTTPException, status
from starlette.concurrency import run_in_threadpool

from app.api.embeddings import create_embedding_router
from app.config import EmbeddingSettings, get_settings
from app.providers.base import EmbeddingProvider
from app.providers.fake_provider import FakeEmbeddingProvider
from app.providers.sentence_transformer_provider import (
    SentenceTransformerEmbeddingProvider,
)
from app.schemas import HealthResponse, ReadyResponse


LOGGER = logging.getLogger(__name__)


def configure_logging(settings: EmbeddingSettings) -> None:
    logging.basicConfig(
        level=getattr(
            logging,
            settings.log_level.upper(),
            logging.INFO,
        ),
        format=(
            "%(asctime)s %(levelname)s "
            "%(name)s %(message)s"
        ),
    )


def create_provider(
        settings: EmbeddingSettings,
) -> EmbeddingProvider:
    if settings.embedding_provider == "fake":
        return FakeEmbeddingProvider(settings)

    if settings.embedding_provider == "sentence-transformer":
        return SentenceTransformerEmbeddingProvider(settings)

    raise ValueError(
        "Unsupported embedding provider: "
        f"{settings.embedding_provider}"
    )


def create_app(
        settings: EmbeddingSettings | None = None,
        provider: EmbeddingProvider | None = None,
) -> FastAPI:
    active_settings = settings or get_settings()
    active_provider = provider or create_provider(
        active_settings
    )

    configure_logging(active_settings)

    @asynccontextmanager
    async def lifespan(
            application: FastAPI,
    ) -> AsyncIterator[None]:
        application.state.provider_load_error = None

        LOGGER.info(
            "Embedding service starting "
            "serviceName=%s serviceVersion=%s provider=%s",
            active_settings.service_name,
            active_settings.service_version,
            active_settings.embedding_provider,
        )

        if active_settings.embedding_load_on_startup:
            try:
                await run_in_threadpool(
                    active_provider.load
                )
            except Exception as exception:
                safe_message = safe_error_message(exception)

                application.state.provider_load_error = (
                    safe_message
                )

                LOGGER.error(
                    "Embedding provider load failed "
                    "provider=%s errorType=%s message=%s",
                    active_settings.embedding_provider,
                    type(exception).__name__,
                    safe_message,
                )

        yield

        LOGGER.info(
            "Embedding service stopped "
            "serviceName=%s",
            active_settings.service_name,
        )

    application = FastAPI(
        title="AutoJob Embedding Service",
        version=active_settings.service_version,
        lifespan=lifespan,
    )

    application.state.settings = active_settings
    application.state.embedding_provider = active_provider
    application.state.provider_load_error = None

    application.include_router(
        create_embedding_router(active_provider)
    )

    @application.get(
        "/health",
        response_model=HealthResponse,
        status_code=status.HTTP_200_OK,
        tags=["health"],
    )
    async def health() -> HealthResponse:
        return HealthResponse(
            status="ok",
            service_name=active_settings.service_name,
            service_version=active_settings.service_version,
        )

    @application.get(
        "/ready",
        response_model=ReadyResponse,
        status_code=status.HTTP_200_OK,
        tags=["health"],
    )
    async def ready() -> ReadyResponse:
        if not active_provider.is_ready:
            detail = "Embedding provider is not ready"

            load_error = getattr(
                application.state,
                "provider_load_error",
                None,
            )

            if load_error:
                detail = (
                    "Embedding provider is not ready: "
                    f"{load_error}"
                )

            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=detail,
            )

        metadata = active_provider.metadata

        return ReadyResponse(
            status="ready",
            provider=metadata.provider_name,
            model_name=metadata.model_name,
            model_revision=metadata.model_revision,
            embedding_version=metadata.embedding_version,
            dimension=metadata.dimension,
            normalized=True,
        )

    return application


def safe_error_message(
        exception: Exception,
) -> str:
    message = str(exception).strip()

    if not message:
        return type(exception).__name__

    return message[:500]


app = create_app()