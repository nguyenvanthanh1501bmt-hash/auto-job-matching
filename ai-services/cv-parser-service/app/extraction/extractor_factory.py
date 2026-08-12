from __future__ import annotations

from pathlib import PurePath

from app.config import Settings
from app.exceptions import CvUnsupportedFormatError
from app.extraction.base import DocumentExtractor


# Các MIME type được chấp nhận cho từng định dạng file.
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

# Các phần mở rộng mà CV parser hỗ trợ.
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
        # Factory không cần đọc data ở bước chọn extractor.
        del data

        # Lấy extension từ tên file và chuyển về lowercase.
        extension = PurePath(
            original_filename
        ).suffix.lower()

        # Chuẩn hóa Content-Type, bỏ phần parameters phía sau dấu ";".
        normalized_content_type = (
            content_type
            .split(
                ";",
                maxsplit=1,
            )[0]
            .strip()
            .lower()
        )

        # Từ chối những định dạng file không được hỗ trợ.
        if extension not in SUPPORTED_EXTENSIONS:
            raise CvUnsupportedFormatError(
                raw_cv_id=raw_cv_id
            )

        # Chọn extractor tương ứng với file PDF.
        if extension == ".pdf":
            self._validate_content_type(
                normalized_content_type,
                PDF_CONTENT_TYPES,
                extension,
                raw_cv_id,
            )

            # Import tại đây để tránh load dependency không cần thiết
            # khi extractor này không được sử dụng.
            from app.extraction.pdf_extractor import PdfExtractor

            return PdfExtractor(
                self._settings
            )

        # Chọn extractor tương ứng với file DOCX.
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

        # Phần còn lại là DOC vì các extension khác đã bị reject ở trên.
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
        # Đảm bảo MIME type khớp với extension của file.
        if content_type not in allowed_types:
            raise CvUnsupportedFormatError(
                message=(
                    "The content type does not match "
                    f"the {extension} document format"
                ),
                raw_cv_id=raw_cv_id,
            )