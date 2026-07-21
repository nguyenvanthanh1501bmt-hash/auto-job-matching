package com.autojob.modules.jobembedding.vectorstore;

import com.autojob.modules.jobembedding.config.QdrantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QdrantJobVectorStoreTest {

    private MockWebServer server;
    private QdrantProperties properties;
    private ObjectMapper objectMapper;
    private QdrantJobVectorStore vectorStore;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        properties = new QdrantProperties();
        properties.setBaseUrl(
                server.url("/").toString()
        );
        properties.setCollection("job_vectors_v1");
        properties.setDimension(3);
        properties.setDistance("Cosine");
        properties.setConnectTimeout(
                Duration.ofSeconds(1)
        );
        properties.setResponseTimeout(
                Duration.ofSeconds(1)
        );

        objectMapper = new ObjectMapper();

        vectorStore = new QdrantJobVectorStore(
                WebClient.builder(),
                objectMapper,
                properties
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldCreateMissingCollection() throws Exception {
        server.enqueue(
                new MockResponse().setResponseCode(404)
        );

        server.enqueue(jsonResponse(
                "{\"result\":true,\"status\":\"ok\"}"
        ));

        server.enqueue(jsonResponse(
                collectionBody(3, "Cosine")
        ));

        vectorStore.ensureCollection();

        RecordedRequest lookup =
                server.takeRequest();

        RecordedRequest create =
                server.takeRequest();

        RecordedRequest validation =
                server.takeRequest();

        assertThat(lookup.getMethod()).isEqualTo("GET");
        assertThat(create.getMethod()).isEqualTo("PUT");
        assertThat(validation.getMethod())
                .isEqualTo("GET");

        JsonNode createBody = objectMapper.readTree(
                create.getBody().readUtf8()
        );

        assertThat(
                createBody.path("vectors")
                        .path("size")
                        .asInt()
        ).isEqualTo(3);

        assertThat(
                createBody.path("vectors")
                        .path("distance")
                        .asText()
        ).isEqualTo("Cosine");
    }

    @Test
    void shouldAcceptExistingCorrectCollection() {
        server.enqueue(jsonResponse(
                collectionBody(3, "Cosine")
        ));

        vectorStore.ensureCollection();

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void shouldRejectWrongDimensionWithoutRecreate() {
        server.enqueue(jsonResponse(
                collectionBody(384, "Cosine")
        ));

        assertThatThrownBy(
                vectorStore::ensureCollection
        )
                .isInstanceOf(
                        JobVectorStoreException.class
                )
                .hasMessageContaining(
                        "dimension mismatch"
                );

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void shouldRejectWrongDistanceWithoutRecreate() {
        server.enqueue(jsonResponse(
                collectionBody(3, "Dot")
        ));

        assertThatThrownBy(
                vectorStore::ensureCollection
        )
                .isInstanceOf(
                        JobVectorStoreException.class
                )
                .hasMessageContaining(
                        "distance mismatch"
                );

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void shouldUpsertPointWithExpectedPayload()
            throws Exception {
        server.enqueue(jsonResponse(
                "{\"result\":{\"status\":\"completed\"}}"
        ));

        vectorStore.upsert(validPoint());

        RecordedRequest request =
                server.takeRequest();

        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath())
                .isEqualTo(
                        "/collections/job_vectors_v1/points?wait=true"
                );

        JsonNode body = objectMapper.readTree(
                request.getBody().readUtf8()
        );

        JsonNode point = body.path("points").get(0);

        assertThat(point.path("id").asText())
                .isEqualTo(
                        "13f4a274-a0f2-5d77-bf84-4c65bf870dac"
                );

        assertThat(point.path("vector").size())
                .isEqualTo(3);

        JsonNode payload = point.path("payload");

        assertThat(payload.path("jobId").asText())
                .isEqualTo("normalized-001");

        assertThat(
                payload.path("sourceCode").asText()
        ).isEqualTo("MOCK");

        assertThat(
                payload.path("normalizationVersion")
                        .asText()
        ).isEqualTo("rule-v1");

        assertThat(
                payload.path("embeddingVersion")
                        .asText()
        ).isEqualTo(
                "test-model@revision|prep-v1|l2"
        );

        assertThat(
                payload.path("textHash").asText()
        ).isEqualTo("a".repeat(64));

        assertThat(payload.has("embeddingText"))
                .isFalse();
    }

    @Test
    void shouldRejectWrongVectorDimension() {
        JobVectorPoint point = new JobVectorPoint(
                "13f4a274-a0f2-5d77-bf84-4c65bf870dac",
                "normalized-001",
                "MOCK",
                "rule-v1",
                "test-model@revision|prep-v1|l2",
                "a".repeat(64),
                List.of(1.0, 0.0)
        );

        assertThatThrownBy(
                () -> vectorStore.upsert(point)
        )
                .isInstanceOf(
                        JobVectorStoreException.class
                )
                .hasMessageContaining(
                        "dimension mismatch"
                );

        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void shouldWrapQdrant4xx() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(400)
                        .setBody("invalid point")
        );

        assertThatThrownBy(
                () -> vectorStore.upsert(validPoint())
        )
                .isInstanceOf(
                        JobVectorStoreException.class
                )
                .hasMessageContaining("HTTP 400");
    }

    @Test
    void shouldWrapQdrant5xx() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(500)
                        .setBody("server failed")
        );

        assertThatThrownBy(
                () -> vectorStore.upsert(validPoint())
        )
                .isInstanceOf(
                        JobVectorStoreException.class
                )
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void shouldReturnTrueWhenPointExists() {
        server.enqueue(jsonResponse(
                """
                {
                  "result": {
                    "id": "13f4a274-a0f2-5d77-bf84-4c65bf870dac"
                  }
                }
                """
        ));

        assertThat(
                vectorStore.pointExists(
                        "13f4a274-a0f2-5d77-bf84-4c65bf870dac"
                )
        ).isTrue();
    }

    @Test
    void shouldReturnFalseWhenPointDoesNotExist() {
        server.enqueue(
                new MockResponse().setResponseCode(404)
        );

        assertThat(
                vectorStore.pointExists(
                        "13f4a274-a0f2-5d77-bf84-4c65bf870dac"
                )
        ).isFalse();
    }

    private JobVectorPoint validPoint() {
        return new JobVectorPoint(
                "13f4a274-a0f2-5d77-bf84-4c65bf870dac",
                "normalized-001",
                "MOCK",
                "rule-v1",
                "test-model@revision|prep-v1|l2",
                "a".repeat(64),
                List.of(1.0, 0.0, 0.0)
        );
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader(
                        "Content-Type",
                        "application/json"
                )
                .setBody(body);
    }

    private String collectionBody(
            int dimension,
            String distance
    ) {
        return """
                {
                  "result": {
                    "config": {
                      "params": {
                        "vectors": {
                          "size": %d,
                          "distance": "%s"
                        }
                      }
                    }
                  }
                }
                """.formatted(dimension, distance);
    }
}