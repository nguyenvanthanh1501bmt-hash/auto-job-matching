from abc import ABC, abstractmethod
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class EmbeddingProviderMetadata:
    provider_name: str
    model_name: str
    model_revision: str
    embedding_version: str
    dimension: int
    normalized: bool = True


class EmbeddingProvider(ABC):

    @abstractmethod
    def load(self) -> None:
        """Load and validate provider resources."""

    @property
    @abstractmethod
    def is_ready(self) -> bool:
        """Return whether the provider is ready for inference."""

    @property
    @abstractmethod
    def metadata(self) -> EmbeddingProviderMetadata:
        """Return immutable provider metadata."""

    @abstractmethod
    def embed(self, text: str) -> list[float]:
        """Embed the exact input text into a normalized vector."""