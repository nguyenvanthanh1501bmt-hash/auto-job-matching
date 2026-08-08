package com.autojob.modules.jobembedding.service;

import com.autojob.common.embedding.client.EmbeddingClient;
import com.autojob.common.embedding.client.dto.EmbeddingResponse;
import com.autojob.common.embedding.config.EmbeddingProperties;
import com.autojob.common.embedding.service.EmbeddingTextHashCalculator;
import com.autojob.modules.jobembedding.config.QdrantProperties;
import com.autojob.modules.jobembedding.domain.JobEmbedding;
import com.autojob.modules.jobembedding.domain.JobEmbeddingStatus;
import com.autojob.modules.jobembedding.repository.JobEmbeddingRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobEmbeddingService {

    private final NormalizedJobRepository normalizedJobRepository;
    private final JobEmbeddingRepository jobEmbeddingRepository;
    private final EmbeddingClient embeddingClient;
    private final JobVectorStore jobVectorStore;
    private final EmbeddingTextHashCalculator textHashCalculator;
    private final JobEmbeddingPointIdFactory pointIdFactory;
    private final EmbeddingProperties embeddingProperties;
    private final QdrantProperties qdrantProperties;
    private final Clock normalizationClock;

    public JobEmbedding embed(String normalizedJobId) {
        return embed(normalizedJobId, false);
    }

    public JobEmbedding embed(
            String normalizedJobId,
            boolean force
    ) {
        validateNormalizedJobId(normalizedJobId);

        NormalizedJob normalizedJob = normalizedJobRepository
                .findById(normalizedJobId)
                .orElseThrow(
                        () -> new NormalizedJobNotFoundException(
                                normalizedJobId
                        )
                );

        String embeddingText = normalizedJob.getEmbeddingText();
        String textHash = null;
        String activeVersion = configuredExpectedVersion();
        JobEmbedding processingRecord = null;

        try {
            validateEmbeddingText(embeddingText);
            textHash = textHashCalculator.calculate(embeddingText);

            if (activeVersion != null) {
                ProcessingPreparation preparation =
                        prepareProcessing(
                                normalizedJob,
                                activeVersion,
                                textHash,
                                force
                        );

                if (!preparation.shouldProcess()) {
                    return preparation.embedding();
                }

                processingRecord = preparation.embedding();
            }

            log.info(
                    "Job embedding started normalizedJobId={} "
                            + "embeddingVersion={} textHash={} status=PROCESSING",
                    normalizedJobId,
                    activeVersion == null
                            ? "DISCOVER_FROM_PROVIDER"
                            : activeVersion,
                    textHash
            );

            EmbeddingResponse response =
                    embeddingClient.embed(embeddingText);

            validateEmbeddingResponse(response, textHash);
            activeVersion = response.embeddingVersion();

            if (processingRecord == null) {
                ProcessingPreparation preparation =
                        prepareProcessing(
                                normalizedJob,
                                activeVersion,
                                textHash,
                                force
                        );

                if (!preparation.shouldProcess()) {
                    return preparation.embedding();
                }

                processingRecord = preparation.embedding();
            } else if (!Objects.equals(
                    processingRecord.getEmbeddingVersion(),
                    activeVersion
            )) {
                throw new IllegalStateException(
                        "Embedding version changed during processing"
                );
            }

            applyResponseMetadata(
                    processingRecord,
                    response
            );

            String pointId = pointIdFactory.create(
                    normalizedJobId,
                    activeVersion
            );

            jobVectorStore.ensureCollection();

            jobVectorStore.upsert(
                    new JobVectorPoint(
                            pointId,
                            normalizedJobId,
                            normalizedJob.getSourceCode(),
                            normalizedJob.getNormalizationVersion(),
                            activeVersion,
                            textHash,
                            response.vector()
                    )
            );

            Instant now = Instant.now(normalizationClock);

            processingRecord.setQdrantCollection(
                    qdrantProperties.getCollection()
            );
            processingRecord.setQdrantPointId(pointId);
            processingRecord.setStatus(JobEmbeddingStatus.READY);
            processingRecord.setLastError(null);
            processingRecord.setEmbeddedAt(now);
            processingRecord.setUpdatedAt(now);

            JobEmbedding ready =
                    jobEmbeddingRepository.save(processingRecord);

            log.info(
                    "Job embedding ready normalizedJobId={} "
                            + "modelName={} embeddingVersion={} "
                            + "textHash={} qdrantPointId={} status=READY",
                    normalizedJobId,
                    ready.getModelName(),
                    ready.getEmbeddingVersion(),
                    ready.getTextHash(),
                    ready.getQdrantPointId()
            );

            return ready;
        } catch (NormalizedJobNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            markFailed(
                    normalizedJob,
                    processingRecord,
                    activeVersion,
                    textHash,
                    exception
            );

            String safeError = safeError(exception);

            log.error(
                    "Job embedding failed normalizedJobId={} "
                            + "embeddingVersion={} textHash={} "
                            + "errorType={} message={} status=FAILED",
                    normalizedJobId,
                    activeVersion,
                    textHash,
                    exception.getClass().getSimpleName(),
                    safeError
            );

            if (exception
                    instanceof JobEmbeddingProcessingException
                    processingException) {
                throw processingException;
            }

            throw new JobEmbeddingProcessingException(
                    normalizedJobId,
                    safeError,
                    exception
            );
        }
    }

    public JobEmbedding getLatest(String normalizedJobId) {
        validateNormalizedJobId(normalizedJobId);

        return jobEmbeddingRepository
                .findFirstByNormalizedJobIdOrderByUpdatedAtDesc(
                        normalizedJobId
                )
                .orElseThrow(
                        () -> new JobEmbeddingNotFoundException(
                                normalizedJobId
                        )
                );
    }

    private ProcessingPreparation prepareProcessing(
            NormalizedJob normalizedJob,
            String embeddingVersion,
            String textHash,
            boolean force
    ) {
        String normalizedJobId = normalizedJob.getId();

        return jobEmbeddingRepository
                .findByNormalizedJobIdAndEmbeddingVersion(
                        normalizedJobId,
                        embeddingVersion
                )
                .map(existing -> prepareExisting(
                        existing,
                        normalizedJob,
                        textHash,
                        force
                ))
                .orElseGet(() -> insertProcessing(
                        normalizedJob,
                        embeddingVersion,
                        textHash,
                        force
                ));
    }

    private ProcessingPreparation prepareExisting(
            JobEmbedding existing,
            NormalizedJob normalizedJob,
            String textHash,
            boolean force
    ) {
        String pointId = pointIdFactory.create(
                normalizedJob.getId(),
                existing.getEmbeddingVersion()
        );

        if (!force
                && isReadyAndUnchanged(
                existing,
                textHash,
                pointId
        )) {
            log.info(
                    "Job embedding skipped normalizedJobId={} "
                            + "embeddingVersion={} textHash={} "
                            + "qdrantPointId={} reason=UNCHANGED",
                    normalizedJob.getId(),
                    existing.getEmbeddingVersion(),
                    textHash,
                    pointId
            );

            return new ProcessingPreparation(
                    existing,
                    false
            );
        }

        if (!force
                && isFreshProcessing(existing, textHash)) {
            log.info(
                    "Job embedding skipped normalizedJobId={} "
                            + "embeddingVersion={} textHash={} "
                            + "reason=ALREADY_PROCESSING",
                    normalizedJob.getId(),
                    existing.getEmbeddingVersion(),
                    textHash
            );

            return new ProcessingPreparation(
                    existing,
                    false
            );
        }

        applyProcessingState(
                existing,
                normalizedJob,
                existing.getEmbeddingVersion(),
                textHash
        );

        return new ProcessingPreparation(
                jobEmbeddingRepository.save(existing),
                true
        );
    }

    private ProcessingPreparation insertProcessing(
            NormalizedJob normalizedJob,
            String embeddingVersion,
            String textHash,
            boolean force
    ) {
        Instant now = Instant.now(normalizationClock);
        String pointId = pointIdFactory.create(
                normalizedJob.getId(),
                embeddingVersion
        );

        JobEmbedding newRecord = JobEmbedding.builder()
                .normalizedJobId(normalizedJob.getId())
                .normalizationVersion(
                        normalizedJob.getNormalizationVersion()
                )
                .embeddingVersion(embeddingVersion)
                .textHash(textHash)
                .dimension(
                        embeddingProperties.getExpectedDimension()
                )
                .qdrantCollection(
                        qdrantProperties.getCollection()
                )
                .qdrantPointId(pointId)
                .status(JobEmbeddingStatus.PROCESSING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            return new ProcessingPreparation(
                    jobEmbeddingRepository.insert(newRecord),
                    true
            );
        } catch (DuplicateKeyException exception) {
            JobEmbedding concurrent =
                    jobEmbeddingRepository
                            .findByNormalizedJobIdAndEmbeddingVersion(
                                    normalizedJob.getId(),
                                    embeddingVersion
                            )
                            .orElseThrow(() -> exception);

            return prepareExisting(
                    concurrent,
                    normalizedJob,
                    textHash,
                    force
            );
        }
    }

    private void applyProcessingState(
            JobEmbedding embedding,
            NormalizedJob normalizedJob,
            String embeddingVersion,
            String textHash
    ) {
        Instant now = Instant.now(normalizationClock);

        embedding.setNormalizedJobId(normalizedJob.getId());
        embedding.setNormalizationVersion(
                normalizedJob.getNormalizationVersion()
        );
        embedding.setEmbeddingVersion(embeddingVersion);
        embedding.setTextHash(textHash);
        embedding.setDimension(
                embeddingProperties.getExpectedDimension()
        );
        embedding.setNormalized(null);
        embedding.setModelName(null);
        embedding.setModelRevision(null);
        embedding.setQdrantCollection(
                qdrantProperties.getCollection()
        );
        embedding.setQdrantPointId(
                pointIdFactory.create(
                        normalizedJob.getId(),
                        embeddingVersion
                )
        );
        embedding.setStatus(JobEmbeddingStatus.PROCESSING);
        embedding.setLastError(null);
        embedding.setEmbeddedAt(null);

        if (embedding.getCreatedAt() == null) {
            embedding.setCreatedAt(now);
        }

        embedding.setUpdatedAt(now);
    }

    private boolean isReadyAndUnchanged(
            JobEmbedding embedding,
            String textHash,
            String expectedPointId
    ) {
        if (embedding.getStatus() != JobEmbeddingStatus.READY
                || !Objects.equals(
                embedding.getTextHash(),
                textHash
        )
                || !Objects.equals(
                embedding.getQdrantPointId(),
                expectedPointId
        )) {
            return false;
        }

        return jobVectorStore.pointExists(expectedPointId);
    }

    private boolean isFreshProcessing(
            JobEmbedding embedding,
            String textHash
    ) {
        if (embedding.getStatus()
                != JobEmbeddingStatus.PROCESSING
                || !Objects.equals(
                embedding.getTextHash(),
                textHash
        )
                || embedding.getUpdatedAt() == null) {
            return false;
        }

        Duration lease = embeddingProperties
                .getResponseTimeout()
                .multipliedBy(2);

        if (lease.compareTo(Duration.ofSeconds(60)) < 0) {
            lease = Duration.ofSeconds(60);
        }

        Instant staleBefore = Instant.now(normalizationClock)
                .minus(lease);

        return embedding.getUpdatedAt().isAfter(staleBefore);
    }

    private void validateEmbeddingResponse(
            EmbeddingResponse response,
            String expectedTextHash
    ) {
        if (response == null) {
            throw new IllegalStateException(
                    "Embedding response must not be null"
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
                != embeddingProperties.getExpectedDimension()) {
            throw new IllegalStateException(
                    "Embedding response dimension mismatch"
            );
        }

        if (response.dimension()
                != qdrantProperties.getDimension()) {
            throw new IllegalStateException(
                    "Embedding and Qdrant dimensions do not match"
            );
        }

        if (!Boolean.TRUE.equals(response.normalized())) {
            throw new IllegalStateException(
                    "Embedding response is not normalized"
            );
        }

        if (isBlank(response.modelName())) {
            throw new IllegalStateException(
                    "Embedding response modelName is blank"
            );
        }

        if (isBlank(response.modelRevision())) {
            throw new IllegalStateException(
                    "Embedding response modelRevision is blank"
            );
        }

        if (isBlank(response.embeddingVersion())) {
            throw new IllegalStateException(
                    "Embedding response embeddingVersion is blank"
            );
        }

        List<Double> vector = response.vector();

        if (vector == null
                || vector.size() != response.dimension()) {
            throw new IllegalStateException(
                    "Embedding response vector length mismatch"
            );
        }

        for (Double value : vector) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalStateException(
                        "Embedding response contains a non-finite value"
                );
            }
        }
    }

    private void applyResponseMetadata(
            JobEmbedding embedding,
            EmbeddingResponse response
    ) {
        embedding.setModelName(response.modelName());
        embedding.setModelRevision(response.modelRevision());
        embedding.setEmbeddingVersion(
                response.embeddingVersion()
        );
        embedding.setDimension(response.dimension());
        embedding.setNormalized(response.normalized());
        embedding.setTextHash(response.textHash());
    }

    private void markFailed(
            NormalizedJob normalizedJob,
            JobEmbedding processingRecord,
            String embeddingVersion,
            String textHash,
            RuntimeException failure
    ) {
        if (isBlank(embeddingVersion)) {
            return;
        }

        try {
            JobEmbedding failed = processingRecord;

            if (failed == null) {
                failed = jobEmbeddingRepository
                        .findByNormalizedJobIdAndEmbeddingVersion(
                                normalizedJob.getId(),
                                embeddingVersion
                        )
                        .orElseGet(() -> JobEmbedding.builder()
                                .normalizedJobId(
                                        normalizedJob.getId()
                                )
                                .normalizationVersion(
                                        normalizedJob
                                                .getNormalizationVersion()
                                )
                                .embeddingVersion(
                                        embeddingVersion
                                )
                                .createdAt(
                                        Instant.now(
                                                normalizationClock
                                        )
                                )
                                .build());
            }

            Instant now = Instant.now(normalizationClock);

            failed.setNormalizedJobId(normalizedJob.getId());
            failed.setNormalizationVersion(
                    normalizedJob.getNormalizationVersion()
            );
            failed.setEmbeddingVersion(embeddingVersion);
            failed.setTextHash(textHash);
            failed.setDimension(
                    embeddingProperties.getExpectedDimension()
            );
            failed.setQdrantCollection(
                    qdrantProperties.getCollection()
            );
            failed.setQdrantPointId(
                    pointIdFactory.create(
                            normalizedJob.getId(),
                            embeddingVersion
                    )
            );
            failed.setStatus(JobEmbeddingStatus.FAILED);
            failed.setLastError(safeError(failure));
            failed.setUpdatedAt(now);

            if (failed.getCreatedAt() == null) {
                failed.setCreatedAt(now);
            }

            try {
                jobEmbeddingRepository.save(failed);
            } catch (DuplicateKeyException duplicateKeyException) {
                JobEmbedding concurrent =
                        jobEmbeddingRepository
                                .findByNormalizedJobIdAndEmbeddingVersion(
                                        normalizedJob.getId(),
                                        embeddingVersion
                                )
                                .orElseThrow(
                                        () -> duplicateKeyException
                                );

                concurrent.setStatus(
                        JobEmbeddingStatus.FAILED
                );
                concurrent.setLastError(safeError(failure));
                concurrent.setTextHash(textHash);
                concurrent.setUpdatedAt(now);

                jobEmbeddingRepository.save(concurrent);
            }
        } catch (RuntimeException persistenceFailure) {
            log.error(
                    "Unable to persist FAILED job embedding "
                            + "normalizedJobId={} embeddingVersion={} "
                            + "errorType={} message={}",
                    normalizedJob.getId(),
                    embeddingVersion,
                    persistenceFailure
                            .getClass()
                            .getSimpleName(),
                    safeError(persistenceFailure)
            );
        }
    }

    private String configuredExpectedVersion() {
        if (!embeddingProperties.hasExpectedVersion()) {
            return null;
        }

        return embeddingProperties
                .getExpectedVersion()
                .trim();
    }

    private String safeError(Throwable throwable) {
        String message = throwable.getMessage();

        if (message == null || message.isBlank()) {
            message = throwable
                    .getClass()
                    .getSimpleName();
        }

        String normalized = message
                .replaceAll("\\s+", " ")
                .replaceAll(
                        "(?i)bearer\\s+[^\\s,;]+",
                        "Bearer [REDACTED]"
                )
                .replaceAll(
                        "(?i)(token|password|secret)=([^\\s&]+)",
                        "$1=[REDACTED]"
                )
                .trim();

        String result = throwable
                .getClass()
                .getSimpleName()
                + ": "
                + normalized;

        int maxLength = embeddingProperties
                .getMaxErrorLength();

        if (result.length() > maxLength) {
            return result.substring(0, maxLength);
        }

        return result;
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

    private void validateEmbeddingText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Normalized job embeddingText must not be blank"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ProcessingPreparation(
            JobEmbedding embedding,
            boolean shouldProcess
    ) {
    }
}