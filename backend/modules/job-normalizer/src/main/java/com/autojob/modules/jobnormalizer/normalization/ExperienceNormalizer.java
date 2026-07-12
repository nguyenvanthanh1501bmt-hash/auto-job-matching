package com.autojob.modules.jobnormalizer.normalization;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ExperienceNormalizer {

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("(?<!\\d)(\\d+(?:[.,]\\d+)?)(?!\\d)");

    private static final Pattern PLUS_SUFFIX_PATTERN =
            Pattern.compile("\\d(?:[\\d.,]*)\\s*\\+");

    private final TextNormalizer textNormalizer;

    public ExperienceNormalizationResult normalize(
            String experienceText
    ) {
        String cleaned = textNormalizer.normalizeInline(experienceText);

        if (cleaned == null) {
            return unknown();
        }

        String folded = NormalizationTextSupport.fold(cleaned);

        if (meansNoExperience(folded)) {
            return new ExperienceNormalizationResult(
                    0.0,
                    null
            );
        }

        boolean monthUnit = containsAny(
                folded,
                "month",
                "months",
                "thang"
        );

        List<Double> values = extractValues(cleaned, monthUnit);

        if (values.isEmpty()) {
            return unknown();
        }

        if (values.size() >= 2 && hasRangeMarker(cleaned, folded)) {
            double first = values.get(0);
            double second = values.get(1);

            return new ExperienceNormalizationResult(
                    Math.min(first, second),
                    Math.max(first, second)
            );
        }

        double value = values.get(0);

        if (isUpperBoundOnly(folded)) {
            return new ExperienceNormalizationResult(
                    0.0,
                    value
            );
        }

        if (monthUnit
                || isLowerBoundOnly(cleaned, folded)) {
            return new ExperienceNormalizationResult(
                    value,
                    null
            );
        }

        /*
         * "1 năm" không có từ khóa giới hạn:
         * xem như khoảng chính xác 1-1.
         */
        return new ExperienceNormalizationResult(
                value,
                value
        );
    }

    private List<Double> extractValues(
            String value,
            boolean monthUnit
    ) {
        List<Double> result = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(value);

        while (matcher.find()) {
            String numericToken = matcher.group(1)
                    .replace(',', '.');

            try {
                double parsedValue = Double.parseDouble(numericToken);

                if (monthUnit) {
                    parsedValue = parsedValue / 12.0;
                }

                result.add(round(parsedValue));
            } catch (NumberFormatException ignored) {
                // Unknown numeric token is skipped.
            }
        }

        return result;
    }

    private boolean meansNoExperience(String folded) {
        return containsAny(
                folded,
                "khong yeu cau kinh nghiem",
                "khong can kinh nghiem",
                "chua co kinh nghiem",
                "no experience required",
                "no experience",
                "fresher",
                "fresh graduate"
        );
    }

    private boolean hasRangeMarker(
            String original,
            String folded
    ) {
        return original.matches(".*\\d\\s*[-–—]\\s*\\d.*")
                || folded.contains(" den ")
                || folded.contains(" to ");
    }

    private boolean isUpperBoundOnly(String folded) {
        return containsAny(
                folded,
                "duoi ",
                "under ",
                "less than",
                "up to",
                "khong qua",
                "toi da"
        );
    }

    private boolean isLowerBoundOnly(
            String original,
            String folded
    ) {
        return containsAny(
                folded,
                "it nhat",
                "at least",
                "tu ",
                "tren ",
                "over ",
                "more than",
                "minimum",
                "toi thieu"
        ) || PLUS_SUFFIX_PATTERN.matcher(original).find();
    }

    private boolean containsAny(
            String value,
            String... candidates
    ) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }

        return false;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private ExperienceNormalizationResult unknown() {
        return new ExperienceNormalizationResult(
                null,
                null
        );
    }
}