package com.autojob.modules.cvtailoring.ai;

import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.EditableNode;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.EvidenceValue;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.FailureReason;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.JobContext;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.ProviderException;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteRequest;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteResponse;
import com.autojob.modules.cvtailoring.config.CvTailoringAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroqCvRewriteProviderTest {

    private CvTailoringAiProperties properties;
    private ObjectMapper objectMapper;

    private HttpClient httpClient;

    private HttpResponse<String> httpResponse;

    private GroqCvRewriteProvider provider;

    @BeforeEach
    void setUp() {
        properties =
                new CvTailoringAiProperties();

        properties.setEnabled(
                true
        );

        properties.setMaxOutputTokens(
                900
        );

        properties
                .getGroq()
                .setEnabled(
                        true
                );

        properties
                .getGroq()
                .setBaseUrl(
                        "https://groq.test/openai/v1"
                );

        properties
                .getGroq()
                .setModel(
                        "openai/gpt-oss-120b"
                );

        properties
                .getGroq()
                .setApiKey(
                        "groq-test-key"
                );

        objectMapper =
                new ObjectMapper()
                        .findAndRegisterModules();

        httpClient =
                mock(
                        HttpClient.class
                );

        @SuppressWarnings("unchecked")
        HttpResponse<String> response =
                (HttpResponse<String>) mock(
                        HttpResponse.class
                );

        httpResponse =
                response;

        provider =
                new GroqCvRewriteProvider(
                        properties,
                        objectMapper,
                        httpClient
                );
    }

    @Test
    void sendsStrictStructuredOutputContractAndParsesResponse()
            throws Exception {

        String structuredOutput =
                objectMapper
                        .writeValueAsString(
                                Map.of(
                                        "suggestions",
                                        List.of(
                                                Map.of(
                                                        "sourceId",
                                                        "work:0:responsibility:0",

                                                        "suggested",
                                                        "Developed Java backend services.",

                                                        "targetSkills",
                                                        List.of(
                                                                "Java"
                                                        ),

                                                        "evidenceIds",
                                                        List.of(
                                                                "work:0:skill:0"
                                                        )
                                                )
                                        )
                                )
                        );

        String providerBody =
                objectMapper
                        .writeValueAsString(
                                Map.of(
                                        "choices",
                                        List.of(
                                                Map.of(
                                                        "message",
                                                        Map.of(
                                                                "content",
                                                                structuredOutput
                                                        )
                                                )
                                        )
                                )
                        );

        when(
                httpResponse.statusCode()
        ).thenReturn(
                200
        );

        when(
                httpResponse.body()
        ).thenReturn(
                providerBody
        );

        when(
                httpClient.send(
                        any(
                                HttpRequest.class
                        ),
                        ArgumentMatchers
                                .<HttpResponse.BodyHandler<String>>any()
                )
        ).thenReturn(
                httpResponse
        );

        RewriteResponse response =
                provider.generate(
                        request()
                );

        assertThat(
                response.suggestions()
        ).hasSize(
                1
        );

        assertThat(
                response
                        .suggestions()
                        .getFirst()
                        .sourceId()
        ).isEqualTo(
                "work:0:responsibility:0"
        );

        ArgumentCaptor<HttpRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        HttpRequest.class
                );

        verify(
                httpClient
        ).send(
                requestCaptor.capture(),
                ArgumentMatchers
                        .<HttpResponse.BodyHandler<String>>any()
        );

        HttpRequest actualRequest =
                requestCaptor.getValue();

        assertThat(
                actualRequest
                        .uri()
                        .toString()
        ).isEqualTo(
                "https://groq.test/openai/v1/chat/completions"
        );

        assertThat(
                actualRequest
                        .headers()
                        .firstValue(
                                "Authorization"
                        )
        ).contains(
                "Bearer groq-test-key"
        );

        JsonNode body =
                objectMapper
                        .readTree(
                                readBody(
                                        actualRequest
                                )
                        );

        assertThat(
                body
                        .path("model")
                        .asText()
        ).isEqualTo(
                "openai/gpt-oss-120b"
        );

        assertThat(
                body
                        .path("reasoning_effort")
                        .asText()
        ).isEqualTo(
                "low"
        );

        assertThat(
                body
                        .path("max_completion_tokens")
                        .asInt()
        ).isEqualTo(
                900
        );

        assertThat(
                body
                        .path("response_format")
                        .path("type")
                        .asText()
        ).isEqualTo(
                "json_schema"
        );

        assertThat(
                body
                        .path("response_format")
                        .path("json_schema")
                        .path("strict")
                        .asBoolean()
        ).isTrue();

        JsonNode schema =
                body
                        .path("response_format")
                        .path("json_schema")
                        .path("schema");

        assertThat(
                schema
                        .path("type")
                        .asText()
        ).isEqualTo(
                "object"
        );

        assertThat(
                schema
                        .path("additionalProperties")
                        .asBoolean(
                                true
                        )
        ).isFalse();

        JsonNode suggestionSchema =
                schema
                        .path("properties")
                        .path("suggestions")
                        .path("items");

        assertThat(
                suggestionSchema
                        .path("additionalProperties")
                        .asBoolean(
                                true
                        )
        ).isFalse();

        assertThat(
                suggestionSchema
                        .path("required")
                        .size()
        ).isEqualTo(
                4
        );

        assertThat(
                body
                        .path("messages")
                        .path(0)
                        .path("role")
                        .asText()
        ).isEqualTo(
                "system"
        );

        assertThat(
                body
                        .path("messages")
                        .path(0)
                        .path("content")
                        .asText()
        ).contains(
                "UNTRUSTED DATA"
        );
    }

    @Test
    void maps429ToRateLimitFailure()
            throws Exception {

        when(
                httpResponse.statusCode()
        ).thenReturn(
                429
        );

        when(
                httpResponse.body()
        ).thenReturn(
                "{}"
        );

        when(
                httpClient.send(
                        any(
                                HttpRequest.class
                        ),
                        ArgumentMatchers
                                .<HttpResponse.BodyHandler<String>>any()
                )
        ).thenReturn(
                httpResponse
        );

        assertThatThrownBy(
                () ->
                        provider.generate(
                                request()
                        )
        )
                .isInstanceOf(
                        ProviderException.class
                )
                .satisfies(
                        throwable -> {

                            ProviderException exception =
                                    (ProviderException) throwable;

                            assertThat(
                                    exception.reason()
                            ).isEqualTo(
                                    FailureReason.RATE_LIMIT
                            );

                            assertThat(
                                    exception.statusCode()
                            ).isEqualTo(
                                    429
                            );
                        }
                );
    }

    @Test
    void rejectsMalformedStructuredOutput()
            throws Exception {

        String providerBody =
                objectMapper
                        .writeValueAsString(
                                Map.of(
                                        "choices",
                                        List.of(
                                                Map.of(
                                                        "message",
                                                        Map.of(
                                                                "content",
                                                                "not-json"
                                                        )
                                                )
                                        )
                                )
                        );

        when(
                httpResponse.statusCode()
        ).thenReturn(
                200
        );

        when(
                httpResponse.body()
        ).thenReturn(
                providerBody
        );

        when(
                httpClient.send(
                        any(
                                HttpRequest.class
                        ),
                        ArgumentMatchers
                                .<HttpResponse.BodyHandler<String>>any()
                )
        ).thenReturn(
                httpResponse
        );

        assertThatThrownBy(
                () ->
                        provider.generate(
                                request()
                        )
        )
                .isInstanceOf(
                        ProviderException.class
                )
                .satisfies(
                        throwable -> {

                            ProviderException exception =
                                    (ProviderException) throwable;

                            assertThat(
                                    exception.reason()
                            ).isEqualTo(
                                    FailureReason.INVALID_RESPONSE
                            );
                        }
                );
    }

    private RewriteRequest request() {
        return new RewriteRequest(
                new JobContext(
                        "Backend Developer",
                        List.of(
                                "Java",
                                "Spring Boot"
                        ),
                        "Java and Spring Boot are required.",
                        "Develop backend applications."
                ),

                List.of(
                        new EditableNode(
                                "work:0:responsibility:0",
                                "WORK_EXPERIENCE",
                                "Work role: Backend Developer",
                                "Developed backend services.",
                                List.of(
                                        "work:0:responsibility:0",
                                        "work:0:skill:0"
                                )
                        )
                ),

                List.of(
                        new EvidenceValue(
                                "work:0:responsibility:0",
                                "TEXT",
                                "Developed backend services.",
                                ""
                        ),

                        new EvidenceValue(
                                "work:0:skill:0",
                                "SKILL",
                                "Java",
                                "java"
                        )
                )
        );
    }

    private String readBody(
            HttpRequest request
    ) {
        HttpRequest.BodyPublisher publisher =
                request
                        .bodyPublisher()
                        .orElseThrow();

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        CompletableFuture<byte[]> future =
                new CompletableFuture<>();

        publisher.subscribe(
                new Flow.Subscriber<>() {

                    @Override
                    public void onSubscribe(
                            Flow.Subscription subscription
                    ) {
                        subscription.request(
                                Long.MAX_VALUE
                        );
                    }

                    @Override
                    public void onNext(
                            ByteBuffer item
                    ) {
                        byte[] bytes =
                                new byte[
                                        item.remaining()
                                        ];

                        item.get(
                                bytes
                        );

                        output.writeBytes(
                                bytes
                        );
                    }

                    @Override
                    public void onError(
                            Throwable throwable
                    ) {
                        future.completeExceptionally(
                                throwable
                        );
                    }

                    @Override
                    public void onComplete() {
                        future.complete(
                                output.toByteArray()
                        );
                    }
                }
        );

        return new String(
                future.join(),
                StandardCharsets.UTF_8
        );
    }
}