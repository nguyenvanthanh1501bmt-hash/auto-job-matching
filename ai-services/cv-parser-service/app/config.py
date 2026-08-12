from __future__ import annotations

from functools import lru_cache

from pydantic import Field, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore",
    )

    app_name: str = Field(
        default="autojob-cv-parser-service",
        validation_alias="CV_PARSER_APP_NAME",
    )
    parser_version: str = Field(
        default="rule-v1",
        min_length=1,
        max_length=100,
        validation_alias="CV_PARSER_VERSION",
    )
    log_level: str = Field(
        default="INFO",
        validation_alias="LOG_LEVEL",
    )

    minio_endpoint: str = Field(
        default="minio:9000",
        min_length=3,
        max_length=500,
        validation_alias="MINIO_ENDPOINT",
    )
    minio_access_key: str = Field(
        default="minioadmin",
        min_length=1,
        validation_alias="MINIO_ACCESS_KEY",
    )
    minio_secret_key: str = Field(
        default="minioadmin",
        min_length=1,
        validation_alias="MINIO_SECRET_KEY",
    )
    minio_secure: bool = Field(
        default=False,
        validation_alias="MINIO_SECURE",
    )
    minio_bucket: str = Field(
        default="autojob-cvs",
        min_length=3,
        max_length=63,
        validation_alias="MINIO_BUCKET_CVS",
    )

    max_object_size_bytes: int = Field(
        default=10_485_760,
        ge=1,
        le=104_857_600,
        validation_alias="CV_MAX_OBJECT_SIZE_BYTES",
    )
    max_extracted_chars: int = Field(
        default=200_000,
        ge=1_000,
        le=2_000_000,
        validation_alias="CV_MAX_EXTRACTED_CHARS",
    )
    max_section_chars: int = Field(
        default=50_000,
        ge=500,
        le=500_000,
        validation_alias="CV_MAX_SECTION_CHARS",
    )

    max_work_experiences: int = Field(
        default=30,
        ge=1,
        le=200,
        validation_alias="CV_MAX_WORK_EXPERIENCES",
    )
    max_projects: int = Field(
        default=30,
        ge=1,
        le=200,
        validation_alias="CV_MAX_PROJECTS",
    )
    max_educations: int = Field(
        default=20,
        ge=1,
        le=100,
        validation_alias="CV_MAX_EDUCATIONS",
    )
    max_certifications: int = Field(
        default=30,
        ge=1,
        le=200,
        validation_alias="CV_MAX_CERTIFICATIONS",
    )
    max_licenses: int = Field(
        default=20,
        ge=1,
        le=100,
        validation_alias="CV_MAX_LICENSES",
    )
    max_training_courses: int = Field(
        default=30,
        ge=1,
        le=200,
        validation_alias="CV_MAX_TRAINING_COURSES",
    )
    max_skills: int = Field(
        default=200,
        ge=1,
        le=1_000,
        validation_alias="CV_MAX_SKILLS",
    )
    max_links: int = Field(
        default=30,
        ge=1,
        le=200,
        validation_alias="CV_MAX_LINKS",
    )

    max_docx_entries: int = Field(
        default=1_000,
        ge=10,
        le=100_000,
        validation_alias="CV_MAX_DOCX_ENTRIES",
    )
    max_docx_uncompressed_bytes: int = Field(
        default=52_428_800,
        ge=1_048_576,
        le=1_073_741_824,
        validation_alias="CV_MAX_DOCX_UNCOMPRESSED_BYTES",
    )

    doc_command_timeout_seconds: int = Field(
        default=20,
        ge=1,
        le=300,
        validation_alias="CV_DOC_COMMAND_TIMEOUT_SECONDS",
    )
    extraction_timeout_seconds: int = Field(
        default=45,
        ge=1,
        le=600,
        validation_alias="CV_EXTRACTION_TIMEOUT_SECONDS",
    )

    min_text_characters: int = Field(
        default=40,
        ge=1,
        le=10_000,
        validation_alias="CV_MIN_TEXT_CHARACTERS",
    )
    max_pdf_pages: int = Field(
        default=100,
        ge=1,
        le=2_000,
        validation_alias="CV_MAX_PDF_PAGES",
    )

    taxonomy_directory: str = Field(
        default="../../configs/taxonomy/cv-parser",
        min_length=1,
        validation_alias="CV_TAXONOMY_DIRECTORY",
    )

    allowed_object_prefixes: tuple[str, ...] = Field(
        default=("raw/", "cvs/"),
        validation_alias="CV_ALLOWED_OBJECT_PREFIXES",
    )

    @field_validator("log_level")
    @classmethod
    def validate_log_level(cls, value: str) -> str:
        normalized = value.strip().upper()
        allowed = {"CRITICAL", "ERROR", "WARNING", "INFO", "DEBUG"}
        if normalized not in allowed:
            raise ValueError(
                f"LOG_LEVEL must be one of {sorted(allowed)}"
            )
        return normalized

    @field_validator("minio_endpoint")
    @classmethod
    def validate_minio_endpoint(cls, value: str) -> str:
        normalized = value.strip()

        if normalized.startswith(("http://", "https://")):
            raise ValueError(
                "MINIO_ENDPOINT must not include an URL scheme; "
                "use host:port and configure MINIO_SECURE separately"
            )

        if "/" in normalized:
            raise ValueError(
                "MINIO_ENDPOINT must contain only host and optional port"
            )

        return normalized

    @field_validator("minio_bucket")
    @classmethod
    def validate_bucket_name(cls, value: str) -> str:
        normalized = value.strip()

        if normalized != normalized.lower():
            raise ValueError("MINIO_BUCKET_CVS must be lowercase")

        if not all(
                character.islower()
                or character.isdigit()
                or character in {".", "-"}
                for character in normalized
        ):
            raise ValueError(
                "MINIO_BUCKET_CVS contains unsupported characters"
            )

        if normalized.startswith((".", "-")) or normalized.endswith((".", "-")):
            raise ValueError(
                "MINIO_BUCKET_CVS must not start or end with '.' or '-'"
            )

        return normalized

    @field_validator("allowed_object_prefixes", mode="before")
    @classmethod
    def parse_allowed_object_prefixes(
            cls,
            value: object,
    ) -> tuple[str, ...]:
        if isinstance(value, str):
            prefixes = tuple(
                prefix.strip()
                for prefix in value.split(",")
                if prefix.strip()
            )
        elif isinstance(value, (tuple, list)):
            prefixes = tuple(str(prefix).strip() for prefix in value)
        else:
            raise ValueError(
                "CV_ALLOWED_OBJECT_PREFIXES must be a comma-separated string"
            )

        if not prefixes:
            raise ValueError(
                "CV_ALLOWED_OBJECT_PREFIXES must contain at least one prefix"
            )

        normalized: list[str] = []
        for prefix in prefixes:
            if prefix.startswith("/") or ".." in prefix or "\\" in prefix:
                raise ValueError(
                    "CV_ALLOWED_OBJECT_PREFIXES contains an unsafe prefix"
                )
            normalized.append(prefix)

        return tuple(normalized)

    @model_validator(mode="after")
    def validate_limits(self) -> "Settings":
        if self.max_section_chars > self.max_extracted_chars:
            raise ValueError(
                "CV_MAX_SECTION_CHARS must not exceed "
                "CV_MAX_EXTRACTED_CHARS"
            )

        return self


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()