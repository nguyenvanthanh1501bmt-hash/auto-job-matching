package com.autojob.modules.jobembedding.vectorstore;

import com.autojob.modules.jobembedding.config.QdrantProperties;
import com.autojob.modules.jobembedding.search.JobVectorHit;
import com.autojob.modules.jobembedding.search.JobVectorSearchCriteria;
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

        properties.setCollection(
                "job_vectors_v1"
        );

        properties.setDimension(3);

        properties.setDistance(
                "Cosine"
        );

        properties.setConnectTimeout(
                Duration.ofSeconds(1)
        );

        properties.setResponseTimeout(
                Duration.ofSeconds(1)
        );

        objectMapper =
                new ObjectMapper();

        vectorStore =
                new QdrantJobVectorStore(
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
    void shouldCreateMissingCollection()
            throws Exception {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(404)
        );

        server.enqueue(
                jsonResponse(
                        """
                        {
                          "result": true,
                          "status": "ok"
                        }
                        """
                )
        );

        server.enqueue(
                jsonResponse(
                        collectionBody(
                                3,
                                "Cosine"
                        )
                )
        );

        vectorStore.ensureCollection();

        RecordedRequest lookup =
                server.takeRequest();

        RecordedRequest create =
                server.takeRequest();

        RecordedRequest validation =
                server.takeRequest();

        assertThat(
                lookup.getMethod()
        ).isEqualTo(
                "GET"
        );

        assertThat(
                create.getMethod()
        ).isEqualTo(
                "PUT"
        );

        assertThat(
                validation.getMethod()
        ).isEqualTo(
                "GET"
        );

        JsonNode createBody =
                objectMapper.readTree(
                        create
                                .getBody()
                                .readUtf8()
                );

        assertThat(
                createBody
                        .path("vectors")
                        .path("size")
                        .asInt()
        ).isEqualTo(
                3
        );

        assertThat(
                createBody
                        .path("vectors")
                        .path("distance")
                        .asText()
        ).isEqualTo(
                "Cosine"
        );
    }

    @Test
    void shouldAcceptExistingCorrectCollection() {
        server.enqueue(
                jsonResponse(
                        collectionBody(
                                3,
                                "Cosine"
                        )
                )
        );

        vectorStore.ensureCollection();

        assertThat(
                server.getRequestCount()
        ).isEqualTo(
                1
        );
    }

    @Test
    void shouldRejectWrongDimensionWithoutRecreate() {
        server.enqueue(
                jsonResponse(
                        collectionBody(
                                384,
                                "Cosine"
                        )
                )
        );

        assertThatThrownBy(
                vectorStore::ensureCollection
        )
                .isInstanceOf(
                        JobVectorStoreException.class
                )
                .hasMessageContaining(
                        "dimension mismatch"
                );

        assertThat(
                server.getRequestCount()
        ).isEqualTo(
                1
        );
    }

    @Test
    void shouldRejectWrongDistanceWithoutRecreate() {
        server.enqueue(
                jsonResponse(
                        collectionBody(
                                3,
                                "Dot"
                        )
                )
        );

        assertThatThrownBy(
                vectorStore::ensureCollection
        )
                .isInstanceOf(
                        JobVectorStoreException.class
                )
                .hasMessageContaining(
                        "distance mismatch"
                );

        assertThat(
                server.getRequestCount()
        ).isEqualTo(
                1
        );
    }

    @Test
    void shouldUpsertPointWithExpectedPayload()
            throws Exception {
        server.enqueue(
                jsonResponse(
                        """
                        {
                          "result": {
                            "status": "completed"
                          }
                        }
                        """
                )
        );

        vectorStore.upsert(
                validPoint()
        );

        RecordedRequest request =
                server.takeRequest();

        assertThat(
                request.getMethod()
        ).isEqualTo(
                "PUT"
        );

        assertThat(
                request.getPath()
        ).isEqualTo(
                "/collections/job_vectors_v1/points?wait=true"
        );

        JsonNode body =
                objectMapper.readTree(
                        request
                                .getBody()
                                .readUtf8()
                );

        JsonNode point =
                body.path("points")
                        .get(0);

        assertThat(
                point.path("id").asText()
        ).isEqualTo(
                "13f4a274-a0f2-5d77-bf84-4c65bf870dac"
        );

        assertThat(
                point.path("vector").size()
        ).isEqualTo(
                3
        );

        JsonNode payload =
                point.path(
                        "payload"
                );

        assertThat(
                payload.path(
                                "normalizedJobId"
                        )
                        .asText()
        ).isEqualTo(
                "normalized-001"
        );

        assertThat(
                payload.path(
                                "jobId"
                        )
                        .asText()
        ).isEqualTo(
                "normalized-001"
        );

        assertThat(
                payload.path(
                                "sourceCode"
                        )
                        .asText()
        ).isEqualTo(
                "MOCK"
        );

        assertThat(
                payload.path(
                                "normalizationVersion"
                        )
                        .asText()
        ).isEqualTo(
                "rule-v2"
        );

        assertThat(
                payload.path(
                                "embeddingVersion"
                        )
                        .asText()
        ).isEqualTo(
                "test-model@revision|prep-v1|l2"
        );

        assertThat(
                payload.path(
                                "textVersion"
                        )
                        .asText()
        ).isEqualTo(
                "job-text-v2"
        );

        assertThat(
                payload.path(
                                "textHash"
                        )
                        .asText()
        ).isEqualTo(
                "a".repeat(64)
        );

        assertThat(
                payload.has(
                        "embeddingText"
                )
        ).isFalse();
    }

    @Test
    void shouldRejectWrongVectorDimension() {
        JobVectorPoint point =
                new JobVectorPoint(
                        "13f4a274-a0f2-5d77-bf84-4c65bf870dac",
                        "normalized-001",
                        "MOCK",
                        "rule-v2",
                        "test-model@revision|prep-v1|l2",
                        "job-text-v2",
                        "a".repeat(64),
                        List.of(
                                1.0,
                                0.0
                        )
                );

        assertThatThrownBy(
                () ->
                        vectorStore.upsert(
                                point
                        )
        )
                .isInstanceOf(
                        JobVectorStoreException.class
                )
                .hasMessageContaining(
                        "dimension mismatch"
                );

        assertThat(
                server.getRequestCount()
        ).isZero();
    }

    @Test
    void shouldWrapQdrant4xx() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(400)
                        .setBody(
                                "invalid point"
                        )
        );

        assertThatThrownBy(
                () ->
                        vectorStore.upsert(
                                validPoint()
                        )
        )
                .isInstanceOf(
                        JobVectorStoreException.class
                )
                .hasMessageContaining(
                        "HTTP 400"
                );
    }

    @Test
    void shouldWrapQdrant5xx() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(500)
                        .setBody(
                                "server failed"
                        )
        );

        assertThatThrownBy(
                () ->
                        vectorStore.upsert(
                                validPoint()
                        )
        )
                .isInstanceOf(
                        JobVectorStoreException.class
                )
                .hasMessageContaining(
                        "HTTP 500"
                );
    }

    @Test
    void shouldReturnTrueWhenPointExists() {
        server.enqueue(
                jsonResponse(
                        """
                        {
                          "result": {
                            "id": "13f4a274-a0f2-5d77-bf84-4c65bf870dac"
                          }
                        }
                        """
                )
        );

        assertThat(
                vectorStore.pointExists(
                        "13f4a274-a0f2-5d77-bf84-4c65bf870dac"
                )
        ).isTrue();
    }

    @Test
    void shouldReturnFalseWhenPointDoesNotExist() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(404)
        );

        assertThat(
                vectorStore.pointExists(
                        "13f4a274-a0f2-5d77-bf84-4c65bf870dac"
                )
        ).isFalse();
    }

    @Test
    void shouldSearchWithCompatibilityFilters()
            throws Exception {
        /*
         * 1. ensureCollection()
         */
        server.enqueue(
                jsonResponse(
                        collectionBody(
                                3,
                                "Cosine"
                        )
                )
        );

        /*
         * 2-5. Four payload indexes.
         */
        for (int i = 0; i < 4; i++) {
            server.enqueue(
                    jsonResponse(
                            """
                            {
                              "result": {
                                "status": "completed"
                              },
                              "status": "ok"
                            }
                            """
                    )
            );
        }

        /*
         * 6. Vector search.
         */
        server.enqueue(
                jsonResponse(
                        """
                        {
                          "result": [
                            {
                              "id": "11111111-1111-1111-1111-111111111111",
                              "score": 0.91,
                              "payload": {
                                "normalizedJobId": "normalized-001",
                                "sourceCode": "MOCK",
                                "normalizationVersion": "rule-v2",
                                "embeddingVersion": "test-model@revision|prep-v1|l2",
                                "textVersion": "job-text-v2"
                              }
                            },
                            {
                              "id": "22222222-2222-2222-2222-222222222222",
                              "score": 0.82,
                              "payload": {
                                "normalizedJobId": "normalized-002",
                                "sourceCode": "MOCK",
                                "normalizationVersion": "rule-v2",
                                "embeddingVersion": "test-model@revision|prep-v1|l2",
                                "textVersion": "job-text-v2"
                              }
                            }
                          ]
                        }
                        """
                )
        );

        JobVectorSearchCriteria criteria =
                new JobVectorSearchCriteria(
                        100,
                        "rule-v2",
                        "test-model@revision|prep-v1|l2",
                        "job-text-v2"
                );

        List<JobVectorHit> hits =
                vectorStore.search(
                        List.of(
                                1.0,
                                0.0,
                                0.0
                        ),
                        criteria
                );

        assertThat(hits)
                .hasSize(2);

        assertThat(
                hits.get(0)
                        .normalizedJobId()
        ).isEqualTo(
                "normalized-001"
        );

        assertThat(
                hits.get(0)
                        .score()
        ).isEqualTo(
                0.91d
        );

        assertThat(
                hits.get(1)
                        .normalizedJobId()
        ).isEqualTo(
                "normalized-002"
        );

        /*
         * Skip collection request.
         */
        RecordedRequest collectionRequest =
                server.takeRequest();

        assertThat(
                collectionRequest.getMethod()
        ).isEqualTo(
                "GET"
        );

        /*
         * Check four index requests.
         */
        for (int i = 0; i < 4; i++) {
            RecordedRequest indexRequest =
                    server.takeRequest();

            assertThat(
                    indexRequest.getMethod()
            ).isEqualTo(
                    "PUT"
            );

            assertThat(
                    indexRequest.getPath()
            ).isEqualTo(
                    "/collections/job_vectors_v1/index?wait=true"
            );

            JsonNode indexBody =
                    objectMapper.readTree(
                            indexRequest
                                    .getBody()
                                    .readUtf8()
                    );

            assertThat(
                    indexBody.path(
                                    "field_schema"
                            )
                            .asText()
            ).isEqualTo(
                    "keyword"
            );
        }

        RecordedRequest searchRequest =
                server.takeRequest();

        assertThat(
                searchRequest.getMethod()
        ).isEqualTo(
                "POST"
        );

        assertThat(
                searchRequest.getPath()
        ).isEqualTo(
                "/collections/job_vectors_v1/points/search"
        );

        JsonNode searchBody =
                objectMapper.readTree(
                        searchRequest
                                .getBody()
                                .readUtf8()
                );

        assertThat(
                searchBody
                        .path("vector")
                        .size()
        ).isEqualTo(
                3
        );

        assertThat(
                searchBody
                        .path("limit")
                        .asInt()
        ).isEqualTo(
                100
        );

        assertThat(
                searchBody
                        .path("with_payload")
                        .asBoolean()
        ).isTrue();

        assertThat(
                searchBody
                        .path("with_vector")
                        .asBoolean()
        ).isFalse();

        JsonNode must =
                searchBody
                        .path("filter")
                        .path("must");

        assertThat(
                must.size()
        ).isEqualTo(
                3
        );

        assertMatchCondition(
                must,
                "normalizationVersion",
                "rule-v2"
        );

        assertMatchCondition(
                must,
                "embeddingVersion",
                "test-model@revision|prep-v1|l2"
        );

        assertMatchCondition(
                must,
                "textVersion",
                "job-text-v2"
        );
    }

    @Test
    void shouldCreateSearchIndexesOnlyOncePerStoreInstance() {
        /*
         * Search lần 1:
         * GET collection
         * + 4 index requests
         * + search request.
         */
        server.enqueue(
                jsonResponse(
                        collectionBody(
                                3,
                                "Cosine"
                        )
                )
        );

        for (int i = 0; i < 4; i++) {
            server.enqueue(
                    jsonResponse(
                            """
                            {
                              "result": {
                                "status": "completed"
                              }
                            }
                            """
                    )
            );
        }

        server.enqueue(
                emptySearchResponse()
        );

        /*
         * Search lần 2:
         * chỉ GET collection + search.
         * Không recreate indexes.
         */
        server.enqueue(
                jsonResponse(
                        collectionBody(
                                3,
                                "Cosine"
                        )
                )
        );

        server.enqueue(
                emptySearchResponse()
        );

        JobVectorSearchCriteria criteria =
                new JobVectorSearchCriteria(
                        10,
                        "rule-v2",
                        "test-model@revision|prep-v1|l2",
                        "job-text-v2"
                );

        vectorStore.search(
                List.of(
                        1.0,
                        0.0,
                        0.0
                ),
                criteria
        );

        vectorStore.search(
                List.of(
                        1.0,
                        0.0,
                        0.0
                ),
                criteria
        );

        assertThat(
                server.getRequestCount()
        ).isEqualTo(
                8
        );
    }

    @Test
    void shouldRejectSearchWithWrongQueryVectorDimension() {
        JobVectorSearchCriteria criteria =
                new JobVectorSearchCriteria(
                        10,
                        "rule-v2",
                        "test-model@revision|prep-v1|l2",
                        "job-text-v2"
                );

        assertThatThrownBy(
                () ->
                        vectorStore.search(
                                List.of(
                                        1.0,
                                        0.0
                                ),
                                criteria
                        )
        )
                .isInstanceOf(
                        JobVectorStoreException.class
                )
                .hasMessageContaining(
                        "dimension mismatch"
                );

        assertThat(
                server.getRequestCount()
        ).isZero();
    }

    @Test
    void shouldSupportLegacyJobIdWhenParsingSearchResult() {
        server.enqueue(
                jsonResponse(
                        collectionBody(
                                3,
                                "Cosine"
                        )
                )
        );

        for (int i = 0; i < 4; i++) {
            server.enqueue(
                    jsonResponse(
                            """
                            {
                              "result": {
                                "status": "completed"
                              }
                            }
                            """
                    )
            );
        }

        server.enqueue(
                jsonResponse(
                        """
                        {
                          "result": [
                            {
                              "id": "11111111-1111-1111-1111-111111111111",
                              "score": 0.75,
                              "payload": {
                                "jobId": "legacy-normalized-job"
                              }
                            }
                          ]
                        }
                        """
                )
        );

        List<JobVectorHit> hits =
                vectorStore.search(
                        List.of(
                                1.0,
                                0.0,
                                0.0
                        ),
                        new JobVectorSearchCriteria(
                                10,
                                "rule-v2",
                                "test-model@revision|prep-v1|l2",
                                "job-text-v2"
                        )
                );

        assertThat(hits)
                .hasSize(1);

        assertThat(
                hits.get(0)
                        .normalizedJobId()
        ).isEqualTo(
                "legacy-normalized-job"
        );
    }

    private void assertMatchCondition(
            JsonNode must,
            String key,
            String expectedValue
    ) {
        boolean found =
                false;

        for (JsonNode condition : must) {
            if (key.equals(
                    condition
                            .path("key")
                            .asText()
            )) {
                assertThat(
                        condition
                                .path("match")
                                .path("value")
                                .asText()
                ).isEqualTo(
                        expectedValue
                );

                found = true;
                break;
            }
        }

        assertThat(found)
                .as(
                        "Expected filter condition for key %s",
                        key
                )
                .isTrue();
    }

    private JobVectorPoint validPoint() {
        return new JobVectorPoint(
                "13f4a274-a0f2-5d77-bf84-4c65bf870dac",
                "normalized-001",
                "MOCK",
                "rule-v2",
                "test-model@revision|prep-v1|l2",
                "job-text-v2",
                "a".repeat(64),
                List.of(
                        1.0,
                        0.0,
                        0.0
                )
        );
    }

    private MockResponse emptySearchResponse() {
        return jsonResponse(
                """
                {
                  "result": []
                }
                """
        );
    }

    private MockResponse jsonResponse(
            String body
    ) {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader(
                        "Content-Type",
                        "application/json"
                )
                .setBody(
                        body
                );
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
                """.formatted(
                dimension,
                distance
        );
    }
}