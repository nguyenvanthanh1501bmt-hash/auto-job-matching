from __future__ import annotations

import io
import os
from pathlib import Path
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from minio import Minio
from minio.error import S3Error

import app.main as main_module
from app.config import Settings
from app.main import app
from tests.fixtures.generate_fixtures import (
    GeneratedCvFixture,
    generate_integration_fixtures,
)


RUN_MINIO_INTEGRATION = (
        os.getenv(
            "RUN_MINIO_INTEGRATION",
            "",
        )
        .strip()
        .casefold()
        in {
            "1",
            "true",
            "yes",
            "on",
        }
)

SERVICE_ROOT = (
    Path(__file__)
    .resolve()
    .parents[2]
)

REPOSITORY_ROOT = (
    SERVICE_ROOT
    .parents[1]
)

pytestmark = [
    pytest.mark.integration,
    pytest.mark.skipif(
        not RUN_MINIO_INTEGRATION,
        reason=(
            "Set RUN_MINIO_INTEGRATION=true "
            "to run tests requiring real MinIO"
        ),
    ),
]


def _test_settings() -> Settings:
    return Settings(
        MINIO_ENDPOINT=os.getenv(
            "CV_TEST_MINIO_ENDPOINT",
            "127.0.0.1:9000",
        ),
        MINIO_ACCESS_KEY=os.getenv(
            "CV_TEST_MINIO_ACCESS_KEY",
            "minioadmin",
        ),
        MINIO_SECRET_KEY=os.getenv(
            "CV_TEST_MINIO_SECRET_KEY",
            "minioadmin",
        ),
        MINIO_SECURE=(
                os.getenv(
                    "CV_TEST_MINIO_SECURE",
                    "false",
                )
                .strip()
                .casefold()
                in {
                    "1",
                    "true",
                    "yes",
                    "on",
                }
        ),
        MINIO_BUCKET_CVS=os.getenv(
            "CV_TEST_MINIO_BUCKET",
            "autojob-cvs",
        ),
        CV_TAXONOMY_DIRECTORY=str(
            REPOSITORY_ROOT
            / "configs"
            / "taxonomy"
            / "cv-parser"
        ),
        CV_ALLOWED_OBJECT_PREFIXES=(
            "raw/,cvs/"
        ),
    )


def _minio_client(
        settings: Settings,
) -> Minio:
    return Minio(
        endpoint=settings.minio_endpoint,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        secure=settings.minio_secure,
    )


def _ensure_bucket(
        client: Minio,
        bucket: str,
) -> None:
    try:
        if client.bucket_exists(bucket):
            return

        client.make_bucket(bucket)
    except S3Error as exception:
        if exception.code in {
            "BucketAlreadyExists",
            "BucketAlreadyOwnedByYou",
        }:
            return

        pytest.fail(
            (
                "Unable to prepare MinIO bucket "
                f"{bucket!r}: {exception}"
            ),
            pytrace=False,
        )
    except Exception as exception:
        pytest.fail(
            (
                "Unable to connect to MinIO. "
                "Start it with "
                "'docker compose up -d minio minio-init'. "
                f"Original error: {exception}"
            ),
            pytrace=False,
        )


def _upload_fixture(
        *,
        client: Minio,
        bucket: str,
        object_key: str,
        fixture: GeneratedCvFixture,
) -> None:
    client.put_object(
        bucket_name=bucket,
        object_name=object_key,
        data=io.BytesIO(
            fixture.data
        ),
        length=len(
            fixture.data
        ),
        content_type=(
            fixture.content_type
        ),
    )


def _remove_objects(
        *,
        client: Minio,
        bucket: str,
        object_keys: list[str],
) -> None:
    for object_key in object_keys:
        try:
            client.remove_object(
                bucket_name=bucket,
                object_name=object_key,
            )
        except Exception:
            continue


def test_parse_three_multidomain_cvs_from_real_minio(
        monkeypatch: pytest.MonkeyPatch,
) -> None:
    settings = _test_settings()
    client = _minio_client(settings)

    _ensure_bucket(
        client,
        settings.minio_bucket,
    )

    run_id = uuid4().hex
    uploaded_object_keys: list[str] = []

    fixtures = (
        generate_integration_fixtures()
    )

    monkeypatch.setattr(
        main_module,
        "get_settings",
        lambda: settings,
    )

    try:
        for fixture in fixtures:
            object_key = (
                f"cvs/integration/{run_id}/"
                f"{fixture.filename}"
            )

            _upload_fixture(
                client=client,
                bucket=settings.minio_bucket,
                object_key=object_key,
                fixture=fixture,
            )

            uploaded_object_keys.append(
                object_key
            )

        with TestClient(
                app,
                raise_server_exceptions=False,
        ) as api_client:
            for fixture, object_key in zip(
                    fixtures,
                    uploaded_object_keys,
                    strict=True,
            ):
                response = api_client.post(
                    "/api/v1/cv/parse",
                    json={
                        "rawCvId": (
                            fixture.raw_cv_id
                        ),
                        "bucket": (
                            settings.minio_bucket
                        ),
                        "objectKey": object_key,
                        "originalFilename": (
                            fixture.filename
                        ),
                        "contentType": (
                            fixture.content_type
                        ),
                    },
                )

                assert (
                        response.status_code
                        == 200
                ), response.text

                body = response.json()

                assert body["rawCvId"] == (
                    fixture.raw_cv_id
                )

                assert body["parserVersion"] == (
                    settings.parser_version
                )

                assert (
                        body["extractedTextLength"]
                        > 100
                )

                assert (
                        body["detectedLanguage"]
                        == "EN"
                )

                profile = body["profile"]

                assert profile["fullName"] == (
                    fixture.expected_full_name
                )

                assert profile["headline"] == (
                    fixture.expected_headline
                )

                assert (
                        profile["contact"]["email"]
                        is not None
                )

                assert len(
                    profile["workExperiences"]
                ) >= 1

                work_experience = (
                    profile[
                        "workExperiences"
                    ][0]
                )

                assert (
                        work_experience[
                            "normalizedJobTitle"
                        ]
                        == (
                            fixture
                            .expected_normalized_job_title
                        )
                )

                normalized_skills = {
                    skill["normalizedName"]
                    for skill
                    in profile["skills"]
                }

                assert (
                        fixture.expected_skill
                        in normalized_skills
                )

                assert len(
                    profile["educations"]
                ) >= 1

                assert (
                    profile["rawText"]
                    .strip()
                )

                assert len(
                    profile["sections"]
                ) >= 4

                assert (
                        profile["parseQuality"][
                            "overallScore"
                        ]
                        > 0.0
                )

                assert (
                        body["warnings"]
                        == profile[
                            "parserWarnings"
                        ]
                )
    finally:
        _remove_objects(
            client=client,
            bucket=settings.minio_bucket,
            object_keys=(
                uploaded_object_keys
            ),
        )


def test_real_minio_missing_object_maps_to_404(
        monkeypatch: pytest.MonkeyPatch,
) -> None:
    settings = _test_settings()
    client = _minio_client(settings)

    _ensure_bucket(
        client,
        settings.minio_bucket,
    )

    raw_cv_id = (
        "integration-missing-object"
    )

    missing_object_key = (
        "cvs/integration/"
        f"{uuid4().hex}/missing.pdf"
    )

    monkeypatch.setattr(
        main_module,
        "get_settings",
        lambda: settings,
    )

    with TestClient(
            app,
            raise_server_exceptions=False,
    ) as api_client:
        response = api_client.post(
            "/api/v1/cv/parse",
            json={
                "rawCvId": raw_cv_id,
                "bucket": (
                    settings.minio_bucket
                ),
                "objectKey": (
                    missing_object_key
                ),
                "originalFilename": (
                    "missing.pdf"
                ),
                "contentType": (
                    "application/pdf"
                ),
            },
        )

    assert response.status_code == 404

    assert response.json() == {
        "code": "CV_OBJECT_NOT_FOUND",
        "message": (
            "The CV object was not found "
            "in object storage"
        ),
        "rawCvId": raw_cv_id,
    }