package com.autojob.modules.jobembedding.client;

import com.autojob.modules.jobembedding.client.dto.EmbeddingResponse;
import com.autojob.modules.jobembedding.config.EmbeddingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpEmbeddingClientTest {

    private static final String TEXT =
            "query: Title: Senior Java Backend Engineer";

    private static final String VERSION =
            "test-model@revision-1|prep-v1|l2";

    private MockWebServer server;
    private EmbeddingProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        objectMapper = new ObjectMapper();

        properties = new EmbeddingProperties();
        properties.setBaseUrl(
                server.url("/").toString()
        );
        properties.setExpectedDimension(3);
        properties.setExpectedVersion(VERSION);
        properties.setConnectTimeout(
                Duration.ofSeconds(1)
        );
        properties.setResponseTimeout(
                Duration.ofSeconds(1)
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldReturnValidResponse() throws Exception {
        server.enqueue(jsonResponse(validBody()));

        EmbeddingResponse response =
                createClient().embed(TEXT);

        assertThat(response.vector())
                .containsExactly(1.0, 0.0, 0.0);

        assertThat(response.dimension()).isEqualTo(3);
        assertThat(response.embeddingVersion())
                .isEqualTo(VERSION);

        RecordedRequest request = server.takeRequest();

        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath())
                .isEqualTo("/api/v1/embeddings");

        assertThat(request.getBody().readUtf8())
                .contains("\"text\":\"" + TEXT + "\"");
    }

    @Test
    void shouldRejectHttp400() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(400)
                        .setBody("{\"detail\":\"invalid\"}")
        );

        assertClientFailure("HTTP 400");
    }

    @Test
    void shouldRejectHttp500() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(500)
                        .setBody("{\"detail\":\"failed\"}")
        );

        assertClientFailure("HTTP 500");
    }

    @Test
    void shouldRejectEmptyBody() {
        server.enqueue(
                new MockResponse().setResponseCode(200)
        );

        assertClientFailure("empty body");
    }

    @Test
    void shouldRejectMissingVector() {
        ObjectNode body = validBodyNode();
        body.remove("vector");

        server.enqueue(jsonResponse(body));

        assertClientFailure("vector is missing");
    }

    @Test
    void shouldRejectEmptyVector() {
        ObjectNode body = validBodyNode();
        body.set(
                "vector",
                objectMapper.createArrayNode()
        );

        server.enqueue(jsonResponse(body));

        assertClientFailure("vector is empty");
    }

    @Test
    void shouldRejectDimensionMismatch() {
        ObjectNode body = validBodyNode();
        body.put("dimension", 4);

        server.enqueue(jsonResponse(body));

        assertClientFailure("dimension mismatch");
    }

    @Test
    void shouldRejectVectorLengthMismatch() {
        ObjectNode body = validBodyNode();

        ArrayNode vector = objectMapper.createArrayNode();
        vector.add(1.0);
        vector.add(0.0);

        body.set("vector", vector);

        server.enqueue(jsonResponse(body));

        assertClientFailure("vector length mismatch");
    }

    @Test
    void shouldRejectMissingModelName() {
        ObjectNode body = validBodyNode();
        body.remove("modelName");

        server.enqueue(jsonResponse(body));

        assertClientFailure("modelName is missing");
    }

    @Test
    void shouldRejectMissingModelRevision() {
        ObjectNode body = validBodyNode();
        body.remove("modelRevision");

        server.enqueue(jsonResponse(body));

        assertClientFailure("modelRevision is missing");
    }

    @Test
    void shouldRejectMissingEmbeddingVersion() {
        ObjectNode body = validBodyNode();
        body.remove("embeddingVersion");

        server.enqueue(jsonResponse(body));

        assertClientFailure(
                "embeddingVersion is missing"
        );
    }

    @Test
    void shouldRejectUnexpectedEmbeddingVersion() {
        ObjectNode body = validBodyNode();
        body.put(
                "embeddingVersion",
                "other-model@revision|prep-v1|l2"
        );

        server.enqueue(jsonResponse(body));

        assertClientFailure(
                "embeddingVersion mismatch"
        );
    }

    @Test
    void shouldRejectMissingTextHash() {
        ObjectNode body = validBodyNode();
        body.remove("textHash");

        server.enqueue(jsonResponse(body));

        assertClientFailure("textHash is missing");
    }

    @Test
    void shouldRejectInvalidTextHashFormat() {
        ObjectNode body = validBodyNode();
        body.put("textHash", "invalid-hash");

        server.enqueue(jsonResponse(body));

        assertClientFailure(
                "textHash is not a valid SHA-256"
        );
    }

    @Test
    void shouldRejectTextHashMismatch() {
        ObjectNode body = validBodyNode();
        body.put("textHash", "a".repeat(64));

        server.enqueue(jsonResponse(body));

        assertClientFailure(
                "textHash does not match"
        );
    }

    @Test
    void shouldRejectNormalizedFalse() {
        ObjectNode body = validBodyNode();
        body.put("normalized", false);

        server.enqueue(jsonResponse(body));

        assertClientFailure(
                "normalized must be true"
        );
    }

    @Test
    void shouldRejectNaNVector() {
        server.enqueue(jsonResponse(
                """
                {
                  "vector": ["NaN", 0.0, 0.0],
                  "dimension": 3,
                  "modelName": "test-model",
                  "modelRevision": "revision-1",
                  "embeddingVersion": "%s",
                  "textHash": "%s",
                  "normalized": true
                }
                """.formatted(
                        VERSION,
                        sha256(TEXT)
                )
        ));

        assertClientFailure(
                "vector contains a non-finite value"
        );
    }

    @Test
    void shouldRejectPositiveInfinityVector() {
        server.enqueue(jsonResponse(
                """
                {
                  "vector": ["Infinity", 0.0, 0.0],
                  "dimension": 3,
                  "modelName": "test-model",
                  "modelRevision": "revision-1",
                  "embeddingVersion": "%s",
                  "textHash": "%s",
                  "normalized": true
                }
                """.formatted(
                        VERSION,
                        sha256(TEXT)
                )
        ));

        assertClientFailure(
                "vector contains a non-finite value"
        );
    }

    @Test
    void shouldRejectNonNormalizedVector() {
        ObjectNode body = validBodyNode();

        ArrayNode vector = objectMapper.createArrayNode();
        vector.add(1.0);
        vector.add(1.0);
        vector.add(1.0);

        body.set("vector", vector);

        server.enqueue(jsonResponse(body));

        assertClientFailure(
                "vector is not L2 normalized"
        );
    }

    @Test
    void shouldWrapTimeout() {
        properties.setResponseTimeout(
                Duration.ofMillis(100)
        );

        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(validBody())
                        .setBodyDelay(
                                1,
                                TimeUnit.SECONDS
                        )
        );

        assertThatThrownBy(
                () -> createClient().embed(TEXT)
        )
                .isInstanceOf(
                        EmbeddingClientException.class
                )
                .hasMessageContaining("timed out");
    }

    @Test
    void shouldRejectBlankTextBeforeHttpCall() {
        assertThatThrownBy(
                () -> createClient().embed("   ")
        )
                .isInstanceOf(
                        EmbeddingClientException.class
                )
                .hasMessageContaining(
                        "must not be blank"
                );

        assertThat(server.getRequestCount()).isZero();
    }

    private HttpEmbeddingClient createClient() {
        return new HttpEmbeddingClient(
                WebClient.builder(),
                properties
        );
    }

    private void assertClientFailure(
            String expectedMessage
    ) {
        assertThatThrownBy(
                () -> createClient().embed(TEXT)
        )
                .isInstanceOf(
                        EmbeddingClientException.class
                )
                .hasMessageContaining(expectedMessage);
    }

    private MockResponse jsonResponse(
            ObjectNode body
    ) {
        return jsonResponse(body.toString());
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

    private ObjectNode validBodyNode() {
        ObjectNode body = objectMapper.createObjectNode();

        ArrayNode vector = body.putArray("vector");
        vector.add(1.0);
        vector.add(0.0);
        vector.add(0.0);

        body.put("dimension", 3);
        body.put("modelName", "test-model");
        body.put("modelRevision", "revision-1");
        body.put("embeddingVersion", VERSION);
        body.put("textHash", sha256(TEXT));
        body.put("normalized", true);

        return body;
    }

    private String validBody() {
        return validBodyNode().toString();
    }

    private String sha256(String text) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(
                            text.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    )
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}