package com.autojob.modules.matching.service;

import com.autojob.modules.candidateembedding.domain.CandidateEmbedding;
import com.autojob.modules.candidateembedding.domain.CandidateEmbeddingStatus;
import com.autojob.modules.candidateembedding.repository.CandidateEmbeddingRepository;
import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.repository.CandidateProfileRepository;
import com.autojob.modules.jobembedding.search.JobVectorSearchCriteria;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.config.MatchingProperties;
import com.autojob.modules.matching.contract.MatchingRunRequest;
import com.autojob.modules.matching.contract.MatchingRunResult;
import com.autojob.modules.matching.domain.MatchResult;
import com.autojob.modules.matching.repository.MatchResultRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class HybridMatchingService {

    private final CandidateProfileRepository
            candidateProfileRepository;

    private final CandidateEmbeddingRepository
            candidateEmbeddingRepository;

    private final MatchingEvaluationService
            matchingEvaluationService;

    private final MatchResultRepository
            matchResultRepository;

    private final MatchingProperties
            properties;

    private final Clock
            clock;

    public HybridMatchingService(
            CandidateProfileRepository candidateProfileRepository,
            CandidateEmbeddingRepository candidateEmbeddingRepository,
            MatchingEvaluationService matchingEvaluationService,
            MatchResultRepository matchResultRepository,
            MatchingProperties properties,
            Clock clock
    ) {
        this.candidateProfileRepository =
                Objects.requireNonNull(
                        candidateProfileRepository,
                        "candidateProfileRepository must not be null"
                );

        this.candidateEmbeddingRepository =
                Objects.requireNonNull(
                        candidateEmbeddingRepository,
                        "candidateEmbeddingRepository must not be null"
                );

        this.matchingEvaluationService =
                Objects.requireNonNull(
                        matchingEvaluationService,
                        "matchingEvaluationService must not be null"
                );

        this.matchResultRepository =
                Objects.requireNonNull(
                        matchResultRepository,
                        "matchResultRepository must not be null"
                );

        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );

        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock must not be null"
                );
    }

    public MatchingRunResult run(
            String candidateProfileId,
            String ownerUserId,
            boolean force
    ) {
        return run(
                new MatchingRunRequest(
                        candidateProfileId,
                        force
                ),
                ownerUserId
        );
    }

    public MatchingRunResult run(
            MatchingRunRequest request,
            String ownerUserId
    ) {
        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        CandidateProfile profile =
                loadOwnedCandidateProfile(
                        request.candidateProfileId(),
                        ownerUserId
                );

        CandidateEmbedding embedding =
                loadReadyCandidateEmbedding(
                        profile
                );

        validateEmbedding(
                profile,
                embedding
        );

        String rankingVersion =
                properties.getVersion();

        if (!request.force()) {

            List<MatchResult> existing =
                    findExistingResults(
                            profile.getId(),
                            embedding.getId(),
                            rankingVersion
                    );

            if (!existing.isEmpty()) {

                return new MatchingRunResult(
                        profile.getId(),
                        embedding.getId(),
                        rankingVersion,
                        0,
                        0,
                        existing.size(),
                        true,
                        existing
                );
            }
        }

        /*
         * IMPORTANT
         *
         * Normal matching và CV Tailoring Preview
         * đều sử dụng MatchingEvaluationService.
         *
         * Đây là ONE MATCHING LOGIC PATH:
         *
         * Qdrant
         * ↓
         * hydrate jobs
         * ↓
         * eligibility
         * ↓
         * semantic calibration
         * ↓
         * HybridRankingService
         * ↓
         * MatchAcceptanceFilter
         */
        MatchingEvaluationService
                .EvaluationResult evaluation =
                matchingEvaluationService
                        .evaluate(
                                profile,
                                embedding.getVector(),
                                embedding
                                        .getEmbeddingVersion()
                        );

        /*
         * Persistence chỉ thuộc normal matching.
         *
         * MatchingEvaluationService hoàn toàn
         * không persist.
         */
        matchResultRepository
                .deleteByCandidateProfileIdAndCandidateEmbeddingIdAndRankingVersion(
                        profile.getId(),
                        embedding.getId(),
                        rankingVersion
                );

        Instant generatedAt =
                Instant.now(
                        clock
                );

        List<MatchResult> documents =
                toMatchResults(
                        profile,
                        embedding,
                        evaluation.criteria(),
                        evaluation.rankedJobs(),
                        generatedAt
                );

        List<MatchResult> savedResults;

        if (documents.isEmpty()) {

            savedResults =
                    List.of();

        } else {

            savedResults =
                    matchResultRepository
                            .saveAll(
                                    documents
                            );

            savedResults =
                    savedResults
                            .stream()
                            .sorted(
                                    Comparator
                                            .comparingInt(
                                                    MatchResult::getRank
                                            )
                            )
                            .toList();
        }

        return new MatchingRunResult(
                profile.getId(),
                embedding.getId(),
                rankingVersion,
                evaluation.retrievedCount(),
                evaluation.hydratedCount(),
                savedResults.size(),
                false,
                savedResults
        );
    }

    public MatchingRunResult getCurrent(
            String candidateProfileId,
            String ownerUserId
    ) {
        CandidateProfile profile =
                loadOwnedCandidateProfile(
                        candidateProfileId,
                        ownerUserId
                );

        CandidateEmbedding embedding =
                loadReadyCandidateEmbedding(
                        profile
                );

        validateEmbedding(
                profile,
                embedding
        );

        String rankingVersion =
                properties.getVersion();

        List<MatchResult> existing =
                findExistingResults(
                        profile.getId(),
                        embedding.getId(),
                        rankingVersion
                );

        if (existing.isEmpty()) {

            throw MatchingPreconditionException
                    .matchResultNotFound(
                            profile.getId()
                    );
        }

        return new MatchingRunResult(
                profile.getId(),
                embedding.getId(),
                rankingVersion,
                0,
                0,
                existing.size(),
                true,
                existing
        );
    }

    private CandidateProfile
    loadOwnedCandidateProfile(
            String candidateProfileId,
            String ownerUserId
    ) {
        String normalizedOwnerUserId =
                requireOwnerUserId(
                        candidateProfileId,
                        ownerUserId
                );

        CandidateProfile profile =
                candidateProfileRepository
                        .findById(
                                candidateProfileId
                        )
                        .orElseThrow(
                                () ->
                                        MatchingPreconditionException
                                                .candidateProfileNotFound(
                                                        candidateProfileId
                                                )
                        );

        if (!Objects.equals(
                normalizeOwnerUserId(
                        profile.getOwnerUserId()
                ),
                normalizedOwnerUserId
        )) {

            throw MatchingPreconditionException
                    .candidateProfileNotFound(
                            candidateProfileId
                    );
        }

        return profile;
    }

    private String requireOwnerUserId(
            String candidateProfileId,
            String ownerUserId
    ) {
        String normalized =
                normalizeOwnerUserId(
                        ownerUserId
                );

        if (normalized == null) {

            throw MatchingPreconditionException
                    .authenticationRequired(
                            candidateProfileId
                    );
        }

        return normalized;
    }

    private String normalizeOwnerUserId(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return null;
        }

        return value.trim();
    }

    private CandidateEmbedding
    loadReadyCandidateEmbedding(
            CandidateProfile profile
    ) {
        String requiredTextVersion =
                properties
                        .getCompatibility()
                        .getCandidateTextVersion();

        return candidateEmbeddingRepository
                .findFirstByCandidateProfileIdAndStatusAndTextVersionOrderByUpdatedAtDesc(
                        profile.getId(),
                        CandidateEmbeddingStatus.READY,
                        requiredTextVersion
                )
                .orElseThrow(
                        () ->
                                MatchingPreconditionException
                                        .readyEmbeddingNotFound(
                                                profile.getId(),
                                                requiredTextVersion
                                        )
                );
    }

    private List<MatchResult> toMatchResults(
            CandidateProfile profile,
            CandidateEmbedding embedding,
            JobVectorSearchCriteria criteria,
            List<HybridRankingService.RankedJob> rankedJobs,
            Instant generatedAt
    ) {
        List<MatchResult> result =
                new ArrayList<>(
                        rankedJobs.size()
                );

        for (
                HybridRankingService.RankedJob ranked
                : rankedJobs
        ) {

            NormalizedJob job =
                    ranked.job();

            MatchResult document =
                    MatchResult
                            .builder()

                            .rawCvId(
                                    profile.getRawCvId()
                            )

                            .candidateProfileId(
                                    profile.getId()
                            )

                            .candidateEmbeddingId(
                                    embedding.getId()
                            )

                            .normalizedJobId(
                                    job.getId()
                            )

                            .qdrantPointId(
                                    ranked.pointId()
                            )

                            .sourceCode(
                                    job.getSourceCode()
                            )

                            .sourceJobId(
                                    job.getSourceJobId()
                            )

                            .jobTitle(
                                    job.getTitle()
                            )

                            .companyName(
                                    job.getCompanyName()
                            )

                            .locations(
                                    safeList(
                                            job.getLocations()
                                    )
                            )

                            .locationText(
                                    job.getLocationText()
                            )

                            .salaryText(
                                    job.getSalaryText()
                            )

                            .jobType(
                                    job.getJobType()
                                            == null
                                            ? null
                                            : job
                                            .getJobType()
                                            .name()
                            )

                            .applyType(
                                    job.getApplyType()
                                            == null
                                            ? null
                                            : job
                                            .getApplyType()
                                            .name()
                            )

                            .detailUrl(
                                    job.getDetailUrl()
                            )

                            .applyUrl(
                                    job.getApplyUrl()
                            )

                            .postedAt(
                                    job.getPostedAt()
                            )

                            .deadlineAt(
                                    job.getDeadlineAt()
                            )

                            .parserVersion(
                                    embedding
                                            .getParserVersion()
                            )

                            .normalizationVersion(
                                    criteria
                                            .normalizationVersion()
                            )

                            .embeddingVersion(
                                    criteria
                                            .embeddingVersion()
                            )

                            .candidateTextVersion(
                                    embedding
                                            .getTextVersion()
                            )

                            .jobTextVersion(
                                    criteria
                                            .textVersion()
                            )

                            .rankingVersion(
                                    properties
                                            .getVersion()
                            )

                            .rank(
                                    ranked.rank()
                            )

                            .finalScore(
                                    ranked
                                            .score()
                                            .finalScore()
                            )

                            .semanticScore(
                                    ranked
                                            .score()
                                            .semanticScore()
                            )

                            .skillScore(
                                    ranked
                                            .score()
                                            .skillScore()
                            )

                            .seniorityScore(
                                    ranked
                                            .score()
                                            .seniorityScore()
                            )

                            .locationScore(
                                    ranked
                                            .score()
                                            .locationScore()
                            )

                            .freshnessScore(
                                    ranked
                                            .score()
                                            .freshnessScore()
                            )

                            /*
                             * Model MatchResult đã có các field này,
                             * nhưng mapping cũ chưa set.
                             */
                            .skillKnown(
                                    ranked
                                            .score()
                                            .skillKnown()
                            )

                            .seniorityKnown(
                                    ranked
                                            .score()
                                            .seniorityKnown()
                            )

                            .locationKnown(
                                    ranked
                                            .score()
                                            .locationKnown()
                            )

                            .freshnessKnown(
                                    ranked
                                            .score()
                                            .freshnessKnown()
                            )

                            .matchedSkills(
                                    safeList(
                                            ranked
                                                    .matchedSkills()
                                    )
                            )

                            .missingSkills(
                                    safeList(
                                            ranked
                                                    .missingSkills()
                                    )
                            )

                            .generatedAt(
                                    generatedAt
                            )

                            .build();

            result.add(
                    document
            );
        }

        return List.copyOf(
                result
        );
    }

    private List<MatchResult> findExistingResults(
            String candidateProfileId,
            String candidateEmbeddingId,
            String rankingVersion
    ) {
        List<MatchResult> existing =
                matchResultRepository
                        .findByCandidateProfileIdAndCandidateEmbeddingIdAndRankingVersionOrderByRankAsc(
                                candidateProfileId,
                                candidateEmbeddingId,
                                rankingVersion
                        );

        if (existing == null
                || existing.isEmpty()) {

            return List.of();
        }

        return existing
                .stream()
                .sorted(
                        Comparator
                                .comparingInt(
                                        MatchResult::getRank
                                )
                )
                .toList();
    }

    private void validateEmbedding(
            CandidateProfile profile,
            CandidateEmbedding embedding
    ) {
        if (!Objects.equals(
                profile.getId(),
                embedding.getCandidateProfileId()
        )) {

            throw MatchingPreconditionException
                    .invalidEmbedding(
                            profile.getId(),
                            "Candidate embedding belongs to another candidate profile"
                    );
        }

        if (!Objects.equals(
                profile.getRawCvId(),
                embedding.getRawCvId()
        )) {

            throw MatchingPreconditionException
                    .invalidEmbedding(
                            profile.getId(),
                            "Candidate embedding rawCvId does not match candidate profile"
                    );
        }

        if (profile.getParserVersion()
                != null
                && embedding.getParserVersion()
                != null
                && !Objects.equals(
                profile.getParserVersion(),
                embedding.getParserVersion()
        )) {

            throw MatchingPreconditionException
                    .staleEmbedding(
                            profile.getId()
                    );
        }

        if (embedding.getEmbeddingVersion()
                == null
                || embedding
                .getEmbeddingVersion()
                .isBlank()) {

            throw MatchingPreconditionException
                    .invalidEmbedding(
                            profile.getId(),
                            "Candidate embeddingVersion must not be blank"
                    );
        }

        if (embedding.getTextVersion()
                == null
                || embedding
                .getTextVersion()
                .isBlank()) {

            throw MatchingPreconditionException
                    .invalidEmbedding(
                            profile.getId(),
                            "Candidate textVersion must not be blank"
                    );
        }

        if (embedding.getVector()
                == null
                || embedding
                .getVector()
                .isEmpty()) {

            throw MatchingPreconditionException
                    .invalidEmbedding(
                            profile.getId(),
                            "Candidate embedding vector must not be empty"
                    );
        }

        if (embedding.getDimension()
                != null
                && embedding.getDimension()
                != embedding
                .getVector()
                .size()) {

            throw MatchingPreconditionException
                    .invalidEmbedding(
                            profile.getId(),
                            "Candidate embedding dimension does not match vector size"
                    );
        }

        for (
                Double value
                : embedding.getVector()
        ) {

            if (value == null
                    || !Double.isFinite(
                    value
            )) {

                throw MatchingPreconditionException
                        .invalidEmbedding(
                                profile.getId(),
                                "Candidate embedding vector contains a non-finite value"
                        );
            }
        }

        if (embedding.getEmbeddedAt()
                == null) {

            throw MatchingPreconditionException
                    .invalidEmbedding(
                            profile.getId(),
                            "READY candidate embedding must have embeddedAt"
                    );
        }

        if (profile.getUpdatedAt()
                != null
                && profile
                .getUpdatedAt()
                .isAfter(
                        embedding
                                .getEmbeddedAt()
                )) {

            throw MatchingPreconditionException
                    .staleEmbedding(
                            profile.getId()
                    );
        }
    }

    private static List<String> safeList(
            List<String> values
    ) {
        if (values == null
                || values.isEmpty()) {

            return List.of();
        }

        return List.copyOf(
                values
        );
    }
}