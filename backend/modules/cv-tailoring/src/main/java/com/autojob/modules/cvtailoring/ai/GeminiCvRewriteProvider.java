package com.autojob.modules.cvtailoring.ai;

import com.autojob.modules.cvtailoring.config.CvTailoringAiProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Order(10)
public class GeminiCvRewriteProvider
        implements CvRewriteProvider {

    private static final int MAX_RESPONSE_CHARS =
            250_000;

    private final CvTailoringAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiCvRewriteProvider(
            CvTailoringAiProperties properties,
            ObjectMapper objectMapper
    ) {
        this(
                properties,
                objectMapper,
                HttpClient
                        .newBuilder()
                        .connectTimeout(
                                properties.getConnectTimeout()
                        )
                        .build()
        );
    }

    GeminiCvRewriteProvider(
            CvTailoringAiProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );

        this.objectMapper =
                Objects.requireNonNull(
                        objectMapper,
                        "objectMapper must not be null"
                );

        this.httpClient =
                Objects.requireNonNull(
                        httpClient,
                        "httpClient must not be null"
                );
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public boolean isAvailable() {
        CvTailoringAiProperties.Provider provider =
                properties.getGemini();

        return properties.isEnabled()
                && provider != null
                && provider.isEnabled()
                && provider.hasApiKey();
    }

    @Override
    public RewriteResponse generate(
            RewriteRequest request
    ) {
        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        if (!isAvailable()) {
            throw new ProviderException(
                    FailureReason.CLIENT_ERROR,
                    "Gemini provider is not configured"
            );
        }

        CvTailoringAiProperties.Provider provider =
                properties.getGemini();

        String body =
                buildRequestBody(
                        request
                );

        URI uri =
                URI.create(
                        provider.normalizedBaseUrl()
                                + "/v1beta/models/"
                                + provider.getModel()
                                + ":generateContent"
                );

        HttpRequest httpRequest =
                HttpRequest
                        .newBuilder(
                                uri
                        )
                        .timeout(
                                properties.getRequestTimeout()
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .header(
                                "x-goog-api-key",
                                provider.getApiKey()
                        )
                        .POST(
                                HttpRequest
                                        .BodyPublishers
                                        .ofString(
                                                body,
                                                StandardCharsets.UTF_8
                                        )
                        )
                        .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(
                            httpRequest,
                            HttpResponse
                                    .BodyHandlers
                                    .ofString(
                                            StandardCharsets.UTF_8
                                    )
                    );

            ensureSuccess(
                    response.statusCode()
            );

            return parseResponse(
                    response.body()
            );

        } catch (ProviderException exception) {

            throw exception;

        } catch (HttpTimeoutException exception) {

            throw new ProviderException(
                    FailureReason.TIMEOUT,
                    null,
                    "Gemini request timed out",
                    exception
            );

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();

            throw new ProviderException(
                    FailureReason.NETWORK_ERROR,
                    null,
                    "Gemini request was interrupted",
                    exception
            );

        } catch (IOException exception) {

            throw new ProviderException(
                    FailureReason.NETWORK_ERROR,
                    null,
                    "Gemini request failed",
                    exception
            );

        } catch (RuntimeException exception) {

            throw new ProviderException(
                    FailureReason.CLIENT_ERROR,
                    null,
                    "Gemini request could not be created",
                    exception
            );
        }
    }

    private String buildRequestBody(
            RewriteRequest request
    ) {
        try {
            String inputJson =
                    objectMapper
                            .writeValueAsString(
                                    Map.of(
                                            "task",
                                            "generate_cv_rewrites",
                                            "input",
                                            request
                                    )
                            );

            Map<String, Object> responseFormat =
                    Map.of(
                            "text",
                            Map.of(
                                    /*
                                     * Runtime API expects enum value,
                                     * not MIME string "application/json".
                                     */
                                    "mimeType",
                                    "APPLICATION_JSON",

                                    "schema",
                                    CvRewriteProvider
                                            .responseSchema()
                            )
                    );

            Map<String, Object> generationConfig =
                    Map.of(
                            "maxOutputTokens",
                            properties
                                    .getMaxOutputTokens(),

                            "thinkingConfig",
                            Map.of(
                                    "thinkingLevel",
                                    "low"
                            ),

                            "responseFormat",
                            responseFormat
                    );

            Map<String, Object> body =
                    Map.of(
                            "systemInstruction",
                            Map.of(
                                    "parts",
                                    List.of(
                                            Map.of(
                                                    "text",
                                                    CvRewriteProvider
                                                            .systemInstruction()
                                            )
                                    )
                            ),

                            "contents",
                            List.of(
                                    Map.of(
                                            "role",
                                            "user",

                                            "parts",
                                            List.of(
                                                    Map.of(
                                                            "text",
                                                            inputJson
                                                    )
                                            )
                                    )
                            ),

                            "generationConfig",
                            generationConfig,

                            "store",
                            false
                    );

            return objectMapper
                    .writeValueAsString(
                            body
                    );

        } catch (JsonProcessingException exception) {

            throw new ProviderException(
                    FailureReason.CLIENT_ERROR,
                    null,
                    "Unable to serialize Gemini request",
                    exception
            );
        }
    }

    private RewriteResponse parseResponse(
            String responseBody
    ) {
        if (responseBody == null
                || responseBody.isBlank()
                || responseBody.length()
                > MAX_RESPONSE_CHARS) {

            throw invalidResponse(
                    "Gemini returned an empty or oversized response"
            );
        }

        try {
            JsonNode root =
                    objectMapper
                            .readTree(
                                    responseBody
                            );

            JsonNode candidates =
                    root.path(
                            "candidates"
                    );

            if (!candidates.isArray()
                    || candidates.isEmpty()) {

                throw invalidResponse(
                        "Gemini response did not contain candidates"
                );
            }

            JsonNode parts =
                    candidates
                            .path(0)
                            .path("content")
                            .path("parts");

            if (!parts.isArray()
                    || parts.isEmpty()) {

                throw invalidResponse(
                        "Gemini response did not contain content parts"
                );
            }

            String content =
                    parts
                            .path(0)
                            .path("text")
                            .asText(
                                    null
                            );

            if (content == null
                    || content.isBlank()) {

                throw invalidResponse(
                        "Gemini response did not contain text output"
                );
            }

            RewriteResponse parsed =
                    objectMapper
                            .readValue(
                                    content,
                                    RewriteResponse.class
                            );

            if (parsed.suggestions()
                    == null) {

                throw invalidResponse(
                        "Gemini response did not contain suggestions"
                );
            }

            return parsed;

        } catch (ProviderException exception) {

            throw exception;

        } catch (JsonProcessingException exception) {

            throw new ProviderException(
                    FailureReason.INVALID_RESPONSE,
                    null,
                    "Gemini returned malformed structured output",
                    exception
            );
        }
    }

    private void ensureSuccess(
            int statusCode
    ) {
        if (statusCode >= 200
                && statusCode < 300) {
            return;
        }

        FailureReason reason;

        if (statusCode == 429) {

            reason =
                    FailureReason.RATE_LIMIT;

        } else if (statusCode == 401
                || statusCode == 403) {

            reason =
                    FailureReason.AUTHENTICATION;

        } else if (statusCode >= 500) {

            reason =
                    FailureReason.SERVER_ERROR;

        } else {

            reason =
                    FailureReason.CLIENT_ERROR;
        }

        throw new ProviderException(
                reason,
                statusCode,
                "Gemini returned HTTP "
                        + statusCode
        );
    }

    private ProviderException invalidResponse(
            String message
    ) {
        return new ProviderException(
                FailureReason.INVALID_RESPONSE,
                message
        );
    }
}