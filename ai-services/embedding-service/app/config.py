from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


EmbeddingProviderName = Literal[
    "sentence-transformer",
    "fake",
]

EmbeddingDevice = Literal[
    "cpu",
    "cuda",
    "mps",
]


class EmbeddingSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    service_name: str = "autojob-embedding-service"
    service_version: str = "0.1.0"

    host: str = "0.0.0.0"
    port: int = Field(default=8002, ge=1, le=65_535)
    log_level: str = "INFO"

    embedding_provider: EmbeddingProviderName = (
        "sentence-transformer"
    )

    embedding_model_name: str = (
        "intfloat/multilingual-e5-small"
    )

    embedding_model_revision: str = (
        "c007d7ef6fd86656326059b28395a7a03a7c5846"
    )

    embedding_expected_dimension: int = Field(
        default=384,
        ge=1,
    )

    embedding_preprocessing_version: str = "prep-v1"

    embedding_normalization_strategy: Literal["l2"] = "l2"

    embedding_device: EmbeddingDevice = "cpu"

    embedding_model_cache_dir: Path = Path(
        "/models/huggingface"
    )

    embedding_max_text_chars: int = Field(
        default=20_000,
        ge=1,
    )

    embedding_load_on_startup: bool = True

    embedding_fake_model_name: str = (
        "autojob/fake-sha256"
    )

    embedding_fake_model_revision: str = (
        "deterministic-v1"
    )

    embedding_fake_seed: str = (
        "autojob-fake-embedding-provider-v1"
    )

    @field_validator(
        "service_name",
        "service_version",
        "host",
        "log_level",
        "embedding_model_name",
        "embedding_model_revision",
        "embedding_preprocessing_version",
        "embedding_fake_model_name",
        "embedding_fake_model_revision",
        "embedding_fake_seed",
        mode="before",
    )
    @classmethod
    def validate_non_blank_string(
            cls,
            value: object,
    ) -> str:
        if not isinstance(value, str):
            raise ValueError("value must be a string")

        normalized_value = value.strip()

        if not normalized_value:
            raise ValueError("value must not be blank")

        return normalized_value

    @property
    def active_model_name(self) -> str:
        if self.embedding_provider == "fake":
            return self.embedding_fake_model_name

        return self.embedding_model_name

    @property
    def active_model_revision(self) -> str:
        if self.embedding_provider == "fake":
            return self.embedding_fake_model_revision

        return self.embedding_model_revision

    @property
    def embedding_version(self) -> str:
        return (
            f"{self.active_model_name}"
            f"@{self.active_model_revision}"
            f"|{self.embedding_preprocessing_version}"
            f"|{self.embedding_normalization_strategy}"
        )


@lru_cache(maxsize=1)
def get_settings() -> EmbeddingSettings:
    return EmbeddingSettings()