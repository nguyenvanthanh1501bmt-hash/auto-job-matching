package com.autojob.modules.matching.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "autojob.matching")
public class MatchingProperties {

    private static final double WEIGHT_SUM_TOLERANCE =
            0.000001d;

    @NotBlank
    private String version =
            "hybrid-v6";

    @Valid
    @NotNull
    private Retrieval retrieval =
            new Retrieval();

    @Valid
    @NotNull
    private Compatibility compatibility =
            new Compatibility();

    @Valid
    @NotNull
    private Weights weights =
            new Weights();

    @Valid
    @NotNull
    private Freshness freshness =
            new Freshness();

    @Valid
    @NotNull
    private SemanticCalibration semanticCalibration =
            new SemanticCalibration();

    @Valid
    @NotNull
    private SkillScoring skillScoring =
            new SkillScoring();

    @Valid
    @NotNull
    private Acceptance acceptance =
            new Acceptance();

    @AssertTrue(
            message =
                    "autojob.matching.weights must sum to 1.0"
    )
    public boolean isWeightSumValid() {

        if (weights == null) {
            return false;
        }

        double sum =
                weights.getSemantic()
                        + weights.getSkill()
                        + weights.getSeniority()
                        + weights.getLocation()
                        + weights.getFreshness();

        return Math.abs(
                sum - 1.0d
        ) <= WEIGHT_SUM_TOLERANCE;
    }

    @AssertTrue(
            message =
                    "candidate-pool-size must be >= result-limit"
    )
    public boolean isRetrievalWindowValid() {

        return retrieval != null
                && retrieval.getCandidatePoolSize()
                >= retrieval.getResultLimit();
    }

    @Getter
    @Setter
    public static class Retrieval {

        @Min(1)
        private int candidatePoolSize = 100;

        @Min(1)
        private int resultLimit = 20;
    }

    @Getter
    @Setter
    public static class Compatibility {

        @NotBlank
        private String normalizationVersion =
                "rule-v3";

        @NotBlank
        private String candidateTextVersion =
                "candidate-text-v1";

        @NotBlank
        private String jobTextVersion =
                "job-text-v2";
    }

    @Getter
    @Setter
    public static class Weights {

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double semantic = 0.50d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double skill = 0.30d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double seniority = 0.10d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double location = 0.05d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double freshness = 0.05d;
    }

    @Getter
    @Setter
    public static class Freshness {

        @Min(0)
        private int freshDays = 7;

        @Min(1)
        private int maxAgeDays = 30;
    }

    @Getter
    @Setter
    public static class SemanticCalibration {

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double lowerPercentile = 0.10d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double upperPercentile = 0.90d;

        @DecimalMin("0.000001")
        private double minimumSpread = 0.04d;

        private double rawFloor = 0.75d;

        private double rawCeiling = 0.95d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double relativeWeight = 0.65d;

        @AssertTrue
        public boolean isPercentileRangeValid() {
            return lowerPercentile < upperPercentile;
        }

        @AssertTrue
        public boolean isRawRangeValid() {
            return rawFloor < rawCeiling;
        }
    }

    @Getter
    @Setter
    public static class SkillScoring {

        @DecimalMin("0.0")
        private double coreWeight = 1.0d;

        @DecimalMin("0.0")
        private double genericWeight = 0.20d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double genericOnlyCap = 0.15d;

        @NotNull
        private Set<String> genericSkillIds =
                new LinkedHashSet<>(
                        Set.of(
                                "communication",
                                "presentation",
                                "problem-solving",
                                "critical-thinking",
                                "teamwork",
                                "time-management"
                        )
                );

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double skillsSectionConfidence =
                1.00d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double workExperienceConfidence =
                1.00d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double projectConfidence =
                0.65d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double profileTextConfidence =
                0.55d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double scopedTextConfidence =
                0.50d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double unknownEvidenceConfidence =
                0.50d;
    }

    @Getter
    @Setter
    public static class Acceptance {

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double minimumFinalScore =
                0.50d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double minimumSemanticScore =
                0.55d;

        /**
         * Weak/moderate skill evidence.
         *
         * Nếu chỉ đạt mức này thì structured signals
         * phải không contradiction.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double minimumSkillScore =
                0.10d;

        /**
         * Skill overlap đủ mạnh để cho phép recommendation
         * dù seniority/location chưa lý tưởng.
         *
         * Đây là rule tổng quát, không phụ thuộc ngành.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double strongSkillScore =
                0.45d;

        /**
         * Semantic-only fallback.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double strongSemanticScore =
                0.90d;

        /**
         * Structured score thấp hơn mức này được xem
         * là contradiction rõ.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double minimumNonContradictoryStructuredScore =
                0.35d;

        @AssertTrue(
                message =
                        "strong-skill-score must be >= minimum-skill-score"
        )
        public boolean isSkillRangeValid() {

            return strongSkillScore
                    >= minimumSkillScore;
        }

        @AssertTrue(
                message =
                        "strong-semantic-score must be >= minimum-semantic-score"
        )
        public boolean isSemanticRangeValid() {

            return strongSemanticScore
                    >= minimumSemanticScore;
        }
    }
}