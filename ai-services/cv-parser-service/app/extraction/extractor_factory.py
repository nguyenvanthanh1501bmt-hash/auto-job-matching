from __future__ import annotations

from pathlib import PurePath

from app.config import Settings
from app.exceptions import CvUnsupportedFormatError
from app.extraction.base import DocumentExtractor


PDF_CONTENT_TYPES = {
    "application/pdf",
}

DOCX_CONTENT_TYPES = {
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/docx",
}

DOC_CONTENT_TYPES = {
    "application/msword",
    "application/doc",
    "application/vnd.ms-word",
    "application/x-msword",
    "zz-application/zz-winassoc-doc",
}

SUPPORTED_EXTENSIONS = {
    ".pdf",
    ".docx",
    ".doc",
}


class ExtractorFactory:
    def __init__(
            self,
            settings: Settings,
    ) -> None:
        self._settings = settings

    def create(
            self,
            original_filename: str,
            content_type: str,
            data: bytes,
            raw_cv_id: str | None = None,
    ) -> DocumentExtractor:
        del data

        extension = PurePath(
            original_filename
        ).suffix.lower()

        normalized_content_type = (
            content_type
            .split(
                ";",
                maxsplit=1,
            )[0]
            .strip()
            .lower()
        )

        if extension not in SUPPORTED_EXTENSIONS:
            raise CvUnsupportedFormatError(
                raw_cv_id=raw_cv_id
            )

        if extension == ".pdf":
            self._validate_content_type(
                normalized_content_type,
                PDF_CONTENT_TYPES,
                extension,
                raw_cv_id,
            )

            from app.extraction.pdf_extractor import PdfExtractor

            return PdfExtractor(
                self._settings
            )

        if extension == ".docx":
            self._validate_content_type(
                normalized_content_type,
                DOCX_CONTENT_TYPES,
                extension,
                raw_cv_id,
            )

            from app.extraction.docx_extractor import DocxExtractor

            return DocxExtractor(
                self._settings
            )

        self._validate_content_type(
            normalized_content_type,
            DOC_CONTENT_TYPES,
            extension,
            raw_cv_id,
        )

        from app.extraction.doc_extractor import DocExtractor

        return DocExtractor(
            self._settings
        )

    @staticmethod
    def _validate_content_type(
            content_type: str,
            allowed_types: set[str],
            extension: str,
            raw_cv_id: str | None,
    ) -> None:
        if content_type not in allowed_types:
            raise CvUnsupportedFormatError(
                message=(
                    "The content type does not match "
                    f"the {extension} document format"
                ),
                raw_cv_id=raw_cv_id,
            )