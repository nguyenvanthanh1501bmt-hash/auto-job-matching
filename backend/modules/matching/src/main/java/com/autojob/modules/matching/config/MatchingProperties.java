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
@ConfigurationProperties(
        prefix = "autojob.matching"
)
public class MatchingProperties {

    private static final double WEIGHT_SUM_TOLERANCE =
            0.000001d;

    /*
     * Chưa bump version trong batch thử LANGUAGE.
     * Sau khi behavior thực tế ổn sẽ bump một lần cuối.
     */
    @NotBlank
    private String version =
            "hybrid-v6-balanced-r7";

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
        private int candidatePoolSize =
                100;

        @Min(1)
        private int resultLimit =
                20;
    }

    @Getter
    @Setter
    public static class Compatibility {

        @NotBlank
        private String normalizationVersion =
                "rule-v4";

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
        private double semantic =
                0.40d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double skill =
                0.40d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double seniority =
                0.10d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double location =
                0.05d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double freshness =
                0.05d;
    }

    @Getter
    @Setter
    public static class Freshness {

        @Min(0)
        private int freshDays =
                7;

        @Min(1)
        private int maxAgeDays =
                30;
    }

    @Getter
    @Setter
    public static class SemanticCalibration {

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double lowerPercentile =
                0.10d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double upperPercentile =
                0.90d;

        @DecimalMin("0.000001")
        private double minimumSpread =
                0.04d;

        private double rawFloor =
                0.75d;

        private double rawCeiling =
                0.95d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double relativeWeight =
                0.65d;

        @AssertTrue(
                message =
                        "lower-percentile must be < upper-percentile"
        )
        public boolean isPercentileRangeValid() {

            return lowerPercentile
                    < upperPercentile;
        }

        @AssertTrue(
                message =
                        "raw-floor must be < raw-ceiling"
        )
        public boolean isRawRangeValid() {

            return rawFloor
                    < rawCeiling;
        }
    }

    @Getter
    @Setter
    public static class SkillScoring {

        /*
         * PRIMARY/domain skill coverage.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double coreWeight =
                1.0d;

        /*
         * SECONDARY skill bonus.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double genericWeight =
                0.10d;

        /*
         * Nếu JD có PRIMARY skills nhưng candidate chỉ
         * match SECONDARY skills thì hard-cap rất thấp.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double genericOnlyCap =
                0.05d;

        /*
         * SECONDARY theo exact taxonomy ID.
         *
         * Ví dụ:
         * communication
         * teamwork
         */
        @NotNull
        private Set<String> genericSkillIds =
                new LinkedHashSet<>();

        /*
         * SECONDARY theo taxonomy category.
         *
         * Ví dụ:
         *
         * LANGUAGE
         *
         * Điều này giúp toàn bộ:
         *
         * English
         * Japanese
         * Korean
         * Chinese
         * ...
         *
         * trở thành supporting skills mà không cần
         * hard-code từng language ID trong Java.
         */
        @NotNull
        private Set<String> genericSkillCategories =
                new LinkedHashSet<>();

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

        @AssertTrue(
                message =
                        "generic-only-cap should not exceed generic-weight"
        )
        public boolean isGenericCapValid() {

            return genericOnlyCap
                    <= genericWeight;
        }
    }

    @Getter
    @Setter
    public static class Acceptance {

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double minimumFinalScore =
                0.45d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double minimumSemanticScore =
                0.50d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double minimumSkillScore =
                0.10d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double strongSkillScore =
                0.70d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double strongSemanticScore =
                0.70d;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double minimumNonContradictoryStructuredScore =
                0.10d;

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