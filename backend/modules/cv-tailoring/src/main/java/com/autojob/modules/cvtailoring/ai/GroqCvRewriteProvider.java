package com.autojob.modules.cvtailoring.ai;

import com.autojob.modules.cvtailoring.config.CvTailoringAiProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
public class GroqCvRewriteProvider
        implements CvRewriteProvider {

    private static final int MAX_RESPONSE_CHARS =
            250_000;

    private final CvTailoringAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public GroqCvRewriteProvider(
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

    GroqCvRewriteProvider(
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
        return "groq";
    }

    @Override
    public boolean isAvailable() {
        CvTailoringAiProperties.Provider provider =
                properties.getGroq();

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
                    "Groq provider is not configured"
            );
        }

        CvTailoringAiProperties.Provider provider =
                properties.getGroq();

        String body =
                buildRequestBody(
                        request
                );

        URI uri =
                URI.create(
                        provider.normalizedBaseUrl()
                                + "/chat/completions"
                );

        HttpRequest httpRequest =
                HttpRequest
                        .newBuilder(uri)
                        .timeout(
                                properties.getRequestTimeout()
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .header(
                                "Authorization",
                                "Bearer "
                                        + provider.getApiKey()
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        body,
                                        StandardCharsets.UTF_8
                                )
                        )
                        .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(
                            httpRequest,
                            HttpResponse.BodyHandlers.ofString(
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
                    "Groq request timed out",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new ProviderException(
                    FailureReason.NETWORK_ERROR,
                    null,
                    "Groq request was interrupted",
                    exception
            );
        } catch (IOException exception) {
            throw new ProviderException(
                    FailureReason.NETWORK_ERROR,
                    null,
                    "Groq request failed",
                    exception
            );
        } catch (RuntimeException exception) {
            throw new ProviderException(
                    FailureReason.CLIENT_ERROR,
                    null,
                    "Groq request could not be created",
                    exception
            );
        }
    }

    private String buildRequestBody(
            RewriteRequest request
    ) {
        try {
            String inputJson =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "task",
                                    "generate_cv_rewrites",
                                    "input",
                                    request
                            )
                    );

            Map<String, Object> body =
                    Map.of(
                            "model",
                            properties
                                    .getGroq()
                                    .getModel(),
                            "messages",
                            List.of(
                                    Map.of(
                                            "role",
                                            "system",
                                            "content",
                                            CvRewriteProvider
                                                    .systemInstruction()
                                    ),
                                    Map.of(
                                            "role",
                                            "user",
                                            "content",
                                            inputJson
                                    )
                            ),
                            "temperature",
                            0.2d,
                            "reasoning_effort",
                            "low",
                            "max_completion_tokens",
                            properties.getMaxOutputTokens(),
                            "response_format",
                            Map.of(
                                    "type",
                                    "json_schema",
                                    "json_schema",
                                    Map.of(
                                            "name",
                                            "cv_rewrite_response",
                                            "strict",
                                            true,
                                            "schema",
                                            CvRewriteProvider
                                                    .responseSchema()
                                    )
                            )
                    );

            return objectMapper.writeValueAsString(
                    body
            );
        } catch (JsonProcessingException exception) {
            throw new ProviderException(
                    FailureReason.CLIENT_ERROR,
                    null,
                    "Unable to serialize Groq request",
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
                    "Groq returned an empty or oversized response"
            );
        }

        try {
            JsonNode root =
                    objectMapper.readTree(
                            responseBody
                    );

            String content =
                    root.path("choices")
                            .path(0)
                            .path("message")
                            .path("content")
                            .asText(null);

            if (content == null
                    || content.isBlank()) {

                throw invalidResponse(
                        "Groq response did not contain text output"
                );
            }

            RewriteResponse parsed =
                    objectMapper.readValue(
                            content,
                            RewriteResponse.class
                    );

            if (parsed.suggestions() == null) {
                throw invalidResponse(
                        "Groq response did not contain suggestions"
                );
            }

            return parsed;
        } catch (ProviderException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new ProviderException(
                    FailureReason.INVALID_RESPONSE,
                    null,
                    "Groq returned malformed structured output",
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
            reason = FailureReason.RATE_LIMIT;
        } else if (statusCode == 401
                || statusCode == 403) {
            reason = FailureReason.AUTHENTICATION;
        } else if (statusCode >= 500) {
            reason = FailureReason.SERVER_ERROR;
        } else {
            reason = FailureReason.CLIENT_ERROR;
        }

        throw new ProviderException(
                reason,
                statusCode,
                "Groq returned HTTP "
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