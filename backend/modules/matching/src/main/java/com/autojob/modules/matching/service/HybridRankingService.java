package com.autojob.modules.matching.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.jobembedding.search.JobVectorHit;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.config.MatchingProperties;
import com.autojob.modules.matching.domain.HybridScore;
import com.autojob.modules.matching.filter.JobEligibilityFilter;
import com.autojob.modules.matching.filter.MatchAcceptanceFilter;
import com.autojob.modules.matching.scoring.FreshnessScorer;
import com.autojob.modules.matching.scoring.LocationScorer;
import com.autojob.modules.matching.scoring.SemanticScoreNormalizer;
import com.autojob.modules.matching.scoring.SeniorityScorer;
import com.autojob.modules.matching.scoring.SkillScorer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class HybridRankingService {

    private static final double UNKNOWN_SENTINEL =
            0.50d;

    private static final double EPSILON =
            0.000000001d;

    private final SemanticScoreNormalizer semanticScoreNormalizer;
    private final SkillScorer skillScorer;
    private final SeniorityScorer seniorityScorer;
    private final LocationScorer locationScorer;
    private final FreshnessScorer freshnessScorer;
    private final JobEligibilityFilter eligibilityFilter;
    private final MatchAcceptanceFilter acceptanceFilter;
    private final MatchingProperties properties;

    public HybridRankingService(
            SemanticScoreNormalizer semanticScoreNormalizer,
            SkillScorer skillScorer,
            SeniorityScorer seniorityScorer,
            LocationScorer locationScorer,
            FreshnessScorer freshnessScorer,
            JobEligibilityFilter eligibilityFilter,
            MatchAcceptanceFilter acceptanceFilter,
            MatchingProperties properties
    ) {
        this.semanticScoreNormalizer =
                semanticScoreNormalizer;

        this.skillScorer =
                skillScorer;

        this.seniorityScorer =
                seniorityScorer;

        this.locationScorer =
                locationScorer;

        this.freshnessScorer =
                freshnessScorer;

        this.eligibilityFilter =
                eligibilityFilter;

        this.acceptanceFilter =
                acceptanceFilter;

        this.properties =
                properties;
    }

    public List<RankedJob> rank(
            CandidateProfile candidate,
            List<JobCandidate> candidates,
            int limit
    ) {
        Objects.requireNonNull(
                candidate,
                "candidate must not be null"
        );

        if (candidates == null
                || candidates.isEmpty()
                || limit <= 0) {

            return List.of();
        }

        /*
         * -------------------------------------------------
         * Stage 1: hard eligibility
         * -------------------------------------------------
         *
         * Invalid/expired/incompatible jobs không được
         * tham gia semantic calibration.
         */
        List<JobCandidate> eligible =
                candidates
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(
                                value ->
                                        eligibilityFilter.isEligible(
                                                value.job()
                                        )
                        )
                        .toList();

        if (eligible.isEmpty()) {
            return List.of();
        }

        /*
         * -------------------------------------------------
         * Stage 2: semantic calibration
         * -------------------------------------------------
         */
        SemanticScoreNormalizer.Calibration
                semanticCalibration =
                semanticScoreNormalizer.calibrate(
                        eligible
                                .stream()
                                .map(
                                        value ->
                                                value
                                                        .vectorHit()
                                                        .score()
                                )
                                .toList()
                );

        /*
         * -------------------------------------------------
         * Stage 3: hybrid scoring
         * -------------------------------------------------
         */
        List<ScoredJob> scored =
                eligible
                        .stream()
                        .map(
                                value ->
                                        score(
                                                candidate,
                                                value,
                                                semanticCalibration
                                        )
                        )
                        .toList();

        /*
         * -------------------------------------------------
         * Stage 4: recommendation acceptance
         * -------------------------------------------------
         *
         * Quan trọng:
         *
         * filter TRƯỚC limit.
         *
         * Nếu chỉ có 6 job đủ relevance thì trả 6,
         * không lấy job #7..#20 yếu để lấp đủ quota.
         */
        List<ScoredJob> accepted =
                scored
                        .stream()
                        .filter(
                                value ->
                                        acceptanceFilter.accept(
                                                value
                                                        .candidate()
                                                        .job(),
                                                value.score()
                                        )
                        )
                        .sorted(
                                scoredComparator()
                        )
                        .limit(limit)
                        .toList();

        List<RankedJob> ranked =
                new ArrayList<>(
                        accepted.size()
                );

        for (
                int index = 0;
                index < accepted.size();
                index++
        ) {

            ScoredJob value =
                    accepted.get(index);

            ranked.add(
                    new RankedJob(
                            index + 1,
                            value
                                    .candidate()
                                    .job(),
                            value
                                    .candidate()
                                    .vectorHit()
                                    .pointId(),
                            value.score(),
                            value.matchedSkills(),
                            value.missingSkills()
                    )
            );
        }

        return List.copyOf(ranked);
    }

    private ScoredJob score(
            CandidateProfile candidate,
            JobCandidate jobCandidate,
            SemanticScoreNormalizer.Calibration semanticCalibration
    ) {
        NormalizedJob job =
                jobCandidate.job();

        double semanticScore =
                semanticScoreNormalizer.normalize(
                        jobCandidate
                                .vectorHit()
                                .score(),
                        semanticCalibration
                );

        SkillScorer.Result skillResult =
                skillScorer.score(
                        candidate,
                        job
                );

        double seniorityScore =
                seniorityScorer.score(
                        candidate,
                        job
                );

        double locationScore =
                locationScorer.score(
                        candidate,
                        job
                );

        double freshnessScore =
                freshnessScorer.score(
                        job
                );

        double finalScore =
                combineKnownComponents(
                        job,
                        semanticScore,
                        skillResult.score(),
                        seniorityScore,
                        locationScore,
                        freshnessScore
                );

        return new ScoredJob(
                jobCandidate,
                new HybridScore(
                        finalScore,
                        semanticScore,
                        skillResult.score(),
                        seniorityScore,
                        locationScore,
                        freshnessScore
                ),
                skillResult.matchedSkills(),
                skillResult.missingSkills()
        );
    }

    private double combineKnownComponents(
            NormalizedJob job,
            double semanticScore,
            double skillScore,
            double seniorityScore,
            double locationScore,
            double freshnessScore
    ) {
        MatchingProperties.Weights weights =
                properties.getWeights();

        double weightedSum =
                0.0d;

        double activeWeight =
                0.0d;

        /*
         * Semantic always available.
         */
        weightedSum +=
                semanticScore
                        * weights.getSemantic();

        activeWeight +=
                weights.getSemantic();

        /*
         * Structured skills available.
         */
        if (job.getSkills() != null
                && !job.getSkills().isEmpty()) {

            weightedSum +=
                    skillScore
                            * weights.getSkill();

            activeWeight +=
                    weights.getSkill();
        }

        /*
         * Unknown seniority does not receive weight.
         */
        if (isKnownStructuredScore(
                seniorityScore
        )) {

            weightedSum +=
                    seniorityScore
                            * weights.getSeniority();

            activeWeight +=
                    weights.getSeniority();
        }

        /*
         * Unknown/no-decision location does not receive
         * artificial neutral weight.
         */
        if (isKnownStructuredScore(
                locationScore
        )) {

            weightedSum +=
                    locationScore
                            * weights.getLocation();

            activeWeight +=
                    weights.getLocation();
        }

        /*
         * Freshness available when there is a timestamp.
         */
        if (job.getPostedAt() != null
                || job.getNormalizedAt() != null) {

            weightedSum +=
                    freshnessScore
                            * weights.getFreshness();

            activeWeight +=
                    weights.getFreshness();
        }

        if (activeWeight <= 0.0d) {
            return 0.0d;
        }

        return clamp01(
                weightedSum
                        / activeWeight
        );
    }

    private boolean isKnownStructuredScore(
            double score
    ) {
        return Double.isFinite(score)
                && Math.abs(
                score - UNKNOWN_SENTINEL
        ) > EPSILON;
    }

    private Comparator<ScoredJob> scoredComparator() {

        return Comparator
                .comparingDouble(
                        (ScoredJob value) ->
                                value
                                        .score()
                                        .finalScore()
                )
                .reversed()

                .thenComparing(
                        Comparator
                                .comparingDouble(
                                        (ScoredJob value) ->
                                                value
                                                        .score()
                                                        .semanticScore()
                                )
                                .reversed()
                )

                .thenComparing(
                        Comparator
                                .comparingDouble(
                                        (ScoredJob value) ->
                                                value
                                                        .score()
                                                        .freshnessScore()
                                )
                                .reversed()
                )

                .thenComparing(
                        value ->
                                safe(
                                        value
                                                .candidate()
                                                .job()
                                                .getId()
                                )
                )

                .thenComparing(
                        value ->
                                safe(
                                        value
                                                .candidate()
                                                .vectorHit()
                                                .pointId()
                                )
                );
    }

    private String safe(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }

    private double clamp01(
            double value
    ) {
        return Math.max(
                0.0d,
                Math.min(
                        1.0d,
                        value
                )
        );
    }

    public record JobCandidate(
            JobVectorHit vectorHit,
            NormalizedJob job
    ) {

        public JobCandidate {

            Objects.requireNonNull(
                    vectorHit,
                    "vectorHit must not be null"
            );

            Objects.requireNonNull(
                    job,
                    "job must not be null"
            );

            if (!Objects.equals(
                    vectorHit.normalizedJobId(),
                    job.getId()
            )) {

                throw new IllegalArgumentException(
                        "vectorHit.normalizedJobId "
                                + "must match normalized job id"
                );
            }
        }
    }

    /*
     * Giữ pointId để backward-compatible với
     * HybridMatchingService.rankResult.pointId().
     */
    public record RankedJob(
            int rank,
            NormalizedJob job,
            String pointId,
            HybridScore score,
            List<String> matchedSkills,
            List<String> missingSkills
    ) {

        public RankedJob {

            matchedSkills =
                    matchedSkills == null
                            ? List.of()
                            : List.copyOf(
                            matchedSkills
                    );

            missingSkills =
                    missingSkills == null
                            ? List.of()
                            : List.copyOf(
                            missingSkills
                    );
        }
    }

    private record ScoredJob(
            JobCandidate candidate,
            HybridScore score,
            List<String> matchedSkills,
            List<String> missingSkills
    ) {
    }
}