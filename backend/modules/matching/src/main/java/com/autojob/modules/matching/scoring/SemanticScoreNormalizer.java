package com.autojob.modules.matching.scoring;

import com.autojob.modules.matching.config.MatchingProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class SemanticScoreNormalizer {

    private final MatchingProperties properties;

    public SemanticScoreNormalizer(
            MatchingProperties properties
    ) {
        this.properties =
                properties;
    }

    /**
     * Backward-compatible single score normalization.
     *
     * Giữ method này để không làm vỡ caller/test cũ.
     *
     * HybridRankingService v3 dùng overload có
     * Calibration ở dưới.
     */
    public double normalize(
            double qdrantScore
    ) {
        validateFinite(
                qdrantScore
        );

        return clamp01(
                qdrantScore
        );
    }

    /**
     * Tính robust semantic range từ toàn candidate pool.
     *
     * Không dùng min/max thẳng vì một outlier có thể kéo
     * toàn bộ distribution.
     */
    public Calibration calibrate(
            List<Double> rawScores
    ) {
        List<Double> finiteScores =
                new ArrayList<>();

        if (rawScores != null) {

            for (Double score : rawScores) {

                if (score != null
                        && Double.isFinite(score)) {

                    finiteScores.add(
                            score
                    );
                }
            }
        }

        if (finiteScores.isEmpty()) {

            return Calibration.unavailable();
        }

        finiteScores.sort(
                Comparator.naturalOrder()
        );

        MatchingProperties.SemanticCalibration
                config =
                properties
                        .getSemanticCalibration();

        double lower =
                percentile(
                        finiteScores,
                        config.getLowerPercentile()
                );

        double upper =
                percentile(
                        finiteScores,
                        config.getUpperPercentile()
                );

        /*
         * Không phóng đại pool quá hẹp.
         *
         * Ví dụ:
         *
         * p10 = 0.861
         * p90 = 0.869
         *
         * spread thực = 0.008
         *
         * nhưng minimumSpread = 0.04.
         */
        if (
                upper - lower
                        < config.getMinimumSpread()
        ) {

            double midpoint =
                    (
                            lower
                                    + upper
                    ) / 2.0d;

            double halfSpread =
                    config
                            .getMinimumSpread()
                            / 2.0d;

            lower =
                    midpoint
                            - halfSpread;

            upper =
                    midpoint
                            + halfSpread;
        }

        return new Calibration(
                lower,
                upper,
                true
        );
    }

    /**
     * Semantic score dùng cho hybrid ranking.
     *
     * absolute:
     *
     * rawFloor -> 0
     * rawCeiling -> 1
     *
     * relative:
     *
     * pool p10 -> 0
     * pool p90 -> 1
     *
     * Kết quả kết hợp cả hai để:
     *
     * - không coi raw cosine 0.85 là 85% fit
     * - vẫn giữ ordering semantic trong candidate pool
     * - không phóng đại một pool rất hẹp
     */
    public double normalize(
            double qdrantScore,
            Calibration calibration
    ) {
        validateFinite(
                qdrantScore
        );

        MatchingProperties.SemanticCalibration
                config =
                properties
                        .getSemanticCalibration();

        double absoluteScore =
                scale(
                        qdrantScore,
                        config.getRawFloor(),
                        config.getRawCeiling()
                );

        if (calibration == null
                || !calibration.available()) {

            return absoluteScore;
        }

        double relativeScore =
                scale(
                        qdrantScore,
                        calibration.lower(),
                        calibration.upper()
                );

        double relativeWeight =
                config.getRelativeWeight();

        double finalScore =
                relativeScore
                        * relativeWeight
                        + absoluteScore
                        * (
                        1.0d
                                - relativeWeight
                );

        return clamp01(
                finalScore
        );
    }

    private double percentile(
            List<Double> sorted,
            double percentile
    ) {
        if (sorted.size() == 1) {

            return sorted.getFirst();
        }

        double position =
                clamp01(percentile)
                        * (
                        sorted.size()
                                - 1
                );

        int lowerIndex =
                (int) Math.floor(
                        position
                );

        int upperIndex =
                (int) Math.ceil(
                        position
                );

        if (lowerIndex == upperIndex) {

            return sorted.get(
                    lowerIndex
            );
        }

        double fraction =
                position
                        - lowerIndex;

        return sorted.get(
                lowerIndex
        ) * (
                1.0d
                        - fraction
        )
                + sorted.get(
                upperIndex
        ) * fraction;
    }

    private double scale(
            double value,
            double lower,
            double upper
    ) {
        if (upper <= lower) {
            return 0.50d;
        }

        return clamp01(
                (
                        value
                                - lower
                )
                        / (
                        upper
                                - lower
                )
        );
    }

    private void validateFinite(
            double value
    ) {
        if (!Double.isFinite(value)) {

            throw new IllegalArgumentException(
                    "qdrantScore must be finite"
            );
        }
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

    public record Calibration(
            double lower,
            double upper,
            boolean available
    ) {

        public static Calibration unavailable() {

            return new Calibration(
                    0.0d,
                    1.0d,
                    false
            );
        }
    }
}