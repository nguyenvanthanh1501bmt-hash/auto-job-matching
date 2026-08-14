package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component("normalizedJobEmbeddingTextBuilder")
@RequiredArgsConstructor
public class JobEmbeddingTextBuilder {

    private static final String MODEL_INPUT_PREFIX = "query: ";
    private static final String TRUNCATION_MARKER = "…";

    private final TextNormalizer textNormalizer;
    private final NormalizationProperties normalizationProperties;

    public String build(NormalizedJob normalizedJob) {
        Objects.requireNonNull(
                normalizedJob,
                "normalizedJob must not be null"
        );

        int maxChars = normalizationProperties
                .getEmbeddingTextMaxChars();

        StringBuilder result = new StringBuilder(
                MODEL_INPUT_PREFIX
        );

        int sectionCount = 0;

        sectionCount = appendSection(
                result,
                sectionCount,
                "Title",
                normalizeInline(normalizedJob.getTitle()),
                maxChars
        );

        sectionCount = appendSection(
                result,
                sectionCount,
                "Company",
                normalizeInline(normalizedJob.getCompanyName()),
                maxChars
        );

        sectionCount = appendSection(
                result,
                sectionCount,
                "Skills",
                joinStable(normalizedJob.getSkills()),
                maxChars
        );

        sectionCount = appendSection(
                result,
                sectionCount,
                "Seniority",
                formatSeniority(normalizedJob.getSeniority()),
                maxChars
        );

        sectionCount = appendSection(
                result,
                sectionCount,
                "Experience",
                formatExperience(
                        normalizedJob.getExperienceMin(),
                        normalizedJob.getExperienceMax()
                ),
                maxChars
        );

        sectionCount = appendSection(
                result,
                sectionCount,
                "Locations",
                joinStable(normalizedJob.getLocations()),
                maxChars
        );

        sectionCount = appendSection(
                result,
                sectionCount,
                "Job type",
                formatJobType(normalizedJob.getJobType()),
                maxChars
        );

        /*
         * Requirements được đặt trước description và benefits
         * để được ưu tiên khi chạm giới hạn tổng.
         */
        sectionCount = appendSection(
                result,
                sectionCount,
                "Requirements",
                normalizeAndLimit(
                        normalizedJob.getRequirementsText(),
                        normalizationProperties
                                .getEmbeddingRequirementsMaxChars()
                ),
                maxChars
        );

        sectionCount = appendSection(
                result,
                sectionCount,
                "Description",
                normalizeAndLimit(
                        normalizedJob.getDescriptionText(),
                        normalizationProperties
                                .getEmbeddingDescriptionMaxChars()
                ),
                maxChars
        );

        sectionCount = appendSection(
                result,
                sectionCount,
                "Benefits",
                normalizeAndLimit(
                        normalizedJob.getBenefitsText(),
                        normalizationProperties
                                .getEmbeddingBenefitsMaxChars()
                ),
                maxChars
        );

        if (sectionCount == 0) {
            return null;
        }

        return result.toString();
    }

    private int appendSection(
            StringBuilder result,
            int sectionCount,
            String label,
            String value,
            int maxChars
    ) {
        if (value == null || value.isBlank()) {
            return sectionCount;
        }

        String separator = sectionCount == 0
                ? ""
                : "\n";

        String labelPrefix = label + ": ";

        int remainingValueChars = maxChars
                - result.length()
                - separator.length()
                - labelPrefix.length();

        if (remainingValueChars <= 0) {
            return sectionCount;
        }

        result.append(separator)
                .append(labelPrefix)
                .append(truncate(
                        value,
                        remainingValueChars
                ));

        return sectionCount + 1;
    }

    private String normalizeInline(String value) {
        return textNormalizer.normalizeInline(value);
    }

    private String normalizeAndLimit(
            String value,
            int fieldMaxChars
    ) {
        String normalized = normalizeInline(value);

        if (normalized == null) {
            return null;
        }

        return truncate(normalized, fieldMaxChars);
    }

    private String joinStable(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        Map<String, String> valuesByStableKey =
                new LinkedHashMap<>();

        values.stream()
                .map(this::normalizeInline)
                .filter(Objects::nonNull)
                .sorted(stableTextComparator())
                .forEach(value -> valuesByStableKey.putIfAbsent(
                        NormalizationTextSupport.fold(value),
                        value
                ));

        if (valuesByStableKey.isEmpty()) {
            return null;
        }

        return String.join(
                ", ",
                valuesByStableKey.values()
        );
    }

    private Comparator<String> stableTextComparator() {
        return Comparator
                .comparing(NormalizationTextSupport::fold)
                .thenComparing(Comparator.naturalOrder());
    }

    private String formatSeniority(
            SeniorityLevel seniority
    ) {
        if (seniority == null
                || seniority == SeniorityLevel.UNKNOWN) {
            return null;
        }

        return formatEnumName(seniority.name());
    }

    private String formatJobType(
            NormalizedJobType jobType
    ) {
        if (jobType == null
                || jobType == NormalizedJobType.UNKNOWN) {
            return null;
        }

        return formatEnumName(jobType.name());
    }

    private String formatEnumName(String enumName) {
        String lowerCase = enumName
                .replace('_', ' ')
                .toLowerCase(Locale.ROOT);

        return Character.toUpperCase(
                lowerCase.charAt(0)
        ) + lowerCase.substring(1);
    }

    private String formatExperience(
            Double minimum,
            Double maximum
    ) {
        Double validMinimum = finiteNonNegative(minimum);
        Double validMaximum = finiteNonNegative(maximum);

        if (validMinimum == null
                && validMaximum == null) {
            return null;
        }

        if (validMinimum != null
                && validMaximum != null) {
            if (Double.compare(
                    validMinimum,
                    validMaximum
            ) == 0) {
                return formatNumber(validMinimum)
                        + " years";
            }

            return formatNumber(validMinimum)
                    + " - "
                    + formatNumber(validMaximum)
                    + " years";
        }

        if (validMinimum != null) {
            return "From "
                    + formatNumber(validMinimum)
                    + " years";
        }

        return "Up to "
                + formatNumber(validMaximum)
                + " years";
    }

    private Double finiteNonNegative(Double value) {
        if (value == null
                || !Double.isFinite(value)
                || value < 0) {
            return null;
        }

        return value;
    }

    private String formatNumber(double value) {
        return BigDecimal.valueOf(value)
                .stripTrailingZeros()
                .toPlainString();
    }

    private String truncate(
            String value,
            int maxChars
    ) {
        if (value.length() <= maxChars) {
            return value;
        }

        if (maxChars <= TRUNCATION_MARKER.length()) {
            return TRUNCATION_MARKER.substring(
                    0,
                    maxChars
            );
        }

        int contentEnd = maxChars
                - TRUNCATION_MARKER.length();

        /*
         * Không cắt giữa surrogate pair của ký tự Unicode.
         */
        if (contentEnd > 0
                && Character.isHighSurrogate(
                value.charAt(contentEnd - 1)
        )) {
            contentEnd--;
        }

        String truncated = value
                .substring(0, contentEnd)
                .stripTrailing();

        return truncated + TRUNCATION_MARKER;
    }
}