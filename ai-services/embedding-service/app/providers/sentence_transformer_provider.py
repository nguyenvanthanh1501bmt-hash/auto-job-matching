import logging
import math
from threading import Lock

import numpy as np
import torch
from sentence_transformers import SentenceTransformer

from app.config import EmbeddingSettings
from app.providers.base import (
    EmbeddingProvider,
    EmbeddingProviderMetadata,
)


LOGGER = logging.getLogger(__name__)


class SentenceTransformerEmbeddingProvider(
    EmbeddingProvider
):

    def __init__(
            self,
            settings: EmbeddingSettings,
    ) -> None:
        self._settings = settings
        self._model: SentenceTransformer | None = None
        self._ready = False

        # Lock để đảm bảo model chỉ được load một lần khi có nhiều thread.
        self._load_lock = Lock()

        # Lock để tránh nhiều thread inference trên cùng model cùng lúc.
        self._inference_lock = Lock()

        self._metadata = EmbeddingProviderMetadata(
            provider_name="sentence-transformer",
            model_name=settings.embedding_model_name,
            model_revision=settings.embedding_model_revision,
            embedding_version=settings.embedding_version,
            dimension=settings.embedding_expected_dimension,
            normalized=True,
        )

    def load(self) -> None:
        # Model đã được load thì không cần load lại.
        if self._ready:
            return

        with self._load_lock:
            if self._ready:
                return

            cache_directory = (
                self._settings.embedding_model_cache_dir
            )

            # Tạo thư mục cache nếu chưa tồn tại.
            cache_directory.mkdir(
                parents=True,
                exist_ok=True,
            )

            LOGGER.info(
                "Loading embedding model "
                "provider=%s modelName=%s modelRevision=%s "
                "device=%s cacheDirectory=%s",
                self._metadata.provider_name,
                self._metadata.model_name,
                self._metadata.model_revision,
                self._settings.embedding_device,
                cache_directory,
            )

            # Load model với revision, device và cache directory đã cấu hình.
            model = SentenceTransformer(
                self._settings.embedding_model_name,
                revision=(
                    self._settings.embedding_model_revision
                ),
                device=self._settings.embedding_device,
                cache_folder=str(cache_directory),
                trust_remote_code=False,
            )

            # Chuyển model sang evaluation mode vì chỉ dùng để inference.
            model.eval()

            # Lấy dimension thực tế từ model.
            actual_dimension = self._resolve_dimension(model)

            expected_dimension = (
                self._settings.embedding_expected_dimension
            )

            # Đảm bảo dimension model khớp với cấu hình của service.
            if actual_dimension != expected_dimension:
                raise RuntimeError(
                    "Embedding model dimension mismatch: "
                    f"expected={expected_dimension}, "
                    f"actual={actual_dimension}"
                )

            self._model = model

            # Cập nhật metadata bằng dimension thực tế của model.
            self._metadata = EmbeddingProviderMetadata(
                provider_name="sentence-transformer",
                model_name=(
                    self._settings.embedding_model_name
                ),
                model_revision=(
                    self._settings.embedding_model_revision
                ),
                embedding_version=(
                    self._settings.embedding_version
                ),
                dimension=actual_dimension,
                normalized=True,
            )

            self._ready = True

            LOGGER.info(
                "Embedding model ready "
                "provider=%s modelName=%s modelRevision=%s "
                "embeddingVersion=%s dimension=%s normalized=%s",
                self._metadata.provider_name,
                self._metadata.model_name,
                self._metadata.model_revision,
                self._metadata.embedding_version,
                self._metadata.dimension,
                self._metadata.normalized,
            )

    @property
    def is_ready(self) -> bool:
        return self._ready and self._model is not None

    @property
    def metadata(self) -> EmbeddingProviderMetadata:
        return self._metadata

    def embed(self, text: str) -> list[float]:
        # Không nhận input rỗng hoặc chỉ chứa whitespace.
        if not isinstance(text, str) or not text.strip():
            raise ValueError("text must not be blank")

        # Giới hạn độ dài input để tránh request quá lớn.
        if len(text) > self._settings.embedding_max_text_chars:
            raise ValueError(
                "text exceeds maximum allowed length of "
                f"{self._settings.embedding_max_text_chars} "
                "characters"
            )

        # Lấy model và đảm bảo model đã sẵn sàng.
        model = self._require_model()

        with self._inference_lock:
            # inference_mode tắt các cơ chế gradient không cần thiết,
            # giúp giảm memory và overhead khi chỉ chạy inference.
            with torch.inference_mode():
                encoded = model.encode(
                    text,
                    batch_size=1,
                    show_progress_bar=False,
                    output_value="sentence_embedding",
                    convert_to_numpy=True,
                    convert_to_tensor=False,
                    # Yêu cầu SentenceTransformer L2-normalize embedding.
                    normalize_embeddings=True,
                    device=self._settings.embedding_device,
                )

        # Chuyển output thành NumPy float32 và đảm bảo vector 1 chiều.
        vector_array = np.asarray(
            encoded,
            dtype=np.float32,
        ).reshape(-1)

        # Kiểm tra dimension của vector trả về.
        if vector_array.size != self._metadata.dimension:
            raise RuntimeError(
                "Embedding vector length mismatch: "
                f"expected={self._metadata.dimension}, "
                f"actual={vector_array.size}"
            )

        # Đảm bảo vector không chứa NaN hoặc Infinity.
        if not np.isfinite(vector_array).all():
            raise RuntimeError(
                "Embedding vector contains non-finite values"
            )

        # Tính L2 norm của embedding vector.
        norm = float(np.linalg.norm(vector_array))

        if not math.isfinite(norm) or norm <= 0.0:
            raise RuntimeError(
                "Embedding vector has an invalid L2 norm"
            )

        # Nếu vector chưa đủ chuẩn hóa thì normalize lại.
        if not math.isclose(
                norm,
                1.0,
                rel_tol=1e-5,
                abs_tol=1e-5,
        ):
            vector_array = vector_array / norm

        # Chuyển NumPy values sang Python float để trả về API.
        vector = [
            float(value)
            for value in vector_array.tolist()
        ]

        # Kiểm tra lần cuối trước khi trả vector.
        if not all(math.isfinite(value) for value in vector):
            raise RuntimeError(
                "Embedding vector contains non-finite values"
            )

        return vector

    def _require_model(self) -> SentenceTransformer:
        # Không cho inference nếu model chưa load thành công.
        if not self.is_ready or self._model is None:
            raise RuntimeError(
                "Sentence transformer provider is not ready"
            )

        return self._model

    def _resolve_dimension(
            self,
            model: SentenceTransformer,
    ) -> int:
        dimension: int | None = None

        # Ưu tiên API get_embedding_dimension() nếu model hỗ trợ.
        get_embedding_dimension = getattr(
            model,
            "get_embedding_dimension",
            None,
        )

        if callable(get_embedding_dimension):
            dimension = get_embedding_dimension()

        # Fallback sang API cũ/khác nếu method trên không tồn tại.
        if dimension is None:
            get_sentence_embedding_dimension = getattr(
                model,
                "get_sentence_embedding_dimension",
                None,
            )

            if callable(get_sentence_embedding_dimension):
                dimension = (
                    get_sentence_embedding_dimension()
                )

        # Không xác định được dimension thì không cho service startup.
        if dimension is None:
            raise RuntimeError(
                "Unable to determine embedding model dimension"
            )

        return int(dimension)