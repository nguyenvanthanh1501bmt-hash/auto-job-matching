from __future__ import annotations

import asyncio
from typing import Protocol

from fastapi import APIRouter, Request

from app.config import Settings
from app.exceptions import CvExtractionTimeoutError
from app.extraction.extractor_factory import ExtractorFactory
from app.normalization.text_normalizer import TextNormalizer
from app.schemas import ParseCvRequest, ParseCvResponse
from app.storage.minio_storage import MinioStorage


class ProfileParserProtocol(Protocol):
    def parse(
            self,
            raw_cv_id: str,
            raw_text: str,
            extraction_warnings: tuple[str, ...],
    ) -> ParseCvResponse:
        ...


router = APIRouter(
    prefix="/api/v1/cv",
    tags=["cv-parser"],
)


@router.post(
    "/parse",
    response_model=ParseCvResponse,
    response_model_by_alias=True,
)
async def parse_cv(
        payload: ParseCvRequest,
        request: Request,
) -> ParseCvResponse:
    # Lấy các dependency đã được khởi tạo trong application lifespan.
    settings: Settings = request.app.state.settings
    storage: MinioStorage = request.app.state.storage
    extractor_factory: ExtractorFactory = (
        request.app.state.extractor_factory
    )
    text_normalizer: TextNormalizer = (
        request.app.state.text_normalizer
    )
    profile_parser: ProfileParserProtocol = (
        request.app.state.profile_parser
    )

    # Đọc file CV từ MinIO.
    # Chạy synchronous I/O trong thread để không block event loop.
    stored_object = await asyncio.to_thread(
        storage.get_object,
        payload.bucket,
        payload.object_key,
        payload.raw_cv_id,
    )

    # Chọn extractor phù hợp dựa trên filename, content type và dữ liệu file.
    extractor = extractor_factory.create(
        original_filename=payload.original_filename,
        content_type=payload.content_type,
        data=stored_object.data,
        raw_cv_id=payload.raw_cv_id,
    )

    try:
        # Extraction có thể tốn thời gian nên chạy trong threadpool
        # và giới hạn thời gian xử lý bằng timeout.
        extraction_result = await asyncio.wait_for(
            asyncio.to_thread(
                extractor.extract,
                stored_object.data,
                payload.raw_cv_id,
            ),
            timeout=settings.extraction_timeout_seconds,
        )
    except TimeoutError as exception:
        # Chuyển timeout thành lỗi nghiệp vụ của CV parser.
        raise CvExtractionTimeoutError(
            raw_cv_id=payload.raw_cv_id
        ) from exception

    # Chuẩn hóa text sau khi đã extract từ file.
    normalized_text = text_normalizer.normalize(
        extraction_result.text
    )

    # Gộp warning từ extraction và normalization,
    # đồng thời loại bỏ warning bị trùng.
    warnings = tuple(
        dict.fromkeys(
            (
                *extraction_result.warnings,
                *normalized_text.warnings,
            )
        )
    )

    # Đưa text đã chuẩn hóa vào ProfileParser để phân tích CV.
    return profile_parser.parse(
        raw_cv_id=payload.raw_cv_id,
        raw_text=normalized_text.text,
        extraction_warnings=warnings,
    )