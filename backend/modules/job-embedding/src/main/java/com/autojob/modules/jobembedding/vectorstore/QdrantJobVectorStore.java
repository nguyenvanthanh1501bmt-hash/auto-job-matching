package com.autojob.modules.jobembedding.vectorstore;

import com.autojob.modules.jobembedding.config.QdrantProperties;
import com.autojob.modules.jobembedding.search.JobVectorHit;
import com.autojob.modules.jobembedding.search.JobVectorSearchCriteria;
import com.autojob.modules.jobembedding.search.JobVectorSearchPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class QdrantJobVectorStore
        implements JobVectorStore, JobVectorSearchPort {

    private static final int MAX_ERROR_BODY_LENGTH =
            500;

    private static final String FIELD_NORMALIZED_JOB_ID =
            "normalizedJobId";

    private static final String FIELD_NORMALIZATION_VERSION =
            "normalizationVersion";

    private static final String FIELD_EMBEDDING_VERSION =
            "embeddingVersion";

    private static final String FIELD_TEXT_VERSION =
            "textVersion";

    private final QdrantProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    /**
     * Payload indexes chỉ cần ensure một lần
     * trong lifecycle hiện tại của application.
     */
    private volatile boolean searchIndexesReady =
            false;

    public QdrantJobVectorStore(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            QdrantProperties properties
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        HttpClient httpClient =
                HttpClient.create()
                        .option(
                                ChannelOption
                                        .CONNECT_TIMEOUT_MILLIS,
                                Math.toIntExact(
                                        properties
                                                .getConnectTimeout()
                                                .toMillis()
                                )
                        )
                        .responseTimeout(
                                properties
                                        .getResponseTimeout()
                        );

        this.webClient =
                webClientBuilder
                        .clone()
                        .baseUrl(
                                properties
                                        .normalizedBaseUrl()
                        )
                        .clientConnector(
                                new ReactorClientHttpConnector(
                                        httpClient
                                )
                        )
                        .build();
    }

    @Override
    public synchronized void ensureCollection() {
        JsonNode collection =
                fetchCollection();

        if (collection == null) {
            createCollection();

            collection =
                    fetchCollection();
        }

        if (collection == null) {
            throw new JobVectorStoreException(
                    "Qdrant collection was not available after creation: "
                            + properties.getCollection()
            );
        }

        validateCollection(
                collection
        );

        log.info(
                "Qdrant collection ready collection={} dimension={} distance={}",
                properties.getCollection(),
                properties.getDimension(),
                properties.getDistance()
        );
    }

    @Override
    public void upsert(
            JobVectorPoint point
    ) {
        validatePoint(
                point
        );

        ObjectNode request =
                objectMapper.createObjectNode();

        ArrayNode points =
                request.putArray("points");

        ObjectNode pointNode =
                points.addObject();

        pointNode.put(
                "id",
                point.pointId()
        );

        pointNode.set(
                "vector",
                objectMapper.valueToTree(
                        point.vector()
                )
        );

        ObjectNode payload =
                pointNode.putObject(
                        "payload"
                );

        /*
         * Canonical ID dùng cho matching.
         */
        payload.put(
                FIELD_NORMALIZED_JOB_ID,
                point.normalizedJobId()
        );

        /*
         * Legacy alias giữ tạm để không phá
         * dữ liệu/consumer cũ.
         *
         * Matching mới không dùng field này.
         */
        payload.put(
                "jobId",
                point.normalizedJobId()
        );

        payload.put(
                "sourceCode",
                point.sourceCode()
        );

        payload.put(
                FIELD_NORMALIZATION_VERSION,
                point.normalizationVersion()
        );

        payload.put(
                FIELD_EMBEDDING_VERSION,
                point.embeddingVersion()
        );

        payload.put(
                FIELD_TEXT_VERSION,
                point.textVersion()
        );

        payload.put(
                "textHash",
                point.textHash()
        );

        executeRequiredBody(
                webClient.put()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .pathSegment(
                                                        "collections",
                                                        properties
                                                                .getCollection(),
                                                        "points"
                                                )
                                                .queryParam(
                                                        "wait",
                                                        true
                                                )
                                                .build()
                        )
                        .bodyValue(
                                request
                        )
        );

        log.info(
                "Qdrant point upserted collection={} "
                        + "normalizedJobId={} "
                        + "embeddingVersion={} "
                        + "textVersion={} "
                        + "textHash={} "
                        + "qdrantPointId={}",
                properties.getCollection(),
                point.normalizedJobId(),
                point.embeddingVersion(),
                point.textVersion(),
                point.textHash(),
                point.pointId()
        );
    }

    @Override
    public boolean pointExists(
            String pointId
    ) {
        if (pointId == null
                || pointId.isBlank()) {
            return false;
        }

        JsonNode response =
                webClient.get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .pathSegment(
                                                        "collections",
                                                        properties
                                                                .getCollection(),
                                                        "points",
                                                        pointId
                                                )
                                                .queryParam(
                                                        "with_payload",
                                                        false
                                                )
                                                .queryParam(
                                                        "with_vector",
                                                        false
                                                )
                                                .build()
                        )
                        .exchangeToMono(
                                clientResponse -> {
                                    if (clientResponse
                                            .statusCode()
                                            .value()
                                            == 404) {
                                        return clientResponse
                                                .releaseBody()
                                                .thenReturn(
                                                        NullNode
                                                                .getInstance()
                                                );
                                    }

                                    return decodeRequiredBody(
                                            clientResponse
                                    );
                                }
                        )
                        .timeout(
                                properties
                                        .getResponseTimeout()
                        )
                        .onErrorMap(
                                throwable ->
                                        wrapFailure(
                                                "Failed to check Qdrant point",
                                                throwable
                                        )
                        )
                        .block();

        if (response == null
                || response.isNull()) {
            return false;
        }

        JsonNode result =
                response.path(
                        "result"
                );

        if (!result.isObject()) {
            throw new JobVectorStoreException(
                    "Qdrant point lookup returned an invalid body"
            );
        }

        return true;
    }

    /**
     * Semantic retrieval dành cho Matching Engine.
     *
     * Chỉ chịu trách nhiệm:
     *
     * query vector
     * +
     * compatibility filters
     * ->
     * ordered vector hits
     *
     * Không thực hiện hybrid reranking ở đây.
     */
    @Override
    public List<JobVectorHit> search(
            List<Double> queryVector,
            JobVectorSearchCriteria criteria
    ) {
        validateQueryVector(
                queryVector
        );

        if (criteria == null) {
            throw new IllegalArgumentException(
                    "criteria must not be null"
            );
        }

        /*
         * Đảm bảo collection contract đúng
         * trước khi search.
         */
        ensureCollection();

        /*
         * Những field này được dùng liên tục
         * trong filtered semantic search.
         */
        ensureSearchIndexes();

        ObjectNode request =
                objectMapper.createObjectNode();

        request.set(
                "vector",
                objectMapper.valueToTree(
                        queryVector
                )
        );

        request.put(
                "limit",
                criteria.limit()
        );

        request.put(
                "with_payload",
                true
        );

        request.put(
                "with_vector",
                false
        );

        ObjectNode filter =
                request.putObject(
                        "filter"
                );

        ArrayNode must =
                filter.putArray(
                        "must"
                );

        addMatchCondition(
                must,
                FIELD_NORMALIZATION_VERSION,
                criteria.normalizationVersion()
        );

        addMatchCondition(
                must,
                FIELD_EMBEDDING_VERSION,
                criteria.embeddingVersion()
        );

        addMatchCondition(
                must,
                FIELD_TEXT_VERSION,
                criteria.textVersion()
        );

        JsonNode response =
                executeRequiredBody(
                        webClient.post()
                                .uri(
                                        uriBuilder ->
                                                uriBuilder
                                                        .pathSegment(
                                                                "collections",
                                                                properties
                                                                        .getCollection(),
                                                                "points",
                                                                "search"
                                                        )
                                                        .build()
                                )
                                .bodyValue(
                                        request
                                )
                );

        List<JobVectorHit> hits =
                parseSearchHits(
                        response
                );

        log.info(
                "Qdrant job search completed "
                        + "collection={} "
                        + "limit={} "
                        + "normalizationVersion={} "
                        + "embeddingVersion={} "
                        + "textVersion={} "
                        + "hits={}",
                properties.getCollection(),
                criteria.limit(),
                criteria.normalizationVersion(),
                criteria.embeddingVersion(),
                criteria.textVersion(),
                hits.size()
        );

        return hits;
    }

    private synchronized void ensureSearchIndexes() {
        if (searchIndexesReady) {
            return;
        }

        createKeywordPayloadIndex(
                FIELD_NORMALIZED_JOB_ID
        );

        createKeywordPayloadIndex(
                FIELD_NORMALIZATION_VERSION
        );

        createKeywordPayloadIndex(
                FIELD_EMBEDDING_VERSION
        );

        createKeywordPayloadIndex(
                FIELD_TEXT_VERSION
        );

        searchIndexesReady = true;

        log.info(
                "Qdrant matching payload indexes ready collection={}",
                properties.getCollection()
        );
    }

    private void createKeywordPayloadIndex(
            String fieldName
    ) {
        ObjectNode request =
                objectMapper.createObjectNode();

        request.put(
                "field_name",
                fieldName
        );

        request.put(
                "field_schema",
                "keyword"
        );

        executeRequiredBody(
                webClient.put()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .pathSegment(
                                                        "collections",
                                                        properties
                                                                .getCollection(),
                                                        "index"
                                                )
                                                .queryParam(
                                                        "wait",
                                                        true
                                                )
                                                .build()
                        )
                        .bodyValue(
                                request
                        )
        );
    }

    private void addMatchCondition(
            ArrayNode conditions,
            String key,
            String value
    ) {
        ObjectNode condition =
                conditions.addObject();

        condition.put(
                "key",
                key
        );

        condition
                .putObject("match")
                .put(
                        "value",
                        value
                );
    }

    private List<JobVectorHit> parseSearchHits(
            JsonNode response
    ) {
        JsonNode result =
                response.path(
                        "result"
                );

        if (!result.isArray()) {
            throw new JobVectorStoreException(
                    "Qdrant search returned an invalid result body"
            );
        }

        List<JobVectorHit> hits =
                new ArrayList<>();

        for (JsonNode resultItem : result) {
            JsonNode payload =
                    resultItem.path(
                            "payload"
                    );

            if (!payload.isObject()) {
                throw new JobVectorStoreException(
                        "Qdrant search result is missing payload"
                );
            }

            String normalizedJobId =
                    textOrNull(
                            payload.path(
                                    FIELD_NORMALIZED_JOB_ID
                            )
                    );

            /*
             * Compatibility fallback cho point cũ
             * trước Batch 2.
             */
            if (normalizedJobId == null) {
                normalizedJobId =
                        textOrNull(
                                payload.path(
                                        "jobId"
                                )
                        );
            }

            if (normalizedJobId == null) {
                throw new JobVectorStoreException(
                        "Qdrant search result is missing normalizedJobId"
                );
            }

            JsonNode scoreNode =
                    resultItem.path(
                            "score"
                    );

            if (!scoreNode.isNumber()) {
                throw new JobVectorStoreException(
                        "Qdrant search result is missing score"
                );
            }

            double score =
                    scoreNode.asDouble();

            if (!Double.isFinite(
                    score
            )) {
                throw new JobVectorStoreException(
                        "Qdrant search result contains a non-finite score"
                );
            }

            String pointId =
                    textOrNull(
                            resultItem.path(
                                    "id"
                            )
                    );

            if (pointId == null) {
                throw new JobVectorStoreException(
                        "Qdrant search result is missing point id"
                );
            }

            hits.add(
                    new JobVectorHit(
                            normalizedJobId,
                            pointId,
                            score
                    )
            );
        }

        return List.copyOf(
                hits
        );
    }

    private String textOrNull(
            JsonNode node
    ) {
        if (node == null
                || node.isNull()
                || node.isMissingNode()) {
            return null;
        }

        String value =
                node.asText();

        if (value == null
                || value.isBlank()) {
            return null;
        }

        return value;
    }

    private JsonNode fetchCollection() {
        JsonNode response =
                webClient.get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .pathSegment(
                                                        "collections",
                                                        properties
                                                                .getCollection()
                                                )
                                                .build()
                        )
                        .exchangeToMono(
                                clientResponse -> {
                                    if (clientResponse
                                            .statusCode()
                                            .value()
                                            == 404) {
                                        return clientResponse
                                                .releaseBody()
                                                .thenReturn(
                                                        NullNode
                                                                .getInstance()
                                                );
                                    }

                                    return decodeRequiredBody(
                                            clientResponse
                                    );
                                }
                        )
                        .timeout(
                                properties
                                        .getResponseTimeout()
                        )
                        .onErrorMap(
                                throwable ->
                                        wrapFailure(
                                                "Failed to read Qdrant collection",
                                                throwable
                                        )
                        )
                        .block();

        if (response == null
                || response.isNull()) {
            return null;
        }

        return response;
    }

    private void createCollection() {
        ObjectNode request =
                objectMapper.createObjectNode();

        ObjectNode vectors =
                request.putObject(
                        "vectors"
                );

        vectors.put(
                "size",
                properties.getDimension()
        );

        vectors.put(
                "distance",
                properties.getDistance()
        );

        webClient.put()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .pathSegment(
                                                "collections",
                                                properties
                                                        .getCollection()
                                        )
                                        .build()
                )
                .bodyValue(
                        request
                )
                .exchangeToMono(
                        clientResponse -> {
                            /*
                             * Một request khác có thể
                             * đã tạo collection sau lần
                             * GET của thread hiện tại.
                             */
                            if (clientResponse
                                    .statusCode()
                                    .value()
                                    == 409) {
                                return clientResponse
                                        .releaseBody()
                                        .thenReturn(
                                                NullNode
                                                        .getInstance()
                                        );
                            }

                            return decodeRequiredBody(
                                    clientResponse
                            );
                        }
                )
                .timeout(
                        properties
                                .getResponseTimeout()
                )
                .onErrorMap(
                        throwable ->
                                wrapFailure(
                                        "Failed to create Qdrant collection",
                                        throwable
                                )
                )
                .block();

        /*
         * Collection mới đồng nghĩa payload indexes
         * cần được ensure lại.
         */
        searchIndexesReady = false;

        log.info(
                "Qdrant collection creation requested "
                        + "collection={} "
                        + "dimension={} "
                        + "distance={}",
                properties.getCollection(),
                properties.getDimension(),
                properties.getDistance()
        );
    }

    private void validateCollection(
            JsonNode response
    ) {
        JsonNode vectors =
                response.path("result")
                        .path("config")
                        .path("params")
                        .path("vectors");

        JsonNode sizeNode =
                vectors.path(
                        "size"
                );

        JsonNode distanceNode =
                vectors.path(
                        "distance"
                );

        if (!sizeNode.canConvertToInt()) {
            throw new JobVectorStoreException(
                    "Qdrant collection does not use a supported unnamed vector"
            );
        }

        int actualDimension =
                sizeNode.asInt();

        if (actualDimension
                != properties.getDimension()) {
            throw new JobVectorStoreException(
                    "Qdrant collection dimension mismatch: expected="
                            + properties.getDimension()
                            + ", actual="
                            + actualDimension
            );
        }

        String actualDistance =
                distanceNode.asText("");

        if (!properties
                .getDistance()
                .equalsIgnoreCase(
                        actualDistance
                )) {
            throw new JobVectorStoreException(
                    "Qdrant collection distance mismatch: expected="
                            + properties.getDistance()
                            + ", actual="
                            + actualDistance
            );
        }
    }

    private void validatePoint(
            JobVectorPoint point
    ) {
        if (point == null) {
            throw new JobVectorStoreException(
                    "Qdrant point must not be null"
            );
        }

        requireText(
                point.pointId(),
                "pointId"
        );

        requireText(
                point.normalizedJobId(),
                "normalizedJobId"
        );

        requireText(
                point.sourceCode(),
                "sourceCode"
        );

        requireText(
                point.normalizationVersion(),
                "normalizationVersion"
        );

        requireText(
                point.embeddingVersion(),
                "embeddingVersion"
        );

        requireText(
                point.textVersion(),
                "textVersion"
        );

        requireText(
                point.textHash(),
                "textHash"
        );

        validateVector(
                point.vector(),
                "Qdrant vector"
        );
    }

    private void validateQueryVector(
            List<Double> vector
    ) {
        validateVector(
                vector,
                "Qdrant query vector"
        );
    }

    private void validateVector(
            List<Double> vector,
            String fieldName
    ) {
        if (vector == null) {
            throw new JobVectorStoreException(
                    fieldName
                            + " must not be null"
            );
        }

        if (vector.size()
                != properties.getDimension()) {
            throw new JobVectorStoreException(
                    fieldName
                            + " dimension mismatch: expected="
                            + properties.getDimension()
                            + ", actual="
                            + vector.size()
            );
        }

        for (Double value : vector) {
            if (value == null
                    || !Double.isFinite(
                    value
            )) {
                throw new JobVectorStoreException(
                        fieldName
                                + " contains a non-finite value"
                );
            }
        }
    }

    private void requireText(
            String value,
            String fieldName
    ) {
        if (value == null
                || value.isBlank()) {
            throw new JobVectorStoreException(
                    fieldName
                            + " must not be blank"
            );
        }
    }

    private JsonNode executeRequiredBody(
            WebClient.RequestHeadersSpec<?> request
    ) {
        return request
                .exchangeToMono(
                        this::decodeRequiredBody
                )
                .timeout(
                        properties
                                .getResponseTimeout()
                )
                .onErrorMap(
                        throwable ->
                                wrapFailure(
                                        "Qdrant request failed",
                                        throwable
                                )
                )
                .block();
    }

    private Mono<JsonNode> decodeRequiredBody(
            ClientResponse response
    ) {
        if (response
                .statusCode()
                .isError()) {
            return response
                    .bodyToMono(
                            String.class
                    )
                    .defaultIfEmpty("")
                    .flatMap(
                            body ->
                                    Mono.error(
                                            new JobVectorStoreException(
                                                    "Qdrant returned HTTP "
                                                            + response
                                                            .statusCode()
                                                            .value()
                                                            + formatResponseBody(
                                                            body
                                                    )
                                            )
                                    )
                    );
        }

        return response
                .bodyToMono(
                        JsonNode.class
                )
                .switchIfEmpty(
                        Mono.error(
                                new JobVectorStoreException(
                                        "Qdrant returned an empty response body"
                                )
                        )
                );
    }

    private RuntimeException wrapFailure(
            String message,
            Throwable throwable
    ) {
        if (throwable
                instanceof JobVectorStoreException exception) {
            return exception;
        }

        return new JobVectorStoreException(
                message,
                throwable
        );
    }

    private String formatResponseBody(
            String body
    ) {
        if (body == null
                || body.isBlank()) {
            return "";
        }

        String normalized =
                body.replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        if (normalized.length()
                > MAX_ERROR_BODY_LENGTH) {
            normalized =
                    normalized.substring(
                            0,
                            MAX_ERROR_BODY_LENGTH
                    );
        }

        return ": " + normalized;
    }
}