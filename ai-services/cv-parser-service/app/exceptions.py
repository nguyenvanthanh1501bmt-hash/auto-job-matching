from __future__ import annotations

from dataclasses import dataclass

from fastapi import status


@dataclass(slots=True)
class CvParserError(Exception):
    code: str
    message: str
    http_status: int
    raw_cv_id: str | None = None

    def __str__(self) -> str:
        return self.message


class CvInvalidRequestError(CvParserError):
    def __init__(
            self,
            message: str,
            raw_cv_id: str | None = None,
    ) -> None:
        super().__init__(
            code="CV_INVALID_REQUEST",
            message=message,
            http_status=status.HTTP_400_BAD_REQUEST,
            raw_cv_id=raw_cv_id,
        )


class CvObjectNotFoundError(CvParserError):
    def __init__(
            self,
            raw_cv_id: str | None = None,
    ) -> None:
        super().__init__(
            code="CV_OBJECT_NOT_FOUND",
            message="The CV object was not found in object storage",
            http_status=status.HTTP_404_NOT_FOUND,
            raw_cv_id=raw_cv_id,
        )


class CvFileTooLargeError(CvParserError):
    def __init__(
            self,
            raw_cv_id: str | None = None,
    ) -> None:
        super().__init__(
            code="CV_FILE_TOO_LARGE",
            message="The CV file exceeds the configured size limit",
            http_status=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            raw_cv_id=raw_cv_id,
        )


class CvUnsupportedFormatError(CvParserError):
    def __init__(
            self,
            message: str = "The CV file format is not supported",
            raw_cv_id: str | None = None,
    ) -> None:
        super().__init__(
            code="CV_UNSUPPORTED_FORMAT",
            message=message,
            http_status=status.HTTP_422_UNPROCESSABLE_CONTENT,
            raw_cv_id=raw_cv_id,
        )


class CvCorruptFileError(CvParserError):
    def __init__(
            self,
            message: str = "The CV document is corrupt or malformed",
            raw_cv_id: str | None = None,
    ) -> None:
        super().__init__(
            code="CV_CORRUPT_FILE",
            message=message,
            http_status=status.HTTP_422_UNPROCESSABLE_CONTENT,
            raw_cv_id=raw_cv_id,
        )


class CvTextNotExtractableError(CvParserError):
    def __init__(
            self,
            raw_cv_id: str | None = None,
    ) -> None:
        super().__init__(
            code="CV_TEXT_NOT_EXTRACTABLE",
            message="No extractable text was found in the document",
            http_status=status.HTTP_422_UNPROCESSABLE_CONTENT,
            raw_cv_id=raw_cv_id,
        )


class CvDocExtractionFailedError(CvParserError):
    def __init__(
            self,
            message: str = "The legacy DOC document could not be extracted",
            raw_cv_id: str | None = None,
    ) -> None:
        super().__init__(
            code="CV_DOC_EXTRACTION_FAILED",
            message=message,
            http_status=status.HTTP_422_UNPROCESSABLE_CONTENT,
            raw_cv_id=raw_cv_id,
        )


class CvExtractionTimeoutError(CvParserError):
    def __init__(
            self,
            raw_cv_id: str | None = None,
    ) -> None:
        super().__init__(
            code="CV_EXTRACTION_TIMEOUT",
            message="CV text extraction exceeded the configured timeout",
            http_status=status.HTTP_504_GATEWAY_TIMEOUT,
            raw_cv_id=raw_cv_id,
        )


class CvInternalError(CvParserError):
    def __init__(
            self,
            raw_cv_id: str | None = None,
    ) -> None:
        super().__init__(
            code="CV_INTERNAL_ERROR",
            message="The CV parser encountered an internal error",
            http_status=status.HTTP_500_INTERNAL_SERVER_ERROR,
            raw_cv_id=raw_cv_id,
        )