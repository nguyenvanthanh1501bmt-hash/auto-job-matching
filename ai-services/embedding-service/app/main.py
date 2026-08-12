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
    # Cấu hình log level và format cho toàn bộ service.
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
    # Chọn implementation của EmbeddingProvider dựa trên config.
    if settings.embedding_provider == "fake":
        return FakeEmbeddingProvider(settings)

    if settings.embedding_provider == "sentence-transformer":
        return SentenceTransformerEmbeddingProvider(settings)

    # Provider không được hỗ trợ thì dừng quá trình khởi tạo service.
    raise ValueError(
        "Unsupported embedding provider: "
        f"{settings.embedding_provider}"
    )


def create_app(
        settings: EmbeddingSettings | None = None,
        provider: EmbeddingProvider | None = None,
) -> FastAPI:
    # Cho phép truyền settings/provider từ bên ngoài để dễ test.
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

        # Nếu bật config này thì load model/provider ngay khi service startup.
        if active_settings.embedding_load_on_startup:
            try:
                # load() có thể là synchronous nên chạy trong threadpool
                # để không block event loop của FastAPI.
                await run_in_threadpool(
                    active_provider.load
                )
            except Exception as exception:
                # Lưu lỗi để endpoint /ready có thể trả về nguyên nhân.
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

        # Service tiếp tục chạy sau khi startup hoàn tất.
        yield

        LOGGER.info(
            "Embedding service stopped "
            "serviceName=%s",
            active_settings.service_name,
        )

    # Tạo FastAPI application và đăng ký lifecycle handler.
    application = FastAPI(
        title="AutoJob Embedding Service",
        version=active_settings.service_version,
        lifespan=lifespan,
    )

    # Lưu các object dùng chung vào application state.
    application.state.settings = active_settings
    application.state.embedding_provider = active_provider
    application.state.provider_load_error = None

    # Đăng ký các endpoint liên quan đến embedding.
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
        # Health chỉ xác nhận service đang hoạt động.
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
        # Ready kiểm tra provider/model đã sẵn sàng để xử lý request chưa.
        if not active_provider.is_ready:
            detail = "Embedding provider is not ready"

            # Nếu load model thất bại thì trả thêm nguyên nhân.
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

        # Provider đã sẵn sàng -> lấy metadata của model.
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
    # Lấy message của exception và loại bỏ khoảng trắng thừa.
    message = str(exception).strip()

    if not message:
        return type(exception).__name__

    # Giới hạn message để log/response không quá dài.
    return message[:500]


app = create_app()