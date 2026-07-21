import hashlib
import math
import struct
from threading import Lock

from app.config import EmbeddingSettings
from app.providers.base import (
    EmbeddingProvider,
    EmbeddingProviderMetadata,
)


class FakeEmbeddingProvider(EmbeddingProvider):

    def __init__(
        self,
        settings: EmbeddingSettings,
    ) -> None:
        self._settings = settings
        self._ready = False
        self._load_lock = Lock()

        self._metadata = EmbeddingProviderMetadata(
            provider_name="fake",
            model_name=settings.embedding_fake_model_name,
            model_revision=(
                settings.embedding_fake_model_revision
            ),
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

            if self._metadata.dimension <= 0:
                raise ValueError(
                    "Fake embedding dimension must be positive"
                )

            self._ready = True

    @property
    def is_ready(self) -> bool:
        return self._ready

    @property
    def metadata(self) -> EmbeddingProviderMetadata:
        return self._metadata

    def embed(self, text: str) -> list[float]:
        if not self._ready:
            raise RuntimeError(
                "Fake embedding provider is not ready"
            )

        if not isinstance(text, str) or not text.strip():
            raise ValueError("text must not be blank")

        vector = self._generate_vector(text)
        norm = math.sqrt(
            sum(value * value for value in vector)
        )

        if not math.isfinite(norm) or norm <= 0.0:
            raise RuntimeError(
                "Unable to normalize fake embedding vector"
            )

        normalized_vector = [
            value / norm
            for value in vector
        ]

        if not all(
            math.isfinite(value)
            for value in normalized_vector
        ):
            raise RuntimeError(
                "Fake embedding vector contains non-finite values"
            )

        return normalized_vector

    def _generate_vector(self, text: str) -> list[float]:
        seed_bytes = (
            self._settings.embedding_fake_seed
            + "\0"
            + text
        ).encode("utf-8")

        values: list[float] = []
        counter = 0

        while len(values) < self._metadata.dimension:
            digest = hashlib.sha256(
                seed_bytes
                + counter.to_bytes(
                    8,
                    byteorder="big",
                    signed=False,
                )
            ).digest()

            for offset in range(0, len(digest), 4):
                unsigned_value = struct.unpack(
                    ">I",
                    digest[offset:offset + 4],
                )[0]

                scaled_value = (
                    unsigned_value / 4_294_967_295.0
                ) * 2.0 - 1.0

                values.append(scaled_value)

                if len(values) == self._metadata.dimension:
                    break

            counter += 1

        return values