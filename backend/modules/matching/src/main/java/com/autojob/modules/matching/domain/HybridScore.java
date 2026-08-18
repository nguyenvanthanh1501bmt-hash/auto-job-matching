package com.autojob.modules.matching.domain;

/**
 * Snapshot đầy đủ của hybrid score cho một candidate-job pair.
 *
 * Tất cả score luôn nằm trong khoảng [0, 1].
 */
public record HybridScore(
        double finalScore,
        double semanticScore,
        double skillScore,
        double seniorityScore,
        double locationScore,
        double freshnessScore
) {

    public HybridScore {
        requireScore(finalScore, "finalScore");
        requireScore(semanticScore, "semanticScore");
        requireScore(skillScore, "skillScore");
        requireScore(seniorityScore, "seniorityScore");
        requireScore(locationScore, "locationScore");
        requireScore(freshnessScore, "freshnessScore");
    }

    private static void requireScore(
            double value,
            String fieldName
    ) {
        if (!Double.isFinite(value)
                || value < 0.0d
                || value > 1.0d) {
            throw new IllegalArgumentException(
                    fieldName + " must be between 0.0 and 1.0"
            );
        }
    }
}