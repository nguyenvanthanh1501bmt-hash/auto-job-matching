package com.autojob.modules.jobnormalizer.normalization;

public record ExperienceNormalizationResult(
        Double min,
        Double max
) {

    public boolean known() {
        return min != null || max != null;
    }
}