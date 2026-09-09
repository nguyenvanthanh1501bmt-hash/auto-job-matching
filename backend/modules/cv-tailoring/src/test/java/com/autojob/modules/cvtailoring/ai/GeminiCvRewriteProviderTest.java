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

class GeminiCvRewriteProviderTest {

    private CvTailoringAiProperties properties;
    private ObjectMapper objectMapper;

    private HttpClient httpClient;

    private HttpResponse<String> httpResponse;

    private GeminiCvRewriteProvider provider;

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
                .getGemini()
                .setEnabled(
                        true
                );

        properties
                .getGemini()
                .setBaseUrl(
                        "https://gemini.test"
                );

        properties
                .getGemini()
                .setModel(
                        "gemini-test-model"
                );

        properties
                .getGemini()
                .setApiKey(
                        "gemini-test-key"
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
                new GeminiCvRewriteProvider(
                        properties,
                        objectMapper,
                        httpClient
                );
    }

    @Test
    void sendsCurrentGenerateContentStructuredOutputContractAndParsesResponse()
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
                                        "candidates",
                                        List.of(
                                                Map.of(
                                                        "content",
                                                        Map.of(
                                                                "parts",
                                                                List.of(
                                                                        Map.of(
                                                                                "text",
                                                                                structuredOutput
                                                                        )
                                                                )
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

        assertThat(
                response
                        .suggestions()
                        .getFirst()
                        .suggested()
        ).isEqualTo(
                "Developed Java backend services."
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
                "https://gemini.test/v1beta/models/"
                        + "gemini-test-model:generateContent"
        );

        assertThat(
                actualRequest
                        .headers()
                        .firstValue(
                                "x-goog-api-key"
                        )
        ).contains(
                "gemini-test-key"
        );

        assertThat(
                actualRequest
                        .headers()
                        .firstValue(
                                "Content-Type"
                        )
        ).contains(
                "application/json"
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
                        .path("systemInstruction")
                        .path("parts")
                        .path(0)
                        .path("text")
                        .asText()
        ).contains(
                "UNTRUSTED DATA"
        );

        assertThat(
                body
                        .path("contents")
                        .path(0)
                        .path("role")
                        .asText()
        ).isEqualTo(
                "user"
        );

        assertThat(
                body
                        .path("generationConfig")
                        .path("responseFormat")
                        .path("text")
                        .path("mimeType")
                        .asText()
        ).isEqualTo(
                "APPLICATION_JSON"
        );

        JsonNode schema =
                body
                        .path("generationConfig")
                        .path("responseFormat")
                        .path("text")
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
                        .path("properties")
                        .path("suggestions")
                        .path("type")
                        .asText()
        ).isEqualTo(
                "array"
        );

        assertThat(
                body
                        .path("generationConfig")
                        .has(
                                "responseMimeType"
                        )
        ).isFalse();

        assertThat(
                body
                        .path("generationConfig")
                        .has(
                                "responseJsonSchema"
                        )
        ).isFalse();

        assertThat(
                body
                        .path("generationConfig")
                        .path("thinkingConfig")
                        .path("thinkingLevel")
                        .asText()
        ).isEqualTo(
                "low"
        );

        assertThat(
                body
                        .path("generationConfig")
                        .path("maxOutputTokens")
                        .asInt()
        ).isEqualTo(
                900
        );

        assertThat(
                body
                        .path("store")
                        .asBoolean(
                                true
                        )
        ).isFalse();
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
                                        "candidates",
                                        List.of(
                                                Map.of(
                                                        "content",
                                                        Map.of(
                                                                "parts",
                                                                List.of(
                                                                        Map.of(
                                                                                "text",
                                                                                "not-json"
                                                                        )
                                                                )
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