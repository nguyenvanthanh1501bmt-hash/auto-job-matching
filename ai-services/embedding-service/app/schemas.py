import math
import re
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


SHA256_PATTERN = re.compile(r"^[a-f0-9]{64}$")


def to_camel_case(value: str) -> str:
    first, *remaining = value.split("_")
    return first + "".join(
        item[:1].upper() + item[1:]
        for item in remaining
    )


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel_case,
        populate_by_name=True,
        extra="forbid",
        allow_inf_nan=False,
    )


class EmbeddingRequest(ApiModel):
    text: str

    @field_validator("text")
    @classmethod
    def validate_text(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("text must not be blank")

        return value


class EmbeddingResponse(ApiModel):
    vector: list[float]
    dimension: int = Field(gt=0)
    model_name: str
    model_revision: str
    embedding_version: str
    text_hash: str
    normalized: Literal[True]

    @field_validator(
        "model_name",
        "model_revision",
        "embedding_version",
    )
    @classmethod
    def validate_non_blank_metadata(
            cls,
            value: str,
    ) -> str:
        normalized_value = value.strip()

        if not normalized_value:
            raise ValueError("metadata must not be blank")

        return normalized_value

    @field_validator("text_hash")
    @classmethod
    def validate_text_hash(cls, value: str) -> str:
        normalized_value = value.strip().lower()

        if not SHA256_PATTERN.fullmatch(normalized_value):
            raise ValueError(
                "textHash must be a lowercase SHA-256 hex value"
            )

        return normalized_value

    @field_validator("vector")
    @classmethod
    def validate_vector(
            cls,
            value: list[float],
    ) -> list[float]:
        if not value:
            raise ValueError("vector must not be empty")

        if not all(math.isfinite(item) for item in value):
            raise ValueError(
                "vector must contain only finite values"
            )

        return value


class HealthResponse(ApiModel):
    status: Literal["ok"]
    service_name: str
    service_version: str


class ReadyResponse(ApiModel):
    status: Literal["ready"]
    provider: str
    model_name: str
    model_revision: str
    embedding_version: str
    dimension: int = Field(gt=0)
    normalized: Literal[True]