import hashlib
import logging
import math

from fastapi import APIRouter, HTTPException, status
from starlette.concurrency import run_in_threadpool

from app.providers.base import EmbeddingProvider
from app.schemas import EmbeddingRequest, EmbeddingResponse


LOGGER = logging.getLogger(__name__)


def create_embedding_router(
        provider: EmbeddingProvider,
) -> APIRouter:
    router = APIRouter(tags=["embeddings"])

    @router.post(
        "/api/v1/embeddings",
        response_model=EmbeddingResponse,
        status_code=status.HTTP_200_OK,
    )
    async def create_embedding(
            request: EmbeddingRequest,
    ) -> EmbeddingResponse:
        # Provider chưa sẵn sàng thì không nhận request.
        if not provider.is_ready:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Embedding provider is not ready",
            )

        # Tạo SHA-256 hash của text để định danh request trong log.
        text_bytes = request.text.encode("utf-8")
        text_hash = hashlib.sha256(text_bytes).hexdigest()

        LOGGER.info(
            "Embedding request started "
            "textHash=%s textLength=%s",
            text_hash,
            len(request.text),
        )

        try:
            # Chạy model trong threadpool vì embed() có thể là synchronous
            # và tốn thời gian, tránh block event loop của FastAPI.
            vector = await run_in_threadpool(
                provider.embed,
                request.text,
            )

            metadata = provider.metadata

            # Kiểm tra vector trả về có đúng dimension và giá trị hợp lệ.
            validate_vector(
                vector=vector,
                expected_dimension=metadata.dimension,
            )

            response = EmbeddingResponse(
                vector=vector,
                dimension=metadata.dimension,
                model_name=metadata.model_name,
                model_revision=metadata.model_revision,
                embedding_version=metadata.embedding_version,
                text_hash=text_hash,
                normalized=True,
            )

            LOGGER.info(
                "Embedding request completed "
                "textHash=%s modelName=%s "
                "embeddingVersion=%s dimension=%s",
                text_hash,
                metadata.model_name,
                metadata.embedding_version,
                metadata.dimension,
            )

            return response

        # Lỗi dữ liệu/input hoặc vector không hợp lệ.
        except ValueError as exception:
            LOGGER.warning(
                "Embedding request rejected "
                "textHash=%s errorType=%s message=%s",
                text_hash,
                type(exception).__name__,
                safe_error_message(exception),
            )

            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=safe_error_message(exception),
            ) from exception

        # HTTPException đã có status code riêng nên giữ nguyên.
        except HTTPException:
            raise

        # Các lỗi không xác định trong quá trình tạo embedding.
        except Exception as exception:
            LOGGER.error(
                "Embedding request failed "
                "textHash=%s errorType=%s message=%s",
                text_hash,
                type(exception).__name__,
                safe_error_message(exception),
            )

            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Embedding generation failed",
            ) from exception

    return router


def validate_vector(
        vector: list[float],
        expected_dimension: int,
) -> None:
    # Không cho phép provider trả về vector rỗng.
    if not vector:
        raise RuntimeError(
            "Embedding provider returned an empty vector"
        )

    # Dimension của vector phải đúng với dimension mà model công bố.
    if len(vector) != expected_dimension:
        raise RuntimeError(
            "Embedding vector length mismatch: "
            f"expected={expected_dimension}, "
            f"actual={len(vector)}"
        )

    # Kiểm tra vector không chứa NaN hoặc Infinity.
    if not all(math.isfinite(value) for value in vector):
        raise RuntimeError(
            "Embedding vector contains non-finite values"
        )

    # Tính L2 norm của vector.
    norm = math.sqrt(
        sum(value * value for value in vector)
    )

    # Norm phải là số hữu hạn và lớn hơn 0.
    if not math.isfinite(norm) or norm <= 0.0:
        raise RuntimeError(
            "Embedding vector has an invalid L2 norm"
        )

    # Kiểm tra vector đã được L2 normalize về độ dài gần bằng 1.
    if not math.isclose(
            norm,
            1.0,
            rel_tol=1e-5,
            abs_tol=1e-5,
    ):
        raise RuntimeError(
            "Embedding vector is not L2 normalized"
        )


def safe_error_message(
        exception: Exception,
) -> str:
    # Lấy message lỗi và loại bỏ khoảng trắng thừa.
    message = str(exception).strip()

    if not message:
        return type(exception).__name__

    # Giới hạn message tối đa 500 ký tự.
    return message[:500]