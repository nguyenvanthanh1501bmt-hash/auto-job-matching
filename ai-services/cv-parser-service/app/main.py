from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from pathlib import Path
from typing import AsyncIterator

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app import __version__
from app.api.cv_routes import router as cv_router
from app.config import Settings, get_settings
from app.exceptions import CvInternalError, CvParserError
from app.extraction.doc_extractor import DocExtractor
from app.extraction.extractor_factory import ExtractorFactory
from app.normalization.text_normalizer import TextNormalizer
from app.schemas import (
    CvErrorResponse,
    HealthResponse,
    ReadyResponse,
)
from app.storage.minio_storage import MinioStorage
from app.taxonomy.taxonomy_loader import (
    TaxonomyBundle,
    TaxonomyLoader,
)


LOGGER = logging.getLogger("autojob.cv_parser")


def configure_logging(
        level: str,
) -> None:
    logging.basicConfig(
        level=getattr(logging, level),
        format=(
            "%(asctime)s %(levelname)s "
            "%(name)s %(message)s"
        ),
    )


def resolve_taxonomy_directory(
        configured_path: str,
) -> Path:
    path = Path(configured_path)

    if path.is_absolute():
        return path

    project_root = Path(__file__).resolve().parent.parent
    return project_root / path


@asynccontextmanager
async def lifespan(
        app: FastAPI,
) -> AsyncIterator[None]:
    settings = get_settings()
    configure_logging(settings.log_level)

    taxonomy_directory = resolve_taxonomy_directory(
        settings.taxonomy_directory
    )
    taxonomy = TaxonomyLoader(
        directory=taxonomy_directory,
        expected_version=settings.parser_version,
    ).load()

    app.state.settings = settings
    app.state.taxonomy = taxonomy
    app.state.storage = MinioStorage(settings)
    app.state.extractor_factory = ExtractorFactory(settings)
    app.state.text_normalizer = TextNormalizer(settings)

    from app.parsing.profile_parser import ProfileParser

    app.state.profile_parser = ProfileParser(
        settings=settings,
        taxonomy=taxonomy,
    )

    LOGGER.info(
        "CV parser service started parserVersion=%s taxonomyVersion=%s",
        settings.parser_version,
        taxonomy.version,
    )

    yield

    LOGGER.info("CV parser service stopped")


app = FastAPI(
    title="AutoJob CV Parser Service",
    version=__version__,
    lifespan=lifespan,
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)

app.include_router(cv_router)


@app.exception_handler(CvParserError)
async def handle_cv_parser_error(
        request: Request,
        exception: CvParserError,
) -> JSONResponse:
    LOGGER.warning(
        "CV parser request failed code=%s rawCvId=%s path=%s",
        exception.code,
        exception.raw_cv_id,
        request.url.path,
    )

    body = CvErrorResponse(
        code=exception.code,
        message=exception.message,
        rawCvId=exception.raw_cv_id,
    )

    return JSONResponse(
        status_code=exception.http_status,
        content=body.model_dump(
            by_alias=True,
            exclude_none=True,
        ),
    )


@app.exception_handler(RequestValidationError)
async def handle_request_validation_error(
        request: Request,
        exception: RequestValidationError,
) -> JSONResponse:
    raw_cv_id = None

    try:
        body = await request.json()
        candidate = body.get("rawCvId")
        if isinstance(candidate, str):
            raw_cv_id = candidate[:100]
    except Exception:
        raw_cv_id = None

    LOGGER.warning(
        "CV parser request validation failed rawCvId=%s path=%s",
        raw_cv_id,
        request.url.path,
    )

    response = CvErrorResponse(
        code="CV_INVALID_REQUEST",
        message="The CV parse request is invalid",
        rawCvId=raw_cv_id,
    )

    return JSONResponse(
        status_code=400,
        content=response.model_dump(
            by_alias=True,
            exclude_none=True,
        ),
    )


@app.exception_handler(Exception)
async def handle_unexpected_error(
        request: Request,
        exception: Exception,
) -> JSONResponse:
    LOGGER.exception(
        "Unexpected CV parser error path=%s",
        request.url.path,
    )

    error = CvInternalError()

    body = CvErrorResponse(
        code=error.code,
        message=error.message,
        rawCvId=None,
    )

    return JSONResponse(
        status_code=error.http_status,
        content=body.model_dump(
            by_alias=True,
            exclude_none=True,
        ),
    )


@app.get(
    "/health",
    response_model=HealthResponse,
)
def health() -> HealthResponse:
    return HealthResponse(status="UP")


@app.get(
    "/ready",
    response_model=ReadyResponse,
    response_model_by_alias=True,
)
def ready(
        request: Request,
) -> ReadyResponse:
    settings: Settings = request.app.state.settings
    taxonomy: TaxonomyBundle = request.app.state.taxonomy
    storage: MinioStorage = request.app.state.storage

    details: list[str] = []

    minio_ready = storage.check_readiness()
    doc_ready = DocExtractor.is_ready()

    if not minio_ready:
        details.append(
            "MinIO bucket is unavailable"
        )

    if not doc_ready:
        details.append(
            "antiword is unavailable"
        )

    ready_status = (
        "UP"
        if minio_ready and doc_ready
        else "DOWN"
    )

    return ReadyResponse(
        status=ready_status,
        parser_version=settings.parser_version,
        taxonomy_version=taxonomy.version,
        minio="UP" if minio_ready else "DOWN",
        doc_extractor="UP" if doc_ready else "DOWN",
        details=details,
    )