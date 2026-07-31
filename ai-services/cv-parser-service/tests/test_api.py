from __future__ import annotations

from collections.abc import Generator
from dataclasses import dataclass

import pytest
from fastapi.testclient import TestClient

import app.api.cv_routes as cv_routes
import app.main as main_module
from app.config import Settings
from app.exceptions import (
    CvObjectNotFoundError,
    CvUnsupportedFormatError,
)
from app.extraction.base import ExtractionResult
from app.extraction.doc_extractor import DocExtractor
from app.main import app
from app.storage.minio_storage import StoredObject


API_CV_TEXT = """
JANE CARTER
Warehouse Supervisor
jane.carter@example.com | +44 7700 900123
Preferred Location: Ho Chi Minh City
Preferred Work Mode: Onsite
Preferred Employment Type: Full-time

PROFESSIONAL SUMMARY
Warehouse supervisor experienced in inventory control,
safety, order fulfillment and team supervision.

CAREER OBJECTIVE
Seeking a warehouse supervisor role in logistics operations.

SKILLS
Inventory Management, Warehouse Management,
Order Fulfillment, Occupational Safety,
Forklift, Barcode Scanner

WORK EXPERIENCE
Warehouse Supervisor | Global Distribution Center
03/2021 - 06/2025
- Supervised inbound and outbound warehouse operations.
- Improved inventory accuracy by 20 percent.
- Operated forklifts and barcode scanners safely.

EDUCATION
Diploma in Logistics and Supply Chain Management
City Technical College
2017 - 2019

LICENSES
Forklift Operator License
Issuing Authority: Workplace Safety Authority
Issued: 2023-01
Expires: 2028-01

LANGUAGES
English - Native
""".strip()


@dataclass(slots=True)
class FakeStorage:
    data: bytes = b"cv-content"
    ready: bool = True
    error: Exception | None = None

    def get_object(
            self,
            bucket: str,
            object_key: str,
            raw_cv_id: str | None = None,
    ) -> StoredObject:
        del bucket
        del object_key

        if self.error is not None:
            raise self.error

        return StoredObject(
            data=self.data,
            size=len(self.data),
            content_type="application/pdf",
            etag="fake-etag",
        )

    def check_readiness(self) -> bool:
        return self.ready


@dataclass(slots=True)
class FakeExtractor:
    text: str = API_CV_TEXT
    warnings: tuple[str, ...] = ()
    error: Exception | None = None

    def extract(
            self,
            data: bytes,
            raw_cv_id: str | None = None,
    ) -> ExtractionResult:
        del data
        del raw_cv_id

        if self.error is not None:
            raise self.error

        return ExtractionResult(
            text=self.text,
            warnings=self.warnings,
            page_count=1,
        )


@dataclass(slots=True)
class FakeExtractorFactory:
    extractor: FakeExtractor | None = None
    error: Exception | None = None

    def create(
            self,
            original_filename: str,
            content_type: str,
            data: bytes,
            raw_cv_id: str | None = None,
    ) -> FakeExtractor:
        del original_filename
        del content_type
        del data

        if self.error is not None:
            raise self.error

        if self.extractor is None:
            raise RuntimeError(
                "Fake extractor was not configured"
            )

        return self.extractor


@pytest.fixture
def api_client(
        settings: Settings,
        monkeypatch: pytest.MonkeyPatch,
) -> Generator[TestClient, None, None]:
    monkeypatch.setattr(
        main_module,
        "get_settings",
        lambda: settings,
    )

    with TestClient(
            app,
            raise_server_exceptions=False,
    ) as client:
        yield client


def _valid_request() -> dict[str, str]:
    return {
        "rawCvId": "raw-api-1",
        "bucket": "autojob-cvs",
        "objectKey": (
            "cvs/2026/07/31/"
            "candidate/cv.pdf"
        ),
        "originalFilename": "cv.pdf",
        "contentType": "application/pdf",
    }


def test_health_returns_up(
        api_client: TestClient,
) -> None:
    response = api_client.get(
        "/health"
    )

    assert response.status_code == 200

    assert response.json() == {
        "status": "UP",
    }


def test_ready_returns_up_when_dependencies_are_ready(
        api_client: TestClient,
        monkeypatch: pytest.MonkeyPatch,
) -> None:
    app.state.storage = FakeStorage(
        ready=True
    )

    monkeypatch.setattr(
        DocExtractor,
        "is_ready",
        staticmethod(
            lambda: True
        ),
    )

    response = api_client.get(
        "/ready"
    )

    assert response.status_code == 200

    assert response.json() == {
        "status": "UP",
        "parserVersion": "rule-v1",
        "taxonomyVersion": "rule-v1",
        "minio": "UP",
        "docExtractor": "UP",
        "details": [],
    }


def test_ready_returns_down_when_dependencies_are_unavailable(
        api_client: TestClient,
        monkeypatch: pytest.MonkeyPatch,
) -> None:
    app.state.storage = FakeStorage(
        ready=False
    )

    monkeypatch.setattr(
        DocExtractor,
        "is_ready",
        staticmethod(
            lambda: False
        ),
    )

    response = api_client.get(
        "/ready"
    )

    assert response.status_code == 200

    body = response.json()

    assert body["status"] == "DOWN"
    assert body["minio"] == "DOWN"
    assert body["docExtractor"] == "DOWN"

    assert (
            "MinIO bucket is unavailable"
            in body["details"]
    )

    assert (
            "antiword is unavailable"
            in body["details"]
    )


def test_parse_api_returns_structured_candidate_profile(
        api_client: TestClient,
) -> None:
    app.state.storage = FakeStorage()

    app.state.extractor_factory = (
        FakeExtractorFactory(
            extractor=FakeExtractor(
                warnings=(
                    "TEXT_LAYOUT_MAY_BE_LOST",
                )
            )
        )
    )

    response = api_client.post(
        "/api/v1/cv/parse",
        json=_valid_request(),
    )

    assert response.status_code == 200

    body = response.json()

    assert body["rawCvId"] == "raw-api-1"
    assert body["parserVersion"] == "rule-v1"

    assert (
            body["extractedTextLength"]
            == len(API_CV_TEXT)
    )

    assert body["detectedLanguage"] == "EN"

    profile = body["profile"]

    assert profile["fullName"] == "JANE CARTER"

    assert (
            profile["headline"]
            == "Warehouse Supervisor"
    )

    assert profile["contact"]["email"] == (
        "jane.carter@example.com"
    )

    assert profile["preferredLocations"] == [
        "Ho Chi Minh City",
    ]

    assert profile["preferredWorkModes"] == [
        "ONSITE",
    ]

    assert profile[
               "preferredEmploymentTypes"
           ] == [
               "FULL_TIME",
           ]

    assert len(
        profile["workExperiences"]
    ) == 1

    assert (
            profile["workExperiences"][0][
                "normalizedJobTitle"
            ]
            == "WAREHOUSE_SUPERVISOR"
    )

    assert len(profile["educations"]) == 1
    assert len(profile["licenses"]) == 1

    assert (
            profile["licenses"][0]["name"]
            == "Forklift Operator License"
    )

    assert (
            "TEXT_LAYOUT_MAY_BE_LOST"
            in profile["parserWarnings"]
    )

    assert (
            body["warnings"]
            == profile["parserWarnings"]
    )

    assert (
            0.0
            <= profile["parseQuality"][
                "overallScore"
            ]
            <= 1.0
    )

    assert profile["rawText"] == API_CV_TEXT

    assert all(
        section["text"] is None
        for section in profile["sections"]
    )


def test_parse_api_returns_invalid_request_for_unsafe_object_key(
        api_client: TestClient,
) -> None:
    payload = _valid_request()

    payload["objectKey"] = (
        "cvs/2026/../private/cv.pdf"
    )

    response = api_client.post(
        "/api/v1/cv/parse",
        json=payload,
    )

    assert response.status_code == 400

    assert response.json() == {
        "code": "CV_INVALID_REQUEST",
        "message": (
            "The CV parse request is invalid"
        ),
        "rawCvId": "raw-api-1",
    }


def test_parse_api_rejects_extra_request_fields(
        api_client: TestClient,
) -> None:
    payload = _valid_request()

    payload["externalUrl"] = (
        "https://example.com/cv.pdf"
    )

    response = api_client.post(
        "/api/v1/cv/parse",
        json=payload,
    )

    assert response.status_code == 400

    assert response.json()["code"] == (
        "CV_INVALID_REQUEST"
    )


def test_parse_api_maps_missing_minio_object(
        api_client: TestClient,
) -> None:
    app.state.storage = FakeStorage(
        error=CvObjectNotFoundError(
            raw_cv_id="raw-api-1"
        )
    )

    response = api_client.post(
        "/api/v1/cv/parse",
        json=_valid_request(),
    )

    assert response.status_code == 404

    assert response.json() == {
        "code": "CV_OBJECT_NOT_FOUND",
        "message": (
            "The CV object was not found "
            "in object storage"
        ),
        "rawCvId": "raw-api-1",
    }


def test_parse_api_maps_unsupported_format(
        api_client: TestClient,
) -> None:
    app.state.storage = FakeStorage()

    app.state.extractor_factory = (
        FakeExtractorFactory(
            error=CvUnsupportedFormatError(
                raw_cv_id="raw-api-1"
            )
        )
    )

    response = api_client.post(
        "/api/v1/cv/parse",
        json=_valid_request(),
    )

    assert response.status_code == 422

    assert response.json() == {
        "code": "CV_UNSUPPORTED_FORMAT",
        "message": (
            "The CV file format is not supported"
        ),
        "rawCvId": "raw-api-1",
    }


def test_parse_api_maps_extraction_timeout(
        api_client: TestClient,
) -> None:
    app.state.storage = FakeStorage()

    app.state.extractor_factory = (
        FakeExtractorFactory(
            extractor=FakeExtractor(
                error=TimeoutError(
                    "simulated timeout"
                )
            )
        )
    )

    response = api_client.post(
        "/api/v1/cv/parse",
        json=_valid_request(),
    )

    assert response.status_code == 504

    assert response.json() == {
        "code": "CV_EXTRACTION_TIMEOUT",
        "message": (
            "CV text extraction exceeded "
            "the configured timeout"
        ),
        "rawCvId": "raw-api-1",
    }


def test_parse_api_sanitizes_unexpected_internal_error(
        api_client: TestClient,
) -> None:
    app.state.storage = FakeStorage(
        error=RuntimeError(
            "secret internal implementation detail"
        )
    )

    response = api_client.post(
        "/api/v1/cv/parse",
        json=_valid_request(),
    )

    assert response.status_code == 500

    body = response.json()

    assert body["code"] == "CV_INTERNAL_ERROR"

    assert body["message"] == (
        "The CV parser encountered "
        "an internal error"
    )

    assert (
            "secret internal implementation detail"
            not in response.text
    )

    assert "rawCvId" not in body


def test_request_validation_handles_malformed_json(
        api_client: TestClient,
) -> None:
    response = api_client.post(
        "/api/v1/cv/parse",
        content="{invalid-json",
        headers={
            "Content-Type": "application/json",
        },
    )

    assert response.status_code == 400

    assert response.json() == {
        "code": "CV_INVALID_REQUEST",
        "message": (
            "The CV parse request is invalid"
        ),
    }


def test_taxonomy_load_failure_prevents_service_startup(
        settings: Settings,
        monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        main_module,
        "get_settings",
        lambda: settings,
    )

    def fail_taxonomy_load(
            loader,
    ):
        del loader

        raise RuntimeError(
            "taxonomy validation failed"
        )

    monkeypatch.setattr(
        main_module.TaxonomyLoader,
        "load",
        fail_taxonomy_load,
    )

    with pytest.raises(
            RuntimeError,
            match="taxonomy validation failed",
    ):
        with TestClient(
                app,
                raise_server_exceptions=True,
        ):
            pass