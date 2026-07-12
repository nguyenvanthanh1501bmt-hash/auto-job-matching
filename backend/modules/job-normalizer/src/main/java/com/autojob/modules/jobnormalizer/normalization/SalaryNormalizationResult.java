package com.autojob.modules.jobnormalizer.normalization;

public record SalaryNormalizationResult(
        Long min,
        Long max,
        String currency
) {

    public boolean hasAmount() {
        return min != null || max != null;
    }
}