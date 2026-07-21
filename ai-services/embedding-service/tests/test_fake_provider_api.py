import hashlib
import math

from fastapi.testclient import TestClient


def test_health_endpoint(
        client: TestClient,
) -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "serviceName": "autojob-embedding-service",
        "serviceVersion": "0.1.0",
    }


def test_ready_endpoint(
        client: TestClient,
) -> None:
    response = client.get("/ready")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ready",
        "provider": "fake",
        "modelName": "autojob/fake-sha256",
        "modelRevision": "deterministic-v1",
        "embeddingVersion": (
            "autojob/fake-sha256"
            "@deterministic-v1"
            "|prep-v1"
            "|l2"
        ),
        "dimension": 384,
        "normalized": True,
    }


def test_valid_embedding_request(
        client: TestClient,
) -> None:
    text = "query: Title: Senior Java Backend Engineer"

    response = client.post(
        "/api/v1/embeddings",
        json={"text": text},
    )

    assert response.status_code == 200

    body = response.json()

    assert body["dimension"] == 384
    assert len(body["vector"]) == 384
    assert body["modelName"] == "autojob/fake-sha256"
    assert body["modelRevision"] == "deterministic-v1"
    assert body["embeddingVersion"] == (
        "autojob/fake-sha256"
        "@deterministic-v1"
        "|prep-v1"
        "|l2"
    )
    assert body["normalized"] is True
    assert body["textHash"] == hashlib.sha256(
        text.encode("utf-8")
    ).hexdigest()


def test_same_input_returns_same_vector(
        client: TestClient,
) -> None:
    request = {
        "text": "query: Kỹ sư Java tại Hồ Chí Minh"
    }

    first_response = client.post(
        "/api/v1/embeddings",
        json=request,
    )

    second_response = client.post(
        "/api/v1/embeddings",
        json=request,
    )

    assert first_response.status_code == 200
    assert second_response.status_code == 200

    assert (
            first_response.json()["vector"]
            == second_response.json()["vector"]
    )


def test_different_input_returns_different_vector(
        client: TestClient,
) -> None:
    first_response = client.post(
        "/api/v1/embeddings",
        json={"text": "query: Java Backend Engineer"},
    )

    second_response = client.post(
        "/api/v1/embeddings",
        json={"text": "query: React Frontend Engineer"},
    )

    assert first_response.status_code == 200
    assert second_response.status_code == 200

    assert (
            first_response.json()["vector"]
            != second_response.json()["vector"]
    )


def test_text_hash_uses_exact_utf8_input(
        client: TestClient,
) -> None:
    text = " query: Kỹ sư phần mềm\nSkills: Java, MongoDB "

    response = client.post(
        "/api/v1/embeddings",
        json={"text": text},
    )

    assert response.status_code == 200
    assert response.json()["textHash"] == hashlib.sha256(
        text.encode("utf-8")
    ).hexdigest()


def test_blank_text_is_rejected(
        client: TestClient,
) -> None:
    response = client.post(
        "/api/v1/embeddings",
        json={"text": "   \n\t"},
    )

    assert response.status_code == 422


def test_vector_contains_only_finite_values(
        client: TestClient,
) -> None:
    response = client.post(
        "/api/v1/embeddings",
        json={"text": "query: Data Engineer"},
    )

    assert response.status_code == 200

    vector = response.json()["vector"]

    assert vector
    assert all(math.isfinite(value) for value in vector)


def test_vector_is_l2_normalized(
        client: TestClient,
) -> None:
    response = client.post(
        "/api/v1/embeddings",
        json={"text": "query: DevOps Engineer"},
    )

    assert response.status_code == 200

    vector = response.json()["vector"]
    norm = math.sqrt(
        sum(value * value for value in vector)
    )

    assert math.isclose(
        norm,
        1.0,
        rel_tol=1e-6,
        abs_tol=1e-6,
    )