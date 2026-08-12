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

    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d+(?:[.,]\\d+)?)(?!\\d)"
    );

    private static final Pattern DURATION_COMPONENT_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d+(?:[.,]\\d+)?)\\s*\\+?\\s*"
                    + "(years?|yrs?|yr|nam|months?|mos?|mo|thang)\\b"
    );

    private static final Pattern RANGE_SEPARATOR_PATTERN = Pattern.compile(
            "\\s*(?:[-–—]|\\bden\\b|\\bto\\b)\\s*"
    );

    private static final Pattern PLUS_SUFFIX_PATTERN = Pattern.compile(
            "\\d(?:[\\d.,]*)\\s*\\+"
    );

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

        ExperienceNormalizationResult rangeResult = parseRange(folded);

        if (rangeResult != null) {
            return rangeResult;
        }

        ParsedDuration duration = parseDuration(folded, null);

        if (duration == null) {
            return unknown();
        }

        double value = duration.value();

        if (isUpperBoundOnly(folded)) {
            return new ExperienceNormalizationResult(
                    0.0,
                    value
            );
        }

        if (isLowerBoundOnly(folded)) {
            return new ExperienceNormalizationResult(
                    value,
                    null
            );
        }

        /*
         * Giữ backward-compatible behavior cho một duration chỉ có tháng:
         * "24 months" được hiểu là minimum 2 năm.
         *
         * Mixed units như "1 year 6 months" là một duration hoàn chỉnh,
         * vì vậy được normalize thành exact 1.5 - 1.5 năm.
         */
        if (duration.componentCount() == 1
                && duration.singleUnit() == DurationUnit.MONTH) {
            return new ExperienceNormalizationResult(
                    value,
                    null
            );
        }

        return new ExperienceNormalizationResult(
                value,
                value
        );
    }

    private ExperienceNormalizationResult parseRange(String folded) {
        Matcher separatorMatcher = RANGE_SEPARATOR_PATTERN.matcher(folded);

        while (separatorMatcher.find()) {
            String left = folded.substring(0, separatorMatcher.start());
            String right = folded.substring(separatorMatcher.end());

            if (!containsNumber(left) || !containsNumber(right)) {
                continue;
            }

            ParsedDuration leftExplicit = parseDuration(left, null);
            ParsedDuration rightExplicit = parseDuration(right, null);

            DurationUnit leftDefault = null;
            DurationUnit rightDefault = null;

            if (leftExplicit == null) {
                leftDefault = singleExplicitUnit(right);
            }

            if (rightExplicit == null) {
                rightDefault = singleExplicitUnit(left);
            }

            ParsedDuration leftDuration = leftExplicit != null
                    ? leftExplicit
                    : parseDuration(left, leftDefault);

            ParsedDuration rightDuration = rightExplicit != null
                    ? rightExplicit
                    : parseDuration(right, rightDefault);

            if (leftDuration == null || rightDuration == null) {
                continue;
            }

            double first = leftDuration.value();
            double second = rightDuration.value();

            return new ExperienceNormalizationResult(
                    round(Math.min(first, second)),
                    round(Math.max(first, second))
            );
        }

        return null;
    }

    private ParsedDuration parseDuration(
            String value,
            DurationUnit defaultUnit
    ) {
        Matcher componentMatcher = DURATION_COMPONENT_PATTERN.matcher(value);
        List<DurationComponent> components = new ArrayList<>();

        while (componentMatcher.find()) {
            Double numericValue = parseNumber(componentMatcher.group(1));

            if (numericValue == null) {
                return null;
            }

            DurationUnit unit = parseUnit(componentMatcher.group(2));

            if (unit == null) {
                return null;
            }

            components.add(new DurationComponent(
                    numericValue,
                    unit
            ));
        }

        int numberCount = countNumbers(value);

        if (!components.isEmpty()) {
            /*
             * Nếu còn numeric token không gắn unit thì input có thể chứa
             * dữ liệu khác ngoài kinh nghiệm. Không đoán trong trường hợp đó.
             */
            if (numberCount != components.size()) {
                return null;
            }

            double years = components.stream()
                    .mapToDouble(this::toYears)
                    .sum();

            DurationUnit singleUnit = components.stream()
                    .map(DurationComponent::unit)
                    .distinct()
                    .count() == 1
                    ? components.getFirst().unit()
                    : null;

            return new ParsedDuration(
                    round(years),
                    components.size(),
                    singleUnit
            );
        }

        if (defaultUnit == null || numberCount != 1) {
            return null;
        }

        Matcher numberMatcher = NUMBER_PATTERN.matcher(value);

        if (!numberMatcher.find()) {
            return null;
        }

        Double numericValue = parseNumber(numberMatcher.group(1));

        if (numericValue == null) {
            return null;
        }

        return new ParsedDuration(
                round(toYears(new DurationComponent(
                        numericValue,
                        defaultUnit
                ))),
                1,
                defaultUnit
        );
    }

    private DurationUnit singleExplicitUnit(String value) {
        Matcher matcher = DURATION_COMPONENT_PATTERN.matcher(value);
        DurationUnit unit = null;
        int matchedComponents = 0;

        while (matcher.find()) {
            DurationUnit currentUnit = parseUnit(matcher.group(2));

            if (currentUnit == null) {
                return null;
            }

            if (unit != null && unit != currentUnit) {
                return null;
            }

            unit = currentUnit;
            matchedComponents++;
        }

        if (matchedComponents == 0
                || countNumbers(value) != matchedComponents) {
            return null;
        }

        return unit;
    }

    private DurationUnit parseUnit(String value) {
        if (value == null) {
            return null;
        }

        if (value.startsWith("year")
                || value.startsWith("yr")
                || value.equals("nam")) {
            return DurationUnit.YEAR;
        }

        if (value.startsWith("month")
                || value.startsWith("mo")
                || value.equals("thang")) {
            return DurationUnit.MONTH;
        }

        return null;
    }

    private double toYears(DurationComponent component) {
        if (component.unit() == DurationUnit.MONTH) {
            return component.value() / 12.0;
        }

        return component.value();
    }

    private Double parseNumber(String value) {
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int countNumbers(String value) {
        int count = 0;
        Matcher matcher = NUMBER_PATTERN.matcher(value);

        while (matcher.find()) {
            count++;
        }

        return count;
    }

    private boolean containsNumber(String value) {
        return NUMBER_PATTERN.matcher(value).find();
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

    private boolean isLowerBoundOnly(String folded) {
        return containsAny(
                folded,
                "it nhat",
                "at least",
                "tu ",
                "from ",
                "tren ",
                "over ",
                "more than",
                "minimum",
                "toi thieu"
        ) || PLUS_SUFFIX_PATTERN.matcher(folded).find();
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

    private enum DurationUnit {
        YEAR,
        MONTH
    }

    private record DurationComponent(
            double value,
            DurationUnit unit
    ) {
    }

    private record ParsedDuration(
            double value,
            int componentCount,
            DurationUnit singleUnit
    ) {
    }
}