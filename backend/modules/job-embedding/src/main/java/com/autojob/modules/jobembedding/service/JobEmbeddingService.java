package com.autojob.modules.jobembedding.service;

import com.autojob.common.embedding.client.EmbeddingClient;
import com.autojob.common.embedding.client.dto.EmbeddingResponse;
import com.autojob.common.embedding.config.EmbeddingProperties;
import com.autojob.common.embedding.service.EmbeddingTextHashCalculator;
import com.autojob.modules.jobembedding.config.JobEmbeddingProperties;
import com.autojob.modules.jobembedding.config.QdrantProperties;
import com.autojob.modules.jobembedding.domain.JobEmbedding;
import com.autojob.modules.jobembedding.domain.JobEmbeddingStatus;
import com.autojob.modules.jobembedding.repository.JobEmbeddingRepository;
import com.autojob.modules.jobembedding.text.JobEmbeddingTextBuilder;
import com.autojob.modules.jobembedding.vectorstore.JobVectorPoint;
import com.autojob.modules.jobembedding.vectorstore.JobVectorStore;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.exception.NormalizedJobNotFoundException;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobEmbeddingService {

    private final NormalizedJobRepository
            normalizedJobRepository;

    private final JobEmbeddingRepository
            jobEmbeddingRepository;

    private final EmbeddingClient
            embeddingClient;

    private final JobVectorStore
            jobVectorStore;

    private final EmbeddingTextHashCalculator
            textHashCalculator;

    private final JobEmbeddingTextBuilder
            textBuilder;

    private final JobEmbeddingPointIdFactory
            pointIdFactory;

    private final EmbeddingProperties
            embeddingProperties;

    private final JobEmbeddingProperties
            jobEmbeddingProperties;

    private final QdrantProperties
            qdrantProperties;

    private final Clock normalizationClock;

    public JobEmbedding embed(
            String normalizedJobId
    ) {
        return embed(
                normalizedJobId,
                false
        );
    }

    public JobEmbedding embed(
            String normalizedJobId,
            boolean force
    ) {
        validateNormalizedJobId(
                normalizedJobId
        );

        NormalizedJob job =
                normalizedJobRepository
                        .findById(
                                normalizedJobId
                        )
                        .orElseThrow(
                                () ->
                                        new NormalizedJobNotFoundException(
                                                normalizedJobId
                                        )
                        );

        String embeddingVersion =
                currentEmbeddingVersion();

        String textVersion =
                currentTextVersion();

        /*
         * Quan trọng:
         *
         * JobEmbeddingService tự build text từ
         * structured NormalizedJob.
         *
         * Không dùng normalizedJob.getEmbeddingText()
         * legacy nữa.
         */
        String embeddingText =
                textBuilder.build(job);

        validateEmbeddingText(
                embeddingText
        );

        String textHash =
                textHashCalculator.calculate(
                        embeddingText
                );

        JobEmbedding record =
                loadOrCreateRecord(
                        job,
                        embeddingVersion,
                        textVersion,
                        textHash
                );

        /*
         * Nếu Mongo metadata và Qdrant point
         * đều còn đúng thì bỏ qua.
         */
        if (!force
                && isReadyAndUnchanged(
                record,
                textHash
        )) {
            return record;
        }

        /*
         * Nếu record cũ FAILED/READY/stale
         * thì đưa lại về PROCESSING.
         */
        if (record.getStatus()
                != JobEmbeddingStatus.PROCESSING
                || !Objects.equals(
                record.getTextHash(),
                textHash
        )) {
            applyProcessingState(
                    record,
                    job,
                    embeddingVersion,
                    textVersion,
                    textHash
            );

            record =
                    jobEmbeddingRepository.save(
                            record
                    );
        }

        try {
            EmbeddingResponse response =
                    embeddingClient.embed(
                            embeddingText
                    );

            validateEmbeddingResponse(
                    response,
                    embeddingVersion,
                    textHash
            );

            /*
             * Point ID vẫn giữ contract:
             *
             * normalizedJobId + embeddingVersion.
             *
             * Khi textVersion đổi, vector mới
             * overwrite active point cũ thay vì
             * tạo duplicate vector cho cùng job.
             *
             * textVersion được lưu trong payload.
             */
            String pointId =
                    pointIdFactory.create(
                            normalizedJobId,
                            embeddingVersion
                    );

            jobVectorStore.ensureCollection();

            jobVectorStore.upsert(
                    new JobVectorPoint(
                            pointId,
                            normalizedJobId,
                            job.getSourceCode(),
                            job.getNormalizationVersion(),
                            embeddingVersion,
                            textVersion,
                            textHash,
                            response.vector()
                    )
            );

            Instant now =
                    Instant.now(
                            normalizationClock
                    );

            record.setNormalizationVersion(
                    job.getNormalizationVersion()
            );

            record.setTextVersion(
                    textVersion
            );

            record.setModelName(
                    response.modelName()
            );

            record.setModelRevision(
                    response.modelRevision()
            );

            record.setEmbeddingVersion(
                    response.embeddingVersion()
            );

            record.setTextHash(
                    response.textHash()
            );

            record.setDimension(
                    response.dimension()
            );

            record.setNormalized(
                    response.normalized()
            );

            record.setQdrantCollection(
                    qdrantProperties.getCollection()
            );

            record.setQdrantPointId(
                    pointId
            );

            record.setStatus(
                    JobEmbeddingStatus.READY
            );

            record.setLastError(null);
            record.setEmbeddedAt(now);
            record.setUpdatedAt(now);

            JobEmbedding saved =
                    jobEmbeddingRepository.save(
                            record
                    );

            log.info(
                    "Job embedding ready "
                            + "normalizedJobId={} "
                            + "embeddingVersion={} "
                            + "textVersion={} "
                            + "qdrantPointId={}",
                    normalizedJobId,
                    embeddingVersion,
                    textVersion,
                    pointId
            );

            return saved;
        } catch (RuntimeException exception) {
            markFailed(
                    record,
                    job,
                    embeddingVersion,
                    textVersion,
                    textHash,
                    exception
            );

            if (exception
                    instanceof JobEmbeddingProcessingException
                    processingException) {
                throw processingException;
            }

            throw new JobEmbeddingProcessingException(
                    normalizedJobId,
                    safeError(exception),
                    exception
            );
        }
    }

    public JobEmbedding getLatest(
            String normalizedJobId
    ) {
        validateNormalizedJobId(
                normalizedJobId
        );

        return jobEmbeddingRepository
                .findFirstByNormalizedJobIdOrderByUpdatedAtDesc(
                        normalizedJobId
                )
                .orElseThrow(
                        () ->
                                new JobEmbeddingNotFoundException(
                                        normalizedJobId
                                )
                );
    }

    private JobEmbedding loadOrCreateRecord(
            NormalizedJob job,
            String embeddingVersion,
            String textVersion,
            String textHash
    ) {
        return jobEmbeddingRepository
                .findByNormalizedJobIdAndEmbeddingVersionAndTextVersion(
                        job.getId(),
                        embeddingVersion,
                        textVersion
                )
                .orElseGet(
                        () ->
                                insertProcessingRecord(
                                        job,
                                        embeddingVersion,
                                        textVersion,
                                        textHash
                                )
                );
    }

    private JobEmbedding insertProcessingRecord(
            NormalizedJob job,
            String embeddingVersion,
            String textVersion,
            String textHash
    ) {
        Instant now =
                Instant.now(
                        normalizationClock
                );

        JobEmbedding record =
                JobEmbedding.builder()
                        .normalizedJobId(
                                job.getId()
                        )
                        .normalizationVersion(
                                job.getNormalizationVersion()
                        )
                        .textVersion(
                                textVersion
                        )
                        .embeddingVersion(
                                embeddingVersion
                        )
                        .textHash(
                                textHash
                        )
                        .dimension(
                                embeddingProperties
                                        .getExpectedDimension()
                        )
                        .qdrantCollection(
                                qdrantProperties
                                        .getCollection()
                        )
                        .qdrantPointId(
                                pointIdFactory.create(
                                        job.getId(),
                                        embeddingVersion
                                )
                        )
                        .status(
                                JobEmbeddingStatus.PROCESSING
                        )
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        try {
            return jobEmbeddingRepository.insert(
                    record
            );
        } catch (DuplicateKeyException exception) {
            return jobEmbeddingRepository
                    .findByNormalizedJobIdAndEmbeddingVersionAndTextVersion(
                            job.getId(),
                            embeddingVersion,
                            textVersion
                    )
                    .orElseThrow(
                            () -> exception
                    );
        }
    }

    private void applyProcessingState(
            JobEmbedding record,
            NormalizedJob job,
            String embeddingVersion,
            String textVersion,
            String textHash
    ) {
        Instant now =
                Instant.now(
                        normalizationClock
                );

        record.setNormalizedJobId(
                job.getId()
        );

        record.setNormalizationVersion(
                job.getNormalizationVersion()
        );

        record.setTextVersion(
                textVersion
        );

        record.setEmbeddingVersion(
                embeddingVersion
        );

        record.setTextHash(
                textHash
        );

        record.setDimension(
                embeddingProperties
                        .getExpectedDimension()
        );

        record.setNormalized(null);
        record.setModelName(null);
        record.setModelRevision(null);

        record.setQdrantCollection(
                qdrantProperties.getCollection()
        );

        record.setQdrantPointId(
                pointIdFactory.create(
                        job.getId(),
                        embeddingVersion
                )
        );

        record.setStatus(
                JobEmbeddingStatus.PROCESSING
        );

        record.setLastError(null);
        record.setEmbeddedAt(null);

        if (record.getCreatedAt() == null) {
            record.setCreatedAt(now);
        }

        record.setUpdatedAt(now);
    }

    private boolean isReadyAndUnchanged(
            JobEmbedding record,
            String textHash
    ) {
        if (record.getStatus()
                != JobEmbeddingStatus.READY) {
            return false;
        }

        if (!Objects.equals(
                record.getTextHash(),
                textHash
        )) {
            return false;
        }

        if (record.getQdrantPointId() == null
                || record
                .getQdrantPointId()
                .isBlank()) {
            return false;
        }

        return jobVectorStore.pointExists(
                record.getQdrantPointId()
        );
    }

    private void validateEmbeddingResponse(
            EmbeddingResponse response,
            String expectedEmbeddingVersion,
            String expectedTextHash
    ) {
        if (response == null) {
            throw new IllegalStateException(
                    "Embedding response must not be null"
            );
        }

        if (!Objects.equals(
                response.embeddingVersion(),
                expectedEmbeddingVersion
        )) {
            throw new IllegalStateException(
                    "Embedding response version mismatch"
            );
        }

        if (!Objects.equals(
                response.textHash(),
                expectedTextHash
        )) {
            throw new IllegalStateException(
                    "Embedding response text hash mismatch"
            );
        }

        if (response.dimension() == null
                || response.dimension()
                != embeddingProperties
                .getExpectedDimension()
                || response.dimension()
                != qdrantProperties
                .getDimension()) {
            throw new IllegalStateException(
                    "Embedding response dimension mismatch"
            );
        }

        if (!Boolean.TRUE.equals(
                response.normalized()
        )) {
            throw new IllegalStateException(
                    "Embedding response is not normalized"
            );
        }

        if (isBlank(response.modelName())
                || isBlank(
                response.modelRevision()
        )) {
            throw new IllegalStateException(
                    "Embedding response model metadata is blank"
            );
        }

        List<Double> vector =
                response.vector();

        if (vector == null
                || vector.size()
                != response.dimension()) {
            throw new IllegalStateException(
                    "Embedding response vector length mismatch"
            );
        }

        for (Double value : vector) {
            if (value == null
                    || !Double.isFinite(value)) {
                throw new IllegalStateException(
                        "Embedding response contains a non-finite value"
                );
            }
        }
    }

    private void markFailed(
            JobEmbedding record,
            NormalizedJob job,
            String embeddingVersion,
            String textVersion,
            String textHash,
            RuntimeException failure
    ) {
        try {
            Instant now =
                    Instant.now(
                            normalizationClock
                    );

            record.setNormalizedJobId(
                    job.getId()
            );

            record.setNormalizationVersion(
                    job.getNormalizationVersion()
            );

            record.setTextVersion(
                    textVersion
            );

            record.setEmbeddingVersion(
                    embeddingVersion
            );

            record.setTextHash(
                    textHash
            );

            record.setStatus(
                    JobEmbeddingStatus.FAILED
            );

            record.setLastError(
                    safeError(failure)
            );

            record.setUpdatedAt(now);

            if (record.getCreatedAt() == null) {
                record.setCreatedAt(now);
            }

            jobEmbeddingRepository.save(
                    record
            );
        } catch (RuntimeException persistenceFailure) {
            log.error(
                    "Unable to persist FAILED "
                            + "job embedding "
                            + "normalizedJobId={} "
                            + "error={}",
                    job.getId(),
                    safeError(
                            persistenceFailure
                    )
            );
        }
    }

    private String currentEmbeddingVersion() {
        if (!embeddingProperties
                .hasExpectedVersion()) {
            throw new IllegalStateException(
                    "autojob.embedding.expected-version must not be blank"
            );
        }

        return embeddingProperties
                .getExpectedVersion()
                .trim();
    }

    private String currentTextVersion() {
        String value =
                jobEmbeddingProperties
                        .getTextVersion();

        if (value == null
                || value.isBlank()) {
            throw new IllegalStateException(
                    "autojob.job-embedding.text-version must not be blank"
            );
        }

        return value.trim();
    }

    private void validateEmbeddingText(
            String text
    ) {
        if (text == null
                || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Job embedding text must not be blank"
            );
        }
    }

    private void validateNormalizedJobId(
            String normalizedJobId
    ) {
        if (normalizedJobId == null
                || normalizedJobId.isBlank()) {
            throw new IllegalArgumentException(
                    "normalizedJobId must not be blank"
            );
        }
    }

    private String safeError(
            Throwable throwable
    ) {
        String message =
                throwable.getMessage();

        if (message == null
                || message.isBlank()) {
            message = throwable
                    .getClass()
                    .getSimpleName();
        }

        String normalized =
                message
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .replaceAll(
                                "(?i)bearer\\s+[^\\s,;]+",
                                "Bearer [REDACTED]"
                        )
                        .replaceAll(
                                "(?i)(token|password|secret)=([^\\s&]+)",
                                "$1=[REDACTED]"
                        )
                        .trim();

        String result =
                throwable
                        .getClass()
                        .getSimpleName()
                        + ": "
                        + normalized;

        int maxLength =
                embeddingProperties
                        .getMaxErrorLength();

        if (result.length() > maxLength) {
            return result.substring(
                    0,
                    maxLength
            );
        }

        return result;
    }

    private boolean isBlank(
            String value
    ) {
        return value == null
                || value.isBlank();
    }
}