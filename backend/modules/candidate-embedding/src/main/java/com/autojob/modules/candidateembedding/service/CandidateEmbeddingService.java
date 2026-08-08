package com.autojob.modules.candidateembedding.service;

import com.autojob.common.embedding.client.EmbeddingClient;
import com.autojob.common.embedding.client.dto.EmbeddingResponse;
import com.autojob.common.embedding.config.EmbeddingProperties;
import com.autojob.common.embedding.service.EmbeddingTextHashCalculator;
import com.autojob.modules.candidateembedding.config.CandidateEmbeddingProperties;
import com.autojob.modules.candidateembedding.domain.CandidateEmbedding;
import com.autojob.modules.candidateembedding.domain.CandidateEmbeddingStatus;
import com.autojob.modules.candidateembedding.repository.CandidateEmbeddingRepository;
import com.autojob.modules.candidateembedding.text.CandidateEmbeddingTextBuilder;
import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.repository.CandidateProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class CandidateEmbeddingService {

    private static final double NORMALIZATION_TOLERANCE = 1.0e-3;

    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateEmbeddingRepository candidateEmbeddingRepository;
    private final CandidateEmbeddingTextBuilder textBuilder;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingTextHashCalculator textHashCalculator;
    private final EmbeddingProperties embeddingProperties;
    private final CandidateEmbeddingProperties candidateProperties;
    private final Clock clock;

    @Autowired
    public CandidateEmbeddingService(
            CandidateProfileRepository candidateProfileRepository,
            CandidateEmbeddingRepository candidateEmbeddingRepository,
            CandidateEmbeddingTextBuilder textBuilder,
            EmbeddingClient embeddingClient,
            EmbeddingTextHashCalculator textHashCalculator,
            EmbeddingProperties embeddingProperties,
            CandidateEmbeddingProperties candidateProperties
    ) {
        this(
                candidateProfileRepository,
                candidateEmbeddingRepository,
                textBuilder,
                embeddingClient,
                textHashCalculator,
                embeddingProperties,
                candidateProperties,
                Clock.systemUTC()
        );
    }

    CandidateEmbeddingService(
            CandidateProfileRepository candidateProfileRepository,
            CandidateEmbeddingRepository candidateEmbeddingRepository,
            CandidateEmbeddingTextBuilder textBuilder,
            EmbeddingClient embeddingClient,
            EmbeddingTextHashCalculator textHashCalculator,
            EmbeddingProperties embeddingProperties,
            CandidateEmbeddingProperties candidateProperties,
            Clock clock
    ) {
        this.candidateProfileRepository = Objects.requireNonNull(
                candidateProfileRepository,
                "candidateProfileRepository"
        );
        this.candidateEmbeddingRepository = Objects.requireNonNull(
                candidateEmbeddingRepository,
                "candidateEmbeddingRepository"
        );
        this.textBuilder = Objects.requireNonNull(
                textBuilder,
                "textBuilder"
        );
        this.embeddingClient = Objects.requireNonNull(
                embeddingClient,
                "embeddingClient"
        );
        this.textHashCalculator = Objects.requireNonNull(
                textHashCalculator,
                "textHashCalculator"
        );
        this.embeddingProperties = Objects.requireNonNull(
                embeddingProperties,
                "embeddingProperties"
        );
        this.candidateProperties = Objects.requireNonNull(
                candidateProperties,
                "candidateProperties"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock"
        );
    }

    public CandidateEmbedding embed(String candidateProfileId) {
        return embed(candidateProfileId, false);
    }

    public CandidateEmbedding embed(
            String candidateProfileId,
            boolean force
    ) {
        validateCandidateProfileId(candidateProfileId);

        CandidateProfile profile = candidateProfileRepository
                .findById(candidateProfileId)
                .orElseThrow(() ->
                        CandidateEmbeddingNotFoundException
                                .candidateProfile(candidateProfileId)
                );

        String embeddingText = textBuilder.build(profile);
        String textHash = null;
        String activeVersion = configuredExpectedVersion();
        String textVersion = configuredTextVersion();

        CandidateEmbedding processingRecord = null;

        try {
            validateEmbeddingText(embeddingText);

            textHash = textHashCalculator.calculate(
                    embeddingText
            );

            /*
             * Normally expectedVersion is configured.
             *
             * In that case we can resolve the logical Mongo record
             * before calling the embedding service and therefore
             * protect concurrent requests with PROCESSING.
             *
             * If expectedVersion is intentionally absent, the actual
             * version is learned from the embedding response and the
             * record is resolved afterwards.
             */
            if (activeVersion != null) {
                ProcessingPreparation preparation =
                        prepareProcessing(
                                profile,
                                activeVersion,
                                textVersion,
                                textHash,
                                force
                        );

                if (!preparation.shouldProcess()) {
                    return preparation.embedding();
                }

                processingRecord =
                        preparation.embedding();
            }

            EmbeddingResponse response =
                    embeddingClient.embed(
                            embeddingText
                    );

            validateEmbeddingResponse(
                    response,
                    textHash
            );

            activeVersion =
                    response.embeddingVersion();

            if (processingRecord == null) {
                ProcessingPreparation preparation =
                        prepareProcessing(
                                profile,
                                activeVersion,
                                textVersion,
                                textHash,
                                force
                        );

                if (!preparation.shouldProcess()) {
                    return preparation.embedding();
                }

                processingRecord =
                        preparation.embedding();
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

            Instant now =
                    Instant.now(clock);

            processingRecord.setStatus(
                    CandidateEmbeddingStatus.READY
            );

            processingRecord.setLastError(null);
            processingRecord.setEmbeddedAt(now);
            processingRecord.setUpdatedAt(now);

            return candidateEmbeddingRepository.save(
                    processingRecord
            );
        } catch (RuntimeException exception) {
            markFailed(
                    profile,
                    processingRecord,
                    activeVersion,
                    textVersion,
                    textHash,
                    exception
            );

            String safeError =
                    safeError(exception);

            log.error(
                    "Candidate embedding failed "
                            + "candidateProfileId={} "
                            + "embeddingVersion={} "
                            + "textVersion={} "
                            + "textHash={} "
                            + "errorType={} "
                            + "message={} "
                            + "status=FAILED",
                    candidateProfileId,
                    activeVersion,
                    textVersion,
                    textHash,
                    exception.getClass()
                            .getSimpleName(),
                    safeError
            );

            if (exception
                    instanceof CandidateEmbeddingProcessingException
                    processingException) {
                throw processingException;
            }

            throw new CandidateEmbeddingProcessingException(
                    candidateProfileId,
                    safeError,
                    exception
            );
        }
    }

    public CandidateEmbedding getLatest(
            String candidateProfileId
    ) {
        validateCandidateProfileId(
                candidateProfileId
        );

        return candidateEmbeddingRepository
                .findFirstByCandidateProfileIdOrderByUpdatedAtDesc(
                        candidateProfileId
                )
                .orElseThrow(() ->
                        CandidateEmbeddingNotFoundException
                                .embedding(
                                        candidateProfileId
                                )
                );
    }

    private ProcessingPreparation prepareProcessing(
            CandidateProfile profile,
            String embeddingVersion,
            String textVersion,
            String textHash,
            boolean force
    ) {
        return candidateEmbeddingRepository
                .findByCandidateProfileIdAndEmbeddingVersionAndTextVersion(
                        profile.getId(),
                        embeddingVersion,
                        textVersion
                )
                .map(existing ->
                        prepareExisting(
                                existing,
                                profile,
                                textHash,
                                force
                        )
                )
                .orElseGet(() ->
                        insertProcessing(
                                profile,
                                embeddingVersion,
                                textVersion,
                                textHash,
                                force
                        )
                );
    }

    private ProcessingPreparation prepareExisting(
            CandidateEmbedding existing,
            CandidateProfile profile,
            String textHash,
            boolean force
    ) {
        if (!force
                && isReadyAndUnchanged(
                existing,
                textHash
        )) {

            return new ProcessingPreparation(
                    existing,
                    false
            );
        }

        if (!force
                && isFreshProcessing(
                existing,
                textHash
        )) {

            return new ProcessingPreparation(
                    existing,
                    false
            );
        }

        applyProcessingState(
                existing,
                profile,
                existing.getEmbeddingVersion(),
                existing.getTextVersion(),
                textHash
        );

        return new ProcessingPreparation(
                candidateEmbeddingRepository.save(
                        existing
                ),
                true
        );
    }

    private ProcessingPreparation insertProcessing(
            CandidateProfile profile,
            String embeddingVersion,
            String textVersion,
            String textHash,
            boolean force
    ) {
        Instant now =
                Instant.now(clock);

        CandidateEmbedding record =
                CandidateEmbedding.builder()
                        .candidateProfileId(
                                profile.getId()
                        )
                        .rawCvId(
                                profile.getRawCvId()
                        )
                        .parserVersion(
                                profile.getParserVersion()
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
                        .status(
                                CandidateEmbeddingStatus.PROCESSING
                        )
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        try {
            return new ProcessingPreparation(
                    candidateEmbeddingRepository
                            .insert(record),
                    true
            );
        } catch (DuplicateKeyException exception) {
            CandidateEmbedding concurrent =
                    candidateEmbeddingRepository
                            .findByCandidateProfileIdAndEmbeddingVersionAndTextVersion(
                                    profile.getId(),
                                    embeddingVersion,
                                    textVersion
                            )
                            .orElseThrow(
                                    () -> exception
                            );

            return prepareExisting(
                    concurrent,
                    profile,
                    textHash,
                    force
            );
        }
    }

    private void applyProcessingState(
            CandidateEmbedding embedding,
            CandidateProfile profile,
            String embeddingVersion,
            String textVersion,
            String textHash
    ) {
        Instant now =
                Instant.now(clock);

        embedding.setCandidateProfileId(
                profile.getId()
        );

        embedding.setRawCvId(
                profile.getRawCvId()
        );

        embedding.setParserVersion(
                profile.getParserVersion()
        );

        embedding.setTextVersion(
                textVersion
        );

        embedding.setEmbeddingVersion(
                embeddingVersion
        );

        embedding.setTextHash(
                textHash
        );

        embedding.setDimension(
                embeddingProperties
                        .getExpectedDimension()
        );

        embedding.setNormalized(null);
        embedding.setModelName(null);
        embedding.setModelRevision(null);
        embedding.setVector(null);

        embedding.setStatus(
                CandidateEmbeddingStatus.PROCESSING
        );

        embedding.setLastError(null);
        embedding.setEmbeddedAt(null);

        if (embedding.getCreatedAt() == null) {
            embedding.setCreatedAt(now);
        }

        embedding.setUpdatedAt(now);
    }

    private boolean isReadyAndUnchanged(
            CandidateEmbedding embedding,
            String textHash
    ) {
        return embedding.getStatus()
                == CandidateEmbeddingStatus.READY

                && Objects.equals(
                embedding.getTextHash(),
                textHash
        )

                && isStoredVectorValid(
                embedding
        );
    }

    private boolean isStoredVectorValid(
            CandidateEmbedding embedding
    ) {
        if (embedding.getDimension() == null
                || embedding.getDimension()
                != embeddingProperties.getExpectedDimension()
                || !Boolean.TRUE.equals(
                embedding.getNormalized()
        )
                || isBlank(
                embedding.getModelName()
        )
                || isBlank(
                embedding.getModelRevision()
        )
                || isBlank(
                embedding.getEmbeddingVersion()
        )) {

            return false;
        }

        return isValidNormalizedVector(
                embedding.getVector(),
                embedding.getDimension()
        );
    }

    private boolean isFreshProcessing(
            CandidateEmbedding embedding,
            String textHash
    ) {
        if (embedding.getStatus()
                != CandidateEmbeddingStatus.PROCESSING

                || !Objects.equals(
                embedding.getTextHash(),
                textHash
        )

                || embedding.getUpdatedAt() == null) {

            return false;
        }

        Duration lease =
                embeddingProperties
                        .getResponseTimeout()
                        .multipliedBy(2);

        if (lease.compareTo(
                Duration.ofSeconds(60)
        ) < 0) {
            lease =
                    Duration.ofSeconds(60);
        }

        Instant staleBefore =
                Instant.now(clock)
                        .minus(lease);

        return embedding
                .getUpdatedAt()
                .isAfter(staleBefore);
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
                != embeddingProperties
                .getExpectedDimension()) {

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
        )
                || isBlank(
                response.embeddingVersion()
        )) {

            throw new IllegalStateException(
                    "Embedding response model metadata is incomplete"
            );
        }

        String configuredVersion =
                configuredExpectedVersion();

        if (configuredVersion != null
                && !configuredVersion.equals(
                response.embeddingVersion()
        )) {

            throw new IllegalStateException(
                    "Embedding response embeddingVersion mismatch"
            );
        }

        if (!isValidNormalizedVector(
                response.vector(),
                response.dimension()
        )) {
            throw new IllegalStateException(
                    "Embedding response vector is invalid"
            );
        }
    }

    private boolean isValidNormalizedVector(
            List<Double> vector,
            int dimension
    ) {
        if (vector == null
                || vector.size() != dimension) {
            return false;
        }

        double sumSquares = 0.0;

        for (Double value : vector) {
            if (value == null
                    || !Double.isFinite(value)) {
                return false;
            }

            sumSquares += value * value;
        }

        double norm =
                Math.sqrt(sumSquares);

        return Double.isFinite(norm)
                && norm > 0.0
                && Math.abs(
                norm - 1.0
        ) <= NORMALIZATION_TOLERANCE;
    }

    private void applyResponseMetadata(
            CandidateEmbedding embedding,
            EmbeddingResponse response
    ) {
        embedding.setModelName(
                response.modelName()
        );

        embedding.setModelRevision(
                response.modelRevision()
        );

        embedding.setEmbeddingVersion(
                response.embeddingVersion()
        );

        embedding.setTextHash(
                response.textHash()
        );

        embedding.setDimension(
                response.dimension()
        );

        embedding.setNormalized(
                response.normalized()
        );

        embedding.setVector(
                List.copyOf(
                        response.vector()
                )
        );
    }

    private void markFailed(
            CandidateProfile profile,
            CandidateEmbedding processingRecord,
            String embeddingVersion,
            String textVersion,
            String textHash,
            RuntimeException failure
    ) {
        /*
         * Without a concrete embedding version there is no stable
         * unique Mongo key to persist against.
         */
        if (isBlank(embeddingVersion)) {
            return;
        }

        try {
            CandidateEmbedding failed =
                    processingRecord;

            if (failed == null) {
                failed =
                        candidateEmbeddingRepository
                                .findByCandidateProfileIdAndEmbeddingVersionAndTextVersion(
                                        profile.getId(),
                                        embeddingVersion,
                                        textVersion
                                )
                                .orElseGet(() ->
                                        CandidateEmbedding
                                                .builder()
                                                .candidateProfileId(
                                                        profile.getId()
                                                )
                                                .rawCvId(
                                                        profile.getRawCvId()
                                                )
                                                .parserVersion(
                                                        profile.getParserVersion()
                                                )
                                                .textVersion(
                                                        textVersion
                                                )
                                                .embeddingVersion(
                                                        embeddingVersion
                                                )
                                                .createdAt(
                                                        Instant.now(clock)
                                                )
                                                .build()
                                );
            }

            Instant now =
                    Instant.now(clock);

            failed.setCandidateProfileId(
                    profile.getId()
            );

            failed.setRawCvId(
                    profile.getRawCvId()
            );

            failed.setParserVersion(
                    profile.getParserVersion()
            );

            failed.setTextVersion(
                    textVersion
            );

            failed.setEmbeddingVersion(
                    embeddingVersion
            );

            failed.setTextHash(
                    textHash
            );

            failed.setDimension(
                    embeddingProperties
                            .getExpectedDimension()
            );

            failed.setNormalized(null);
            failed.setVector(null);

            failed.setStatus(
                    CandidateEmbeddingStatus.FAILED
            );

            failed.setLastError(
                    safeError(failure)
            );

            failed.setEmbeddedAt(null);
            failed.setUpdatedAt(now);

            if (failed.getCreatedAt() == null) {
                failed.setCreatedAt(now);
            }

            try {
                candidateEmbeddingRepository.save(
                        failed
                );
            } catch (
                    DuplicateKeyException
                            duplicateKeyException
            ) {
                CandidateEmbedding concurrent =
                        candidateEmbeddingRepository
                                .findByCandidateProfileIdAndEmbeddingVersionAndTextVersion(
                                        profile.getId(),
                                        embeddingVersion,
                                        textVersion
                                )
                                .orElseThrow(
                                        () ->
                                                duplicateKeyException
                                );

                concurrent.setStatus(
                        CandidateEmbeddingStatus.FAILED
                );

                concurrent.setLastError(
                        safeError(failure)
                );

                concurrent.setTextHash(
                        textHash
                );

                concurrent.setNormalized(null);
                concurrent.setVector(null);
                concurrent.setEmbeddedAt(null);
                concurrent.setUpdatedAt(now);

                candidateEmbeddingRepository.save(
                        concurrent
                );
            }
        } catch (RuntimeException persistenceFailure) {
            log.error(
                    "Could not persist FAILED candidate embedding "
                            + "for candidateProfileId={}: {}",
                    profile.getId(),
                    safeError(persistenceFailure)
            );

            /*
             * Do not replace the original embedding failure with a
             * secondary persistence failure.
             */
        }
    }

    private String configuredExpectedVersion() {
        if (!embeddingProperties
                .hasExpectedVersion()) {
            return null;
        }

        return embeddingProperties
                .getExpectedVersion()
                .trim();
    }

    private String configuredTextVersion() {
        String value =
                candidateProperties
                        .getTextVersion();

        if (value == null
                || value.isBlank()) {
            throw new IllegalArgumentException(
                    "candidate embedding textVersion must not be blank"
            );
        }

        return value.trim();
    }

    private String safeError(
            Throwable throwable
    ) {
        String message =
                throwable.getMessage();

        if (message == null
                || message.isBlank()) {
            message =
                    throwable
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

        return result.length() > maxLength
                ? result.substring(
                0,
                maxLength
        )
                : result;
    }

    private void validateCandidateProfileId(
            String candidateProfileId
    ) {
        if (candidateProfileId == null
                || candidateProfileId.isBlank()) {
            throw new IllegalArgumentException(
                    "candidateProfileId must not be blank"
            );
        }
    }

    private void validateEmbeddingText(
            String text
    ) {
        if (text == null
                || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Candidate embedding text must not be blank"
            );
        }
    }

    private boolean isBlank(
            String value
    ) {
        return value == null
                || value.isBlank();
    }

    private record ProcessingPreparation(
            CandidateEmbedding embedding,
            boolean shouldProcess
    ) {
    }
}