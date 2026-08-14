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

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "autojob.matching")
public class MatchingProperties {

    private static final double WEIGHT_SUM_TOLERANCE = 0.000001d;

    /**
     * Version của matching/ranking strategy.
     *
     * Khi scoring semantics thay đổi đáng kể,
     * tăng version thay vì âm thầm đổi behavior.
     */
    @NotBlank
    private String version = "hybrid-v1";

    @Valid
    @NotNull
    private Retrieval retrieval = new Retrieval();

    @Valid
    @NotNull
    private Compatibility compatibility = new Compatibility();

    @Valid
    @NotNull
    private Weights weights = new Weights();

    @Valid
    @NotNull
    private Freshness freshness = new Freshness();

    /**
     * Tổng weights phải bằng 1.
     *
     * Nhờ vậy matching service ngày mai không cần tự normalize
     * weights và behavior sẽ predictable hơn.
     */
    @AssertTrue(
            message = "autojob.matching.weights must sum to 1.0"
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

        return Math.abs(sum - 1.0d)
                <= WEIGHT_SUM_TOLERANCE;
    }

    /**
     * Retrieval pool phải >= số result cuối cùng.
     *
     * Ví dụ:
     * vector search top 100 -> rerank -> trả top 20.
     */
    @AssertTrue(
            message = "autojob.matching.retrieval.candidate-pool-size "
                    + "must be greater than or equal to result-limit"
    )
    public boolean isRetrievalWindowValid() {
        if (retrieval == null) {
            return false;
        }

        return retrieval.getCandidatePoolSize()
                >= retrieval.getResultLimit();
    }

    @Getter
    @Setter
    public static class Retrieval {

        /**
         * Số job lấy từ semantic/vector retrieval
         * trước khi rule-based reranking.
         */
        @Min(1)
        private int candidatePoolSize = 100;

        /**
         * Số kết quả cuối cùng.
         */
        @Min(1)
        private int resultLimit = 20;
    }

    @Getter
    @Setter
    public static class Compatibility {

        /**
         * Normalization version matching hỗ trợ.
         */
        @NotBlank
        private String normalizationVersion = "rule-v2";

        /**
         * Candidate embedding text format mong đợi.
         */
        @NotBlank
        private String candidateTextVersion = "candidate-text-v1";

        /**
         * Job embedding text format mong đợi.
         *
         * Batch embedding tiếp theo sẽ thêm textVersion
         * vào JobEmbedding.
         */
        @NotBlank
        private String jobTextVersion = "job-text-v2";
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

        /**
         * Job trong khoảng này được xem là fresh.
         */
        @Min(0)
        private int freshDays = 7;

        /**
         * Tuổi tối đa dự kiến cho một job được phép match.
         *
         * Hôm nay mới setup config, chưa implement filter/scorer.
         */
        @Min(1)
        private int maxAgeDays = 30;
    }
}