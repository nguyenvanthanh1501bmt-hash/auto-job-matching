from __future__ import annotations

from pathlib import PurePath

from app.config import Settings
from app.exceptions import CvUnsupportedFormatError
from app.extraction.base import DocumentExtractor
from app.extraction.doc_extractor import DocExtractor
from app.extraction.docx_extractor import DocxExtractor
from app.extraction.pdf_extractor import PdfExtractor


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
        self._pdf_extractor = PdfExtractor(settings)
        self._docx_extractor = DocxExtractor(settings)
        self._doc_extractor = DocExtractor(settings)

    def create(
            self,
            original_filename: str,
            content_type: str,
            data: bytes,
            raw_cv_id: str | None = None,
    ) -> DocumentExtractor:
        extension = PurePath(original_filename).suffix.lower()
        normalized_content_type = (
            content_type.split(";", maxsplit=1)[0]
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
            return self._pdf_extractor

        if extension == ".docx":
            self._validate_content_type(
                normalized_content_type,
                DOCX_CONTENT_TYPES,
                extension,
                raw_cv_id,
            )
            return self._docx_extractor

        self._validate_content_type(
            normalized_content_type,
            DOC_CONTENT_TYPES,
            extension,
            raw_cv_id,
        )
        return self._doc_extractor

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
                    f"The content type does not match the {extension} "
                    "document format"
                ),
                raw_cv_id=raw_cv_id,
            )