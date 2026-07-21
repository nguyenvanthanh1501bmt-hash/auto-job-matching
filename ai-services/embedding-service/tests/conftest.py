from collections.abc import Iterator
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import EmbeddingSettings
from app.main import create_app
from app.providers.fake_provider import FakeEmbeddingProvider


@pytest.fixture
def fake_settings(
        tmp_path: Path,
) -> EmbeddingSettings:
    return EmbeddingSettings(
        embedding_provider="fake",
        embedding_expected_dimension=384,
        embedding_model_cache_dir=tmp_path / "model-cache",
        embedding_load_on_startup=True,
    )


@pytest.fixture
def fake_provider(
        fake_settings: EmbeddingSettings,
) -> FakeEmbeddingProvider:
    return FakeEmbeddingProvider(fake_settings)


@pytest.fixture
def client(
        fake_settings: EmbeddingSettings,
        fake_provider: FakeEmbeddingProvider,
) -> Iterator[TestClient]:
    application = create_app(
        settings=fake_settings,
        provider=fake_provider,
    )

    with TestClient(application) as test_client:
        yield test_client