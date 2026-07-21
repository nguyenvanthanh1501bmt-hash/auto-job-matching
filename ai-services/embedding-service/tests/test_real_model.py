import math
from collections.abc import Iterator
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import EmbeddingSettings
from app.main import create_app
from app.providers.sentence_transformer_provider import (
    SentenceTransformerEmbeddingProvider,
)


pytestmark = pytest.mark.model


@pytest.fixture(scope="session")
def model_settings(
        tmp_path_factory: pytest.TempPathFactory,
) -> EmbeddingSettings:
    cache_directory: Path = tmp_path_factory.mktemp(
        "embedding-model-cache"
    )

    return EmbeddingSettings(
        embedding_provider="sentence-transformer",
        embedding_model_name=(
            "intfloat/multilingual-e5-small"
        ),
        embedding_model_revision=(
            "c007d7ef6fd86656326059b28395a7a03a7c5846"
        ),
        embedding_expected_dimension=384,
        embedding_device="cpu",
        embedding_model_cache_dir=cache_directory,
        embedding_load_on_startup=True,
    )


@pytest.fixture(scope="session")
def model_provider(
        model_settings: EmbeddingSettings,
) -> SentenceTransformerEmbeddingProvider:
    provider = SentenceTransformerEmbeddingProvider(
        model_settings
    )
    provider.load()
    return provider


@pytest.fixture(scope="session")
def model_client(
        model_settings: EmbeddingSettings,
        model_provider: SentenceTransformerEmbeddingProvider,
) -> Iterator[TestClient]:
    application = create_app(
        settings=model_settings,
        provider=model_provider,
    )

    with TestClient(application) as test_client:
        yield test_client


def test_model_loads_successfully(
        model_provider: SentenceTransformerEmbeddingProvider,
) -> None:
    assert model_provider.is_ready is True
    assert model_provider.metadata.dimension == 384


def test_ready_returns_expected_metadata(
        model_client: TestClient,
) -> None:
    response = model_client.get("/ready")

    assert response.status_code == 200

    body = response.json()

    assert body["status"] == "ready"
    assert body["provider"] == "sentence-transformer"
    assert body["modelName"] == (
        "intfloat/multilingual-e5-small"
    )
    assert body["modelRevision"] == (
        "c007d7ef6fd86656326059b28395a7a03a7c5846"
    )
    assert body["dimension"] == 384
    assert body["normalized"] is True


@pytest.mark.parametrize(
    "text",
    [
        (
                "query: Title: Kỹ sư Java Backend\n"
                "Skills: Java, Spring Boot, MongoDB\n"
                "Requirements: Có kinh nghiệm phát triển REST API"
        ),
        (
                "query: Title: Senior Backend Engineer\n"
                "Skills: Java, Spring Boot, PostgreSQL\n"
                "Requirements: Build scalable backend services"
        ),
    ],
)
def test_vietnamese_and_english_texts_embed_successfully(
        model_client: TestClient,
        text: str,
) -> None:
    response = model_client.post(
        "/api/v1/embeddings",
        json={"text": text},
    )

    assert response.status_code == 200

    body = response.json()
    vector = body["vector"]

    assert body["dimension"] == 384
    assert len(vector) == 384
    assert all(math.isfinite(value) for value in vector)


def test_vector_is_l2_normalized(
        model_client: TestClient,
) -> None:
    response = model_client.post(
        "/api/v1/embeddings",
        json={
            "text": (
                "query: Senior Java Backend Engineer "
                "with Spring Boot experience"
            )
        },
    )

    assert response.status_code == 200

    vector = response.json()["vector"]

    norm = math.sqrt(
        sum(value * value for value in vector)
    )

    assert math.isclose(
        norm,
        1.0,
        rel_tol=1e-5,
        abs_tol=1e-5,
    )


def test_same_input_is_stable_within_tolerance(
        model_provider: SentenceTransformerEmbeddingProvider,
) -> None:
    text = (
        "query: Kỹ sư phần mềm Java "
        "có kinh nghiệm Spring Boot"
    )

    first_vector = model_provider.embed(text)
    second_vector = model_provider.embed(text)

    assert len(first_vector) == 384
    assert len(second_vector) == 384

    for first_value, second_value in zip(
            first_vector,
            second_vector,
            strict=True,
    ):
        assert math.isclose(
            first_value,
            second_value,
            rel_tol=1e-6,
            abs_tol=1e-6,
        )


def test_semantically_similar_jobs_are_closer(
        model_provider: SentenceTransformerEmbeddingProvider,
) -> None:
    java_english = model_provider.embed(
        "query: Senior Java backend engineer "
        "with Spring Boot and REST API experience"
    )

    java_vietnamese = model_provider.embed(
        "query: Kỹ sư backend Java cấp cao, "
        "thành thạo Spring Boot và REST API"
    )

    unrelated = model_provider.embed(
        "query: Chuyên viên thiết kế đồ họa, "
        "thành thạo Photoshop và Illustrator"
    )

    similar_score = cosine_similarity(
        java_english,
        java_vietnamese,
    )

    unrelated_score = cosine_similarity(
        java_english,
        unrelated,
    )

    assert similar_score > unrelated_score + 0.02


def cosine_similarity(
        first: list[float],
        second: list[float],
) -> float:
    return sum(
        first_value * second_value
        for first_value, second_value in zip(
            first,
            second,
            strict=True,
        )
    )