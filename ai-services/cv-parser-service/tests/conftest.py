from __future__ import annotations

import sys
from pathlib import Path

import pytest


SERVICE_ROOT = (
    Path(__file__)
    .resolve()
    .parents[1]
)

REPOSITORY_ROOT = (
    SERVICE_ROOT.parents[1]
)


if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(
        0,
        str(SERVICE_ROOT),
    )


from app.config import Settings
from app.parsing.language_detector import (
    LanguageDetector,
)
from app.parsing.section_detector import (
    SectionDetector,
)
from app.taxonomy.taxonomy_loader import (
    TaxonomyBundle,
    TaxonomyLoader,
)


@pytest.fixture(scope="session")
def service_root() -> Path:
    return SERVICE_ROOT


@pytest.fixture(scope="session")
def settings(
        service_root: Path,
) -> Settings:
    del service_root

    return Settings(
        _env_file=None,

        CV_PARSER_VERSION=(
            "rule-v1"
        ),

        CV_TAXONOMY_DIRECTORY=str(
            REPOSITORY_ROOT
            / "configs"
            / "taxonomy"
            / "cv-parser"
        ),

        MINIO_ENDPOINT=(
            "127.0.0.1:9000"
        ),

        MINIO_ACCESS_KEY=(
            "test-access-key"
        ),

        MINIO_SECRET_KEY=(
            "test-secret-key"
        ),

        MINIO_BUCKET_CVS=(
            "autojob-cvs"
        ),

        CV_ALLOWED_OBJECT_PREFIXES=(
            "raw/,cvs/"
        ),

        CV_MAX_OBJECT_SIZE_BYTES=(
            10_485_760
        ),

        CV_MAX_EXTRACTED_CHARS=(
            20_000
        ),

        CV_MAX_SECTION_CHARS=(
            5_000
        ),

        CV_MAX_WORK_EXPERIENCES=30,
        CV_MAX_PROJECTS=30,
        CV_MAX_EDUCATIONS=20,
        CV_MAX_CERTIFICATIONS=30,
        CV_MAX_LICENSES=20,
        CV_MAX_TRAINING_COURSES=30,
        CV_MAX_SKILLS=200,
        CV_MAX_LINKS=30,

        CV_MAX_DOCX_ENTRIES=(
            1_000
        ),

        CV_MAX_DOCX_UNCOMPRESSED_BYTES=(
            52_428_800
        ),

        CV_DOC_COMMAND_TIMEOUT_SECONDS=5,

        CV_EXTRACTION_TIMEOUT_SECONDS=5,

        CV_MIN_TEXT_CHARACTERS=40,

        CV_MAX_PDF_PAGES=100,
    )


@pytest.fixture(scope="session")
def taxonomy(
        settings: Settings,
) -> TaxonomyBundle:
    return TaxonomyLoader(
        directory=(
            settings.taxonomy_directory
        ),
        expected_version=(
            settings.parser_version
        ),
    ).load()


@pytest.fixture
def language_detector() -> LanguageDetector:
    return LanguageDetector()


@pytest.fixture
def section_detector(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> SectionDetector:
    return SectionDetector(
        settings=settings,
        taxonomy=taxonomy,
    )


@pytest.fixture
def generated_fixture_dir(
        tmp_path: Path,
) -> Path:
    directory = (
            tmp_path
            / "generated-cvs"
    )

    directory.mkdir(
        parents=True,
        exist_ok=True,
    )

    return directory