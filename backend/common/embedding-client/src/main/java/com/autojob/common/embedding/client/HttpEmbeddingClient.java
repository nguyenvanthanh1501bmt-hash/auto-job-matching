package com.autojob.common.embedding.client;

import com.autojob.common.embedding.client.dto.EmbeddingRequest;
import com.autojob.common.embedding.client.dto.EmbeddingResponse;
import com.autojob.common.embedding.config.EmbeddingProperties;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
public class HttpEmbeddingClient implements EmbeddingClient {

    private static final String EMBEDDING_PATH =
            "/api/v1/embeddings";

    private static final double NORMALIZATION_TOLERANCE =
            1.0e-3;

    private final WebClient webClient;
    private final EmbeddingProperties properties;

    public HttpEmbeddingClient(
            WebClient.Builder webClientBuilder,
            EmbeddingProperties properties
    ) {
        this.properties = properties;

        int connectTimeoutMillis = Math.toIntExact(
                properties.getConnectTimeout().toMillis()
        );

        HttpClient httpClient = HttpClient.create()
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        connectTimeoutMillis
                )
                .responseTimeout(
                        properties.getResponseTimeout()
                );

        this.webClient = webClientBuilder.clone()
                .baseUrl(properties.normalizedBaseUrl())
                .clientConnector(
                        new ReactorClientHttpConnector(httpClient)
                )
                .build();
    }

    @Override
    public EmbeddingResponse embed(String text) {
        validateRequestText(text);

        String expectedTextHash = sha256(text);

        log.info(
                "Embedding HTTP request started textHash={}",
                expectedTextHash
        );

        try {
            EmbeddingResponse response = webClient.post()
                    .uri(EMBEDDING_PATH)
                    .bodyValue(new EmbeddingRequest(text))
                    .exchangeToMono(clientResponse -> {
                        HttpStatusCode statusCode =
                                clientResponse.statusCode();

                        if (statusCode.isError()) {
                            return clientResponse.releaseBody()
                                    .then(Mono.error(
                                            new EmbeddingClientException(
                                                    "Embedding service returned HTTP "
                                                            + statusCode.value()
                                            )
                                    ));
                        }

                        return clientResponse
                                .bodyToMono(EmbeddingResponse.class)
                                .switchIfEmpty(Mono.error(
                                        new EmbeddingClientException(
                                                "Embedding service returned an empty body"
                                        )
                                ));
                    })
                    .timeout(properties.getResponseTimeout())
                    .block();

            if (response == null) {
                throw new EmbeddingClientException(
                        "Embedding service returned an empty body"
                );
            }

            validateResponse(
                    response,
                    expectedTextHash
            );

            log.info(
                    "Embedding HTTP request completed "
                            + "textHash={} modelName={} "
                            + "embeddingVersion={} dimension={}",
                    expectedTextHash,
                    response.modelName(),
                    response.embeddingVersion(),
                    response.dimension()
            );

            return response;
        } catch (EmbeddingClientException exception) {
            throw exception;
        } catch (WebClientRequestException exception) {
            throw new EmbeddingClientException(
                    "Unable to connect to embedding service",
                    exception
            );
        } catch (RuntimeException exception) {
            if (hasCause(exception, TimeoutException.class)) {
                throw new EmbeddingClientException(
                        "Embedding service request timed out",
                        exception
                );
            }

            throw new EmbeddingClientException(
                    "Embedding service request failed",
                    exception
            );
        }
    }

    private void validateRequestText(String text) {
        if (text == null || text.isBlank()) {
            throw new EmbeddingClientException(
                    "Embedding text must not be blank"
            );
        }
    }

    private void validateResponse(
            EmbeddingResponse response,
            String expectedTextHash
    ) {
        Integer dimension = response.dimension();

        if (dimension == null || dimension <= 0) {
            throw invalidResponse(
                    "dimension is missing or invalid"
            );
        }

        if (dimension != properties.getExpectedDimension()) {
            throw invalidResponse(
                    "dimension mismatch: expected="
                            + properties.getExpectedDimension()
                            + ", actual="
                            + dimension
            );
        }

        List<Double> vector = response.vector();

        if (vector == null) {
            throw invalidResponse("vector is missing");
        }

        if (vector.isEmpty()) {
            throw invalidResponse("vector is empty");
        }

        if (vector.size() != dimension) {
            throw invalidResponse(
                    "vector length mismatch: dimension="
                            + dimension
                            + ", vectorLength="
                            + vector.size()
            );
        }

        validateVectorValues(vector);

        if (isBlank(response.modelName())) {
            throw invalidResponse("modelName is missing");
        }

        if (isBlank(response.modelRevision())) {
            throw invalidResponse("modelRevision is missing");
        }

        if (isBlank(response.embeddingVersion())) {
            throw invalidResponse(
                    "embeddingVersion is missing"
            );
        }

        if (properties.hasExpectedVersion()
                && !properties.getExpectedVersion()
                .trim()
                .equals(response.embeddingVersion())) {
            throw invalidResponse(
                    "embeddingVersion mismatch: expected="
                            + properties.getExpectedVersion().trim()
                            + ", actual="
                            + response.embeddingVersion()
            );
        }

        if (isBlank(response.textHash())) {
            throw invalidResponse("textHash is missing");
        }

        if (!isSha256(response.textHash())) {
            throw invalidResponse(
                    "textHash is not a valid SHA-256 value"
            );
        }

        if (!expectedTextHash.equals(response.textHash())) {
            throw invalidResponse(
                    "textHash does not match the request text"
            );
        }

        if (!Boolean.TRUE.equals(response.normalized())) {
            throw invalidResponse(
                    "normalized must be true"
            );
        }
    }

    private void validateVectorValues(List<Double> vector) {
        double sumSquares = 0.0;

        for (Double value : vector) {
            if (value == null || !Double.isFinite(value)) {
                throw invalidResponse(
                        "vector contains a non-finite value"
                );
            }

            sumSquares += value * value;
        }

        double norm = Math.sqrt(sumSquares);

        if (!Double.isFinite(norm) || norm <= 0.0) {
            throw invalidResponse(
                    "vector has an invalid L2 norm"
            );
        }

        if (Math.abs(norm - 1.0)
                > NORMALIZATION_TOLERANCE) {
            throw invalidResponse(
                    "vector is not L2 normalized"
            );
        }
    }

    private EmbeddingClientException invalidResponse(
            String detail
    ) {
        return new EmbeddingClientException(
                "Invalid embedding service response: "
                        + detail
        );
    }

    private String sha256(String text) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    text.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new EmbeddingClientException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }

    private boolean isSha256(String value) {
        return value.matches("^[a-f0-9]{64}$");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hasCause(
            Throwable throwable,
            Class<? extends Throwable> causeType
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}