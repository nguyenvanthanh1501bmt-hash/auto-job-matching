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
                Objects.requireNonNull(
                        semanticScoreNormalizer,
                        "semanticScoreNormalizer must not be null"
                );

        this.skillScorer =
                Objects.requireNonNull(
                        skillScorer,
                        "skillScorer must not be null"
                );

        this.seniorityScorer =
                Objects.requireNonNull(
                        seniorityScorer,
                        "seniorityScorer must not be null"
                );

        this.locationScorer =
                Objects.requireNonNull(
                        locationScorer,
                        "locationScorer must not be null"
                );

        this.freshnessScorer =
                Objects.requireNonNull(
                        freshnessScorer,
                        "freshnessScorer must not be null"
                );

        this.eligibilityFilter =
                Objects.requireNonNull(
                        eligibilityFilter,
                        "eligibilityFilter must not be null"
                );

        this.acceptanceFilter =
                Objects.requireNonNull(
                        acceptanceFilter,
                        "acceptanceFilter must not be null"
                );

        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );
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

        SemanticScoreNormalizer.Calibration semanticCalibration =
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
                        .limit(
                                limit
                        )
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

        return List.copyOf(
                ranked
        );
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

        SeniorityScorer.Result seniorityResult =
                seniorityScorer.evaluate(
                        candidate,
                        job
                );

        LocationScorer.Result locationResult =
                locationScorer.evaluate(
                        candidate,
                        job
                );

        FreshnessScorer.Result freshnessResult =
                freshnessScorer.evaluate(
                        job
                );

        /*
         * Không còn suy skillKnown bằng:
         *
         * job.getSkills() != empty
         *
         * SkillScorer mới là nơi biết skill nào PRIMARY,
         * skill nào SECONDARY.
         *
         * JD chỉ có Communication/Teamwork:
         *
         * skillResult.known() = false
         */
        boolean skillKnown =
                skillResult.known();

        double finalScore =
                combineKnownComponents(
                        semanticScore,

                        skillResult.score(),
                        skillKnown,

                        seniorityResult.score(),
                        seniorityResult.known(),

                        locationResult.score(),
                        locationResult.known(),

                        freshnessResult.score(),
                        freshnessResult.known()
                );

        HybridScore hybridScore =
                new HybridScore(
                        finalScore,

                        semanticScore,

                        skillResult.score(),

                        seniorityResult.score(),

                        locationResult.score(),

                        freshnessResult.score(),

                        skillKnown,

                        seniorityResult.known(),

                        locationResult.known(),

                        freshnessResult.known()
                );

        return new ScoredJob(
                jobCandidate,
                hybridScore,
                skillResult.matchedSkills(),
                skillResult.missingSkills()
        );
    }

    private double combineKnownComponents(
            double semanticScore,

            double skillScore,
            boolean skillKnown,

            double seniorityScore,
            boolean seniorityKnown,

            double locationScore,
            boolean locationKnown,

            double freshnessScore,
            boolean freshnessKnown
    ) {
        MatchingProperties.Weights weights =
                properties.getWeights();

        double weightedSum =
                0.0d;

        double activeWeight =
                0.0d;

        /*
         * Semantic luôn known.
         */
        weightedSum +=
                semanticScore
                        * weights.getSemantic();

        activeWeight +=
                weights.getSemantic();

        /*
         * Skill chỉ active khi JD có ít nhất
         * một PRIMARY/domain skill.
         */
        if (skillKnown) {

            weightedSum +=
                    skillScore
                            * weights.getSkill();

            activeWeight +=
                    weights.getSkill();
        }

        if (seniorityKnown) {

            weightedSum +=
                    seniorityScore
                            * weights.getSeniority();

            activeWeight +=
                    weights.getSeniority();
        }

        if (locationKnown) {

            weightedSum +=
                    locationScore
                            * weights.getLocation();

            activeWeight +=
                    weights.getLocation();
        }

        if (freshnessKnown) {

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

    private Comparator<ScoredJob> scoredComparator() {

        /*
         * Component scores đã được tính vào finalScore
         * khi chúng known.
         *
         * Không reuse chúng làm tie-breaker để tránh
         * double-count và tránh numeric placeholder của
         * UNKNOWN ảnh hưởng thứ tự.
         */
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