from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field


@dataclass(frozen=True, slots=True)
class ExtractionResult:
    text: str
    warnings: tuple[str, ...] = field(default_factory=tuple)
    page_count: int | None = None


class DocumentExtractor(ABC):
    @abstractmethod
    def extract(
            self,
            data: bytes,
            raw_cv_id: str | None = None,
    ) -> ExtractionResult:
        raise NotImplementedError