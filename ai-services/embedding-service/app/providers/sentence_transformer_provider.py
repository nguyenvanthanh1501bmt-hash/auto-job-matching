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
        self._load_lock = Lock()
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
        if self._ready:
            return

        with self._load_lock:
            if self._ready:
                return

            cache_directory = (
                self._settings.embedding_model_cache_dir
            )

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

            model = SentenceTransformer(
                self._settings.embedding_model_name,
                revision=(
                    self._settings.embedding_model_revision
                ),
                device=self._settings.embedding_device,
                cache_folder=str(cache_directory),
                trust_remote_code=False,
            )

            model.eval()

            actual_dimension = self._resolve_dimension(model)

            expected_dimension = (
                self._settings.embedding_expected_dimension
            )

            if actual_dimension != expected_dimension:
                raise RuntimeError(
                    "Embedding model dimension mismatch: "
                    f"expected={expected_dimension}, "
                    f"actual={actual_dimension}"
                )

            self._model = model

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
        if not isinstance(text, str) or not text.strip():
            raise ValueError("text must not be blank")

        if len(text) > self._settings.embedding_max_text_chars:
            raise ValueError(
                "text exceeds maximum allowed length of "
                f"{self._settings.embedding_max_text_chars} "
                "characters"
            )

        model = self._require_model()

        with self._inference_lock:
            with torch.inference_mode():
                encoded = model.encode(
                    text,
                    batch_size=1,
                    show_progress_bar=False,
                    output_value="sentence_embedding",
                    convert_to_numpy=True,
                    convert_to_tensor=False,
                    normalize_embeddings=True,
                    device=self._settings.embedding_device,
                )

        vector_array = np.asarray(
            encoded,
            dtype=np.float32,
        ).reshape(-1)

        if vector_array.size != self._metadata.dimension:
            raise RuntimeError(
                "Embedding vector length mismatch: "
                f"expected={self._metadata.dimension}, "
                f"actual={vector_array.size}"
            )

        if not np.isfinite(vector_array).all():
            raise RuntimeError(
                "Embedding vector contains non-finite values"
            )

        norm = float(np.linalg.norm(vector_array))

        if not math.isfinite(norm) or norm <= 0.0:
            raise RuntimeError(
                "Embedding vector has an invalid L2 norm"
            )

        if not math.isclose(
            norm,
            1.0,
            rel_tol=1e-5,
            abs_tol=1e-5,
        ):
            vector_array = vector_array / norm

        vector = [
            float(value)
            for value in vector_array.tolist()
        ]

        if not all(math.isfinite(value) for value in vector):
            raise RuntimeError(
                "Embedding vector contains non-finite values"
            )

        return vector

    def _require_model(self) -> SentenceTransformer:
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

        get_embedding_dimension = getattr(
            model,
            "get_embedding_dimension",
            None,
        )

        if callable(get_embedding_dimension):
            dimension = get_embedding_dimension()

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

        if dimension is None:
            raise RuntimeError(
                "Unable to determine embedding model dimension"
            )

        return int(dimension)