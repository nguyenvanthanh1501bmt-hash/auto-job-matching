package com.autojob.modules.jobembedding.text;

import com.autojob.modules.jobembedding.config.JobEmbeddingProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.normalization.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JobEmbeddingTextBuilder {

    private static final String PASSAGE_PREFIX = "passage: ";
    private static final String TRUNCATION_MARKER = "…";

    private final TextNormalizer textNormalizer;
    private final JobEmbeddingProperties properties;

    public String build(NormalizedJob job) {
        if (job == null) {
            return null;
        }

        List<String> sections = new ArrayList<>();

        addSection(sections, "Title", job.getTitle());
        addSection(sections, "Company", job.getCompanyName());
        addSection(
                sections,
                "Skills",
                joinStable(job.getSkills())
        );
        addSection(
                sections,
                "Seniority",
                displayEnum(job.getSeniority())
        );
        addSection(
                sections,
                "Experience",
                formatExperience(
                        job.getExperienceMin(),
                        job.getExperienceMax()
                )
        );
        addSection(
                sections,
                "Locations",
                joinStable(job.getLocations())
        );
        addSection(
                sections,
                "Job type",
                displayEnum(job.getJobType())
        );

        /*
         * Requirements được ưu tiên trước description
         * vì đây thường là tín hiệu matching trực tiếp hơn.
         */
        addSection(
                sections,
                "Requirements",
                truncate(
                        clean(job.getRequirementsText()),
                        properties.getRequirementsMaxChars()
                )
        );

        addSection(
                sections,
                "Description",
                truncate(
                        clean(job.getDescriptionText()),
                        properties.getDescriptionMaxChars()
                )
        );

        addSection(
                sections,
                "Benefits",
                truncate(
                        clean(job.getBenefitsText()),
                        properties.getBenefitsMaxChars()
                )
        );

        if (sections.isEmpty()) {
            return null;
        }

        return truncate(
                PASSAGE_PREFIX
                        + String.join("\n", sections),
                properties.getTextMaxChars()
        );
    }

    private void addSection(
            List<String> sections,
            String label,
            String value
    ) {
        String cleaned = clean(value);

        if (cleaned != null) {
            sections.add(
                    label + ": " + cleaned
            );
        }
    }

    private String joinStable(
            Collection<String> values
    ) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        Map<String, String> byKey =
                new LinkedHashMap<>();

        values.stream()
                .map(this::clean)
                .filter(value -> value != null)
                .sorted(
                        Comparator.comparing(
                                value -> value.toLowerCase(
                                        Locale.ROOT
                                )
                        )
                )
                .forEach(
                        value -> byKey.putIfAbsent(
                                value.toLowerCase(
                                        Locale.ROOT
                                ),
                                value
                        )
                );

        if (byKey.isEmpty()) {
            return null;
        }

        return String.join(
                ", ",
                byKey.values()
        );
    }

    private String displayEnum(
            Enum<?> value
    ) {
        if (value == null
                || "UNKNOWN".equals(value.name())) {
            return null;
        }

        String lower = value.name()
                .replace('_', ' ')
                .toLowerCase(Locale.ROOT);

        return Character.toUpperCase(
                lower.charAt(0)
        ) + lower.substring(1);
    }

    private String formatExperience(
            Double minimum,
            Double maximum
    ) {
        Double min = validYears(minimum);
        Double max = validYears(maximum);

        if (min == null && max == null) {
            return null;
        }

        if (min != null && max != null) {
            if (Double.compare(min, max) == 0) {
                return formatNumber(min)
                        + " years";
            }

            return formatNumber(min)
                    + " - "
                    + formatNumber(max)
                    + " years";
        }

        if (min != null) {
            return "From "
                    + formatNumber(min)
                    + " years";
        }

        return "Up to "
                + formatNumber(max)
                + " years";
    }

    private Double validYears(Double value) {
        if (value == null
                || !Double.isFinite(value)
                || value < 0) {
            return null;
        }

        return value;
    }

    private String formatNumber(
            double value
    ) {
        return BigDecimal.valueOf(value)
                .stripTrailingZeros()
                .toPlainString();
    }

    private String clean(String value) {
        return textNormalizer.normalizeInline(
                value
        );
    }

    private String truncate(
            String value,
            int maxChars
    ) {
        if (value == null
                || value.length() <= maxChars) {
            return value;
        }

        if (maxChars
                <= TRUNCATION_MARKER.length()) {
            return TRUNCATION_MARKER.substring(
                    0,
                    maxChars
            );
        }

        int end = maxChars
                - TRUNCATION_MARKER.length();

        /*
         * Tránh cắt giữa surrogate pair.
         */
        if (end > 0
                && Character.isHighSurrogate(
                value.charAt(end - 1)
        )) {
            end--;
        }

        return value.substring(0, end)
                .stripTrailing()
                + TRUNCATION_MARKER;
    }
}