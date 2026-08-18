package com.autojob.modules.matching.service;

import com.autojob.modules.candidateembedding.domain.CandidateEmbedding;
import com.autojob.modules.candidateembedding.domain.CandidateEmbeddingStatus;
import com.autojob.modules.candidateembedding.repository.CandidateEmbeddingRepository;
import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.repository.CandidateProfileRepository;
import com.autojob.modules.jobembedding.search.JobVectorHit;
import com.autojob.modules.jobembedding.search.JobVectorSearchCriteria;
import com.autojob.modules.jobembedding.search.JobVectorSearchPort;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class HybridMatchingService {

    private final CandidateProfileRepository
            candidateProfileRepository;

    private final CandidateEmbeddingRepository
            candidateEmbeddingRepository;

    private final JobVectorSearchPort
            jobVectorSearchPort;

    private final NormalizedJobRepository
            normalizedJobRepository;

    private final HybridRankingService
            hybridRankingService;

    private final MatchResultRepository
            matchResultRepository;

    private final MatchingProperties properties;

    private final Clock clock;

    public HybridMatchingService(
            CandidateProfileRepository candidateProfileRepository,
            CandidateEmbeddingRepository candidateEmbeddingRepository,
            JobVectorSearchPort jobVectorSearchPort,
            NormalizedJobRepository normalizedJobRepository,
            HybridRankingService hybridRankingService,
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

        this.jobVectorSearchPort =
                Objects.requireNonNull(
                        jobVectorSearchPort,
                        "jobVectorSearchPort must not be null"
                );

        this.normalizedJobRepository =
                Objects.requireNonNull(
                        normalizedJobRepository,
                        "normalizedJobRepository must not be null"
                );

        this.hybridRankingService =
                Objects.requireNonNull(
                        hybridRankingService,
                        "hybridRankingService must not be null"
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

        /*
         * 1. Candidate + ownership.
         */
        CandidateProfile profile =
                loadOwnedCandidateProfile(
                        request.candidateProfileId(),
                        ownerUserId
                );

        /*
         * 2. Current READY candidate embedding.
         */
        CandidateEmbedding embedding =
                loadReadyCandidateEmbedding(
                        profile
                );

        /*
         * 3. Validate embedding.
         */
        validateEmbedding(
                profile,
                embedding
        );

        String rankingVersion =
                properties.getVersion();

        /*
         * 4. Reuse exact matching run nếu được phép.
         */
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
         * 5. Qdrant compatibility criteria.
         */
        JobVectorSearchCriteria criteria =
                buildSearchCriteria(
                        embedding
                );

        /*
         * 6. Semantic retrieval.
         */
        List<JobVectorHit> hits =
                jobVectorSearchPort.search(
                        embedding.getVector(),
                        criteria
                );

        if (hits == null) {
            hits = List.of();
        }

        /*
         * 7. Hydrate Qdrant hits bằng NormalizedJob.
         */
        List<HybridRankingService.JobCandidate>
                jobCandidates =
                loadJobCandidates(
                        hits
                );

        /*
         * 8. Hybrid reranking.
         */
        List<HybridRankingService.RankedJob>
                rankedJobs =
                hybridRankingService.rank(
                        profile,
                        jobCandidates,
                        properties
                                .getRetrieval()
                                .getResultLimit()
                );

        /*
         * 9. Replace exact run.
         */
        matchResultRepository
                .deleteByCandidateProfileIdAndCandidateEmbeddingIdAndRankingVersion(
                        profile.getId(),
                        embedding.getId(),
                        rankingVersion
                );

        /*
         * 10. Persist result snapshot.
         */
        Instant generatedAt =
                Instant.now(clock);

        List<MatchResult> documents =
                toMatchResults(
                        profile,
                        embedding,
                        criteria,
                        rankedJobs,
                        generatedAt
                );

        List<MatchResult> savedResults;

        if (documents.isEmpty()) {

            savedResults = List.of();

        } else {

            savedResults =
                    matchResultRepository.saveAll(
                            documents
                    );

            savedResults =
                    savedResults
                            .stream()
                            .sorted(
                                    Comparator.comparingInt(
                                            MatchResult::getRank
                                    )
                            )
                            .toList();
        }

        return new MatchingRunResult(
                profile.getId(),
                embedding.getId(),
                rankingVersion,
                hits.size(),
                jobCandidates.size(),
                savedResults.size(),
                false,
                savedResults
        );
    }

    /**
     * Lấy result của current candidate embedding
     * + current ranking version.
     */
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

    private CandidateProfile loadOwnedCandidateProfile(
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

        /*
         * Không phân biệt:
         *
         * - candidate không tồn tại
         * - candidate thuộc user khác
         *
         * để tránh ID probing.
         */
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

    private CandidateEmbedding loadReadyCandidateEmbedding(
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

    private JobVectorSearchCriteria buildSearchCriteria(
            CandidateEmbedding embedding
    ) {
        return new JobVectorSearchCriteria(
                properties
                        .getRetrieval()
                        .getCandidatePoolSize(),

                properties
                        .getCompatibility()
                        .getNormalizationVersion(),

                /*
                 * Candidate embedding và job embedding
                 * phải cùng embeddingVersion.
                 */
                embedding.getEmbeddingVersion(),

                properties
                        .getCompatibility()
                        .getJobTextVersion()
        );
    }

    private List<HybridRankingService.JobCandidate>
    loadJobCandidates(
            List<JobVectorHit> hits
    ) {
        if (hits.isEmpty()) {
            return List.of();
        }

        Set<String> normalizedJobIds =
                new LinkedHashSet<>();

        for (JobVectorHit hit : hits) {

            if (hit == null) {
                continue;
            }

            String normalizedJobId =
                    hit.normalizedJobId();

            if (normalizedJobId == null
                    || normalizedJobId.isBlank()) {
                continue;
            }

            normalizedJobIds.add(
                    normalizedJobId
            );
        }

        if (normalizedJobIds.isEmpty()) {
            return List.of();
        }

        /*
         * Bulk Mongo lookup.
         */
        Map<String, NormalizedJob> jobsById =
                new LinkedHashMap<>();

        for (NormalizedJob job :
                normalizedJobRepository.findAllById(
                        normalizedJobIds
                )) {

            if (job == null
                    || job.getId() == null
                    || job.getId().isBlank()) {
                continue;
            }

            jobsById.put(
                    job.getId(),
                    job
            );
        }

        List<HybridRankingService.JobCandidate>
                result =
                new ArrayList<>();

        Set<String> addedJobIds =
                new LinkedHashSet<>();

        /*
         * Duyệt lại Qdrant order.
         */
        for (JobVectorHit hit : hits) {

            if (hit == null) {
                continue;
            }

            String normalizedJobId =
                    hit.normalizedJobId();

            if (normalizedJobId == null
                    || normalizedJobId.isBlank()) {
                continue;
            }

            if (!addedJobIds.add(
                    normalizedJobId
            )) {
                continue;
            }

            NormalizedJob job =
                    jobsById.get(
                            normalizedJobId
                    );

            /*
             * Orphan Qdrant point.
             */
            if (job == null) {
                continue;
            }

            result.add(
                    new HybridRankingService.JobCandidate(
                            hit,
                            job
                    )
            );
        }

        return List.copyOf(result);
    }

    /**
     * Convert RankedJob sang MatchResult.
     *
     * Ngoài score còn snapshot luôn display fields
     * của job để frontend không cần N+1 request.
     */
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

        for (HybridRankingService.RankedJob ranked
                : rankedJobs) {

            NormalizedJob job =
                    ranked.job();

            MatchResult document =
                    MatchResult.builder()

                            /*
                             * Candidate identity.
                             */
                            .rawCvId(
                                    profile.getRawCvId()
                            )

                            .candidateProfileId(
                                    profile.getId()
                            )

                            .candidateEmbeddingId(
                                    embedding.getId()
                            )

                            /*
                             * Job identity.
                             */
                            .normalizedJobId(
                                    job.getId()
                            )

                            .qdrantPointId(
                                    ranked.pointId()
                            )

                            /*
                             * ---------------------------------
                             * Job display snapshot.
                             * ---------------------------------
                             */
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
                                    job.getJobType() == null
                                            ? null
                                            : job
                                            .getJobType()
                                            .name()
                            )

                            .applyType(
                                    job.getApplyType() == null
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

                            /*
                             * ---------------------------------
                             * Version snapshot.
                             * ---------------------------------
                             */
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
                                    properties.getVersion()
                            )

                            /*
                             * Rank.
                             */
                            .rank(
                                    ranked.rank()
                            )

                            /*
                             * Score breakdown.
                             */
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
                             * Explainability.
                             */
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

        return List.copyOf(result);
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
                        Comparator.comparingInt(
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
                            "Candidate embedding belongs "
                                    + "to another candidate profile"
                    );
        }

        if (!Objects.equals(
                profile.getRawCvId(),
                embedding.getRawCvId()
        )) {
            throw MatchingPreconditionException
                    .invalidEmbedding(
                            profile.getId(),
                            "Candidate embedding rawCvId "
                                    + "does not match candidate profile"
                    );
        }

        if (profile.getParserVersion() != null
                && embedding.getParserVersion() != null
                && !Objects.equals(
                profile.getParserVersion(),
                embedding.getParserVersion()
        )) {

            throw MatchingPreconditionException
                    .staleEmbedding(
                            profile.getId()
                    );
        }

        if (embedding.getEmbeddingVersion() == null
                || embedding
                .getEmbeddingVersion()
                .isBlank()) {

            throw MatchingPreconditionException
                    .invalidEmbedding(
                            profile.getId(),
                            "Candidate embeddingVersion "
                                    + "must not be blank"
                    );
        }

        if (embedding.getTextVersion() == null
                || embedding
                .getTextVersion()
                .isBlank()) {

            throw MatchingPreconditionException
                    .invalidEmbedding(
                            profile.getId(),
                            "Candidate textVersion "
                                    + "must not be blank"
                    );
        }

        if (embedding.getVector() == null
                || embedding.getVector().isEmpty()) {

            throw MatchingPreconditionException
                    .invalidEmbedding(
                            profile.getId(),
                            "Candidate embedding vector "
                                    + "must not be empty"
                    );
        }

        if (embedding.getDimension() != null
                && embedding.getDimension()
                != embedding
                .getVector()
                .size()) {

            throw MatchingPreconditionException
                    .invalidEmbedding(
                            profile.getId(),
                            "Candidate embedding dimension "
                                    + "does not match vector size"
                    );
        }

        for (Double value :
                embedding.getVector()) {

            if (value == null
                    || !Double.isFinite(value)) {

                throw MatchingPreconditionException
                        .invalidEmbedding(
                                profile.getId(),
                                "Candidate embedding vector "
                                        + "contains a non-finite value"
                        );
            }
        }

        if (embedding.getEmbeddedAt() == null) {

            throw MatchingPreconditionException
                    .invalidEmbedding(
                            profile.getId(),
                            "READY candidate embedding "
                                    + "must have embeddedAt"
                    );
        }

        /*
         * Profile thay đổi sau embeddedAt
         * => query vector đã stale.
         */
        if (profile.getUpdatedAt() != null
                && profile
                .getUpdatedAt()
                .isAfter(
                        embedding.getEmbeddedAt()
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

        return List.copyOf(values);
    }
}