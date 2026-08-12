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

        # Metadata mô tả fake embedding provider và vector mà nó tạo ra.
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
        # Provider đã sẵn sàng thì không cần load lại.
        if self._ready:
            return

        # Đảm bảo chỉ một thread thực hiện quá trình load.
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
        # Không cho phép tạo embedding khi provider chưa được load.
        if not self._ready:
            raise RuntimeError(
                "Fake embedding provider is not ready"
            )

        if not isinstance(text, str) or not text.strip():
            raise ValueError("text must not be blank")

        # Tạo vector giả có tính deterministic từ text.
        vector = self._generate_vector(text)

        # Tính L2 norm để chuẩn hóa vector về độ dài 1.
        norm = math.sqrt(
            sum(value * value for value in vector)
        )

        if not math.isfinite(norm) or norm <= 0.0:
            raise RuntimeError(
                "Unable to normalize fake embedding vector"
            )

        # L2 normalization: mỗi phần tử chia cho độ dài vector.
        normalized_vector = [
            value / norm
            for value in vector
        ]

        # Đảm bảo vector sau khi normalize vẫn chỉ chứa giá trị hữu hạn.
        if not all(
                math.isfinite(value)
                for value in normalized_vector
        ):
            raise RuntimeError(
                "Fake embedding vector contains non-finite values"
            )

        return normalized_vector

    def _generate_vector(self, text: str) -> list[float]:
        # Kết hợp seed + text để cùng một input luôn tạo ra cùng vector.
        seed_bytes = (
                self._settings.embedding_fake_seed
                + "\0"
                + text
        ).encode("utf-8")

        values: list[float] = []
        counter = 0

        # Tiếp tục tạo hash cho đến khi đủ số chiều của vector.
        while len(values) < self._metadata.dimension:
            digest = hashlib.sha256(
                seed_bytes
                + counter.to_bytes(
                    8,
                    byteorder="big",
                    signed=False,
                )
            ).digest()

            # Mỗi 4 byte trong SHA-256 được chuyển thành một giá trị số.
            for offset in range(0, len(digest), 4):
                unsigned_value = struct.unpack(
                    ">I",
                    digest[offset:offset + 4],
                )[0]

                # Đưa số nguyên [0, 2^32 - 1] về khoảng [-1, 1].
                scaled_value = (
                                       unsigned_value / 4_294_967_295.0
                               ) * 2.0 - 1.0

                values.append(scaled_value)

                # Dừng ngay khi vector đạt đủ dimension yêu cầu.
                if len(values) == self._metadata.dimension:
                    break

            # Tăng counter để tạo hash khác cho block tiếp theo.
            counter += 1

        return values