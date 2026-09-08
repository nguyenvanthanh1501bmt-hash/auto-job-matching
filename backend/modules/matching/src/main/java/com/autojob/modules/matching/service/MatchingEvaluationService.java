package com.autojob.modules.matching.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.jobembedding.search.JobVectorHit;
import com.autojob.modules.jobembedding.search.JobVectorSearchCriteria;
import com.autojob.modules.jobembedding.search.JobVectorSearchPort;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import com.autojob.modules.matching.config.MatchingProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Shared matching evaluation path.
 *
 * Normal matching và CV Tailoring Preview đều phải đi qua service này
 * để giữ cùng:
 *
 * Qdrant retrieval
 * job hydration
 * eligibility
 * semantic calibration
 * hybrid ranking
 * acceptance filtering
 *
 * Service này KHÔNG persist MatchResult.
 */
@Service
public class MatchingEvaluationService {

    private final JobVectorSearchPort jobVectorSearchPort;

    private final NormalizedJobRepository normalizedJobRepository;

    private final HybridRankingService hybridRankingService;

    private final MatchingProperties properties;

    public MatchingEvaluationService(
            JobVectorSearchPort jobVectorSearchPort,
            NormalizedJobRepository normalizedJobRepository,
            HybridRankingService hybridRankingService,
            MatchingProperties properties
    ) {
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

        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );
    }

    public EvaluationResult evaluate(
            CandidateProfile profile,
            List<Double> vector,
            String embeddingVersion
    ) {
        Objects.requireNonNull(
                profile,
                "profile must not be null"
        );

        validateVector(
                vector
        );

        requireText(
                embeddingVersion,
                "embeddingVersion"
        );

        JobVectorSearchCriteria criteria =
                buildSearchCriteria(
                        embeddingVersion
                );

        List<JobVectorHit> hits =
                jobVectorSearchPort.search(
                        vector,
                        criteria
                );

        if (hits == null) {

            hits = List.of();

        } else {

            hits =
                    List.copyOf(
                            hits
                    );
        }

        List<HybridRankingService.JobCandidate> jobCandidates =
                loadJobCandidates(
                        hits
                );

        List<HybridRankingService.RankedJob> rankedJobs =
                hybridRankingService.rank(
                        profile,
                        jobCandidates,
                        properties
                                .getRetrieval()
                                .getResultLimit()
                );

        Set<String> retrievedJobIds =
                collectRetrievedJobIds(
                        hits
                );

        Set<String> hydratedJobIds =
                jobCandidates
                        .stream()
                        .map(
                                candidate ->
                                        candidate
                                                .job()
                                                .getId()
                        )
                        .filter(
                                Objects::nonNull
                        )
                        .collect(
                                LinkedHashSet::new,
                                Set::add,
                                Set::addAll
                        );

        return new EvaluationResult(
                criteria,
                hits.size(),
                jobCandidates.size(),
                rankedJobs,
                retrievedJobIds,
                hydratedJobIds
        );
    }

    private JobVectorSearchCriteria buildSearchCriteria(
            String embeddingVersion
    ) {
        return new JobVectorSearchCriteria(
                properties
                        .getRetrieval()
                        .getCandidatePoolSize(),

                properties
                        .getCompatibility()
                        .getNormalizationVersion(),

                embeddingVersion,

                properties
                        .getCompatibility()
                        .getJobTextVersion()
        );
    }

    private List<HybridRankingService.JobCandidate> loadJobCandidates(
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

        Map<String, NormalizedJob> jobsById =
                new LinkedHashMap<>();

        for (
                NormalizedJob job
                : normalizedJobRepository.findAllById(
                normalizedJobIds
        )
        ) {

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

        List<HybridRankingService.JobCandidate> result =
                new ArrayList<>();

        Set<String> addedJobIds =
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

            if (!addedJobIds.add(
                    normalizedJobId
            )) {

                continue;
            }

            NormalizedJob job =
                    jobsById.get(
                            normalizedJobId
                    );

            if (job == null) {

                /*
                 * Có Qdrant point nhưng NormalizedJob
                 * tương ứng không còn tồn tại.
                 */
                continue;
            }

            result.add(
                    new HybridRankingService.JobCandidate(
                            hit,
                            job
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private Set<String> collectRetrievedJobIds(
            List<JobVectorHit> hits
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        for (JobVectorHit hit : hits) {

            if (hit == null
                    || hit.normalizedJobId() == null
                    || hit.normalizedJobId().isBlank()) {

                continue;
            }

            result.add(
                    hit.normalizedJobId()
            );
        }

        return Set.copyOf(
                result
        );
    }

    private void validateVector(
            List<Double> vector
    ) {
        if (vector == null
                || vector.isEmpty()) {

            throw new IllegalArgumentException(
                    "candidate vector must not be empty"
            );
        }

        for (Double value : vector) {

            if (value == null
                    || !Double.isFinite(
                    value
            )) {

                throw new IllegalArgumentException(
                        "candidate vector contains a non-finite value"
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

            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }
    }

    public record EvaluationResult(
            JobVectorSearchCriteria criteria,
            int retrievedCount,
            int hydratedCount,
            List<HybridRankingService.RankedJob> rankedJobs,
            Set<String> retrievedJobIds,
            Set<String> hydratedJobIds
    ) {

        public EvaluationResult {

            Objects.requireNonNull(
                    criteria,
                    "criteria must not be null"
            );

            rankedJobs =
                    rankedJobs == null
                            ? List.of()
                            : List.copyOf(
                            rankedJobs
                    );

            retrievedJobIds =
                    retrievedJobIds == null
                            ? Set.of()
                            : Set.copyOf(
                            retrievedJobIds
                    );

            hydratedJobIds =
                    hydratedJobIds == null
                            ? Set.of()
                            : Set.copyOf(
                            hydratedJobIds
                    );
        }

        public Optional<HybridRankingService.RankedJob> findRankedJob(
                String normalizedJobId
        ) {
            if (normalizedJobId == null
                    || normalizedJobId.isBlank()) {

                return Optional.empty();
            }

            return rankedJobs
                    .stream()
                    .filter(
                            ranked ->
                                    normalizedJobId.equals(
                                            ranked
                                                    .job()
                                                    .getId()
                                    )
                    )
                    .findFirst();
        }

        public boolean wasRetrieved(
                String normalizedJobId
        ) {
            return normalizedJobId != null
                    && retrievedJobIds.contains(
                    normalizedJobId
            );
        }

        public boolean wasHydrated(
                String normalizedJobId
        ) {
            return normalizedJobId != null
                    && hydratedJobIds.contains(
                    normalizedJobId
            );
        }
    }
}