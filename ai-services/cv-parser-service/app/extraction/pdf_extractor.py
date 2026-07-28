from __future__ import annotations

import math
import statistics

import fitz

from app.config import Settings
from app.exceptions import (
    CvCorruptFileError,
    CvTextNotExtractableError,
    CvUnsupportedFormatError,
)
from app.extraction.base import DocumentExtractor, ExtractionResult


class PdfExtractor(DocumentExtractor):
    def __init__(
            self,
            settings: Settings,
    ) -> None:
        self._settings = settings

    def extract(
            self,
            data: bytes,
            raw_cv_id: str | None = None,
    ) -> ExtractionResult:
        if not data.startswith(b"%PDF-"):
            raise CvUnsupportedFormatError(
                message=(
                    "The file extension or content type indicates PDF, "
                    "but the file signature is not PDF"
                ),
                raw_cv_id=raw_cv_id,
            )

        try:
            document = fitz.open(
                stream=data,
                filetype="pdf",
            )
        except (fitz.FileDataError, RuntimeError, ValueError) as exception:
            raise CvCorruptFileError(
                message="The PDF document is corrupt or malformed",
                raw_cv_id=raw_cv_id,
            ) from exception

        try:
            if document.needs_pass:
                raise CvCorruptFileError(
                    message="Password-protected PDF documents are not supported",
                    raw_cv_id=raw_cv_id,
                )

            page_count = document.page_count

            if page_count <= 0:
                raise CvTextNotExtractableError(raw_cv_id=raw_cv_id)

            if page_count > self._settings.max_pdf_pages:
                raise CvCorruptFileError(
                    message="The PDF document contains too many pages",
                    raw_cv_id=raw_cv_id,
                )

            page_texts: list[str] = []
            multi_column_pages = 0

            for page_index in range(page_count):
                page = document.load_page(page_index)
                page_texts.append(page.get_text("text", sort=True))

                if self._looks_multi_column(page):
                    multi_column_pages += 1

            text = "\n\n".join(page_texts).strip()

            if len(self._meaningful_characters(text)) < (
                    self._settings.min_text_characters
            ):
                raise CvTextNotExtractableError(raw_cv_id=raw_cv_id)

            warnings: list[str] = []

            if multi_column_pages >= max(1, math.ceil(page_count / 3)):
                warnings.extend(
                    [
                        "MULTI_COLUMN_LAYOUT_SUSPECTED",
                        "TEXT_LAYOUT_MAY_BE_LOST",
                    ]
                )

            return ExtractionResult(
                text=text,
                warnings=tuple(dict.fromkeys(warnings)),
                page_count=page_count,
            )
        except CvCorruptFileError:
            raise
        except CvTextNotExtractableError:
            raise
        except (fitz.FileDataError, RuntimeError, ValueError) as exception:
            raise CvCorruptFileError(
                message="The PDF document could not be processed",
                raw_cv_id=raw_cv_id,
            ) from exception
        finally:
            document.close()

    @staticmethod
    def _meaningful_characters(text: str) -> str:
        return "".join(
            character
            for character in text
            if character.isalnum()
        )

    @staticmethod
    def _looks_multi_column(page: fitz.Page) -> bool:
        blocks = page.get_text("blocks")

        if len(blocks) < 6:
            return False

        page_width = float(page.rect.width)
        if page_width <= 0:
            return False

        centers: list[float] = []

        for block in blocks:
            x0 = float(block[0])
            x1 = float(block[2])
            text = str(block[4]).strip()

            if len(text) < 10:
                continue

            centers.append((x0 + x1) / 2.0)

        if len(centers) < 6:
            return False

        left_centers = [
            center
            for center in centers
            if center < page_width * 0.46
        ]
        right_centers = [
            center
            for center in centers
            if center > page_width * 0.54
        ]

        if len(left_centers) < 2 or len(right_centers) < 2:
            return False

        left_median = statistics.median(left_centers)
        right_median = statistics.median(right_centers)

        return right_median - left_median > page_width * 0.25