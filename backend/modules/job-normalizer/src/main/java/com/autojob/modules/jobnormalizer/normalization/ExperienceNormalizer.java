package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ExperienceNormalizer {

    private static final Pattern NUMBER =
            Pattern.compile(
                    "(?<!\\d)(\\d+(?:[.,]\\d+)?)(?!\\d)"
            );

    private static final Pattern DURATION =
            Pattern.compile(
                    "(?<!\\d)(\\d+(?:[.,]\\d+)?)"
                            + "\\s*\\+?\\s*([a-z]+)(?![a-z])"
            );

    private static final Pattern PLUS =
            Pattern.compile(
                    "\\d(?:[\\d.,]*)\\s*\\+"
            );

    private final TextNormalizer textNormalizer;

    private final Map<
            String,
            NormalizationTaxonomyProperties.ExperienceUnit
            > units;

    private final Set<String> noExperience;
    private final Set<String> rangeWords;
    private final Set<String> upperBounds;
    private final Set<String> lowerBounds;

    public ExperienceNormalizer(
            TextNormalizer textNormalizer,
            NormalizationTaxonomyProperties taxonomy
    ) {
        this.textNormalizer =
                textNormalizer;

        var config =
                taxonomy.getExperience();

        this.units =
                buildUnits(
                        config.getUnits()
                );

        this.noExperience =
                foldSet(
                        config.getNoExperiencePhrases()
                );

        this.rangeWords =
                foldSet(
                        config.getRangeWords()
                );

        this.upperBounds =
                foldSet(
                        config.getUpperBoundPhrases()
                );

        this.lowerBounds =
                foldSet(
                        config.getLowerBoundPhrases()
                );
    }

    public ExperienceNormalizationResult normalize(
            String experienceText
    ) {
        String cleaned =
                textNormalizer.normalizeInline(
                        experienceText
                );

        if (cleaned == null) {
            return unknown();
        }

        String folded =
                NormalizationTextSupport.fold(
                        cleaned
                );

        if (containsAny(
                folded,
                noExperience
        )) {
            return new ExperienceNormalizationResult(
                    0.0,
                    null
            );
        }

        ExperienceNormalizationResult range =
                parseRange(
                        folded
                );

        if (range != null) {
            return range;
        }

        ParsedDuration duration =
                parseDuration(
                        folded,
                        null
                );

        if (duration == null) {
            return unknown();
        }

        double value =
                duration.value();

        if (containsAny(
                folded,
                upperBounds
        )) {
            return new ExperienceNormalizationResult(
                    0.0,
                    value
            );
        }

        if (containsAny(
                folded,
                lowerBounds
        ) || PLUS.matcher(folded).find()) {

            return new ExperienceNormalizationResult(
                    value,
                    null
            );
        }

        if (duration.componentCount() == 1
                && duration.singleUnit()
                == NormalizationTaxonomyProperties
                .ExperienceUnit.MONTH) {

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

    private ExperienceNormalizationResult parseRange(
            String value
    ) {
        for (RangeSeparator separator
                : findRanges(value)) {

            String left =
                    value.substring(
                            0,
                            separator.start()
                    );

            String right =
                    value.substring(
                            separator.end()
                    );

            if (!containsNumber(left)
                    || !containsNumber(right)) {
                continue;
            }

            ParsedDuration leftExplicit =
                    parseDuration(
                            left,
                            null
                    );

            ParsedDuration rightExplicit =
                    parseDuration(
                            right,
                            null
                    );

            var leftDefault =
                    leftExplicit == null
                            ? singleUnit(right)
                            : null;

            var rightDefault =
                    rightExplicit == null
                            ? singleUnit(left)
                            : null;

            ParsedDuration leftDuration =
                    leftExplicit != null
                            ? leftExplicit
                            : parseDuration(
                            left,
                            leftDefault
                    );

            ParsedDuration rightDuration =
                    rightExplicit != null
                            ? rightExplicit
                            : parseDuration(
                            right,
                            rightDefault
                    );

            if (leftDuration == null
                    || rightDuration == null) {
                continue;
            }

            return new ExperienceNormalizationResult(
                    round(
                            Math.min(
                                    leftDuration.value(),
                                    rightDuration.value()
                            )
                    ),
                    round(
                            Math.max(
                                    leftDuration.value(),
                                    rightDuration.value()
                            )
                    )
            );
        }

        return null;
    }

    private List<RangeSeparator> findRanges(
            String value
    ) {
        List<RangeSeparator> result =
                new ArrayList<>();

        for (int index = 0;
             index < value.length();
             index++) {

            char character =
                    value.charAt(index);

            if (character == '-'
                    || character == '–'
                    || character == '—') {

                result.add(
                        new RangeSeparator(
                                index,
                                index + 1
                        )
                );
            }
        }

        for (String word : rangeWords) {
            int index =
                    findPhrase(
                            value,
                            word
                    );

            if (index >= 0) {
                result.add(
                        new RangeSeparator(
                                index,
                                index + word.length()
                        )
                );
            }
        }

        result.sort(
                (left, right) ->
                        Integer.compare(
                                left.start(),
                                right.start()
                        )
        );

        return result;
    }

    private ParsedDuration parseDuration(
            String value,
            NormalizationTaxonomyProperties.ExperienceUnit
                    defaultUnit
    ) {
        Matcher matcher =
                DURATION.matcher(
                        value
                );

        List<DurationComponent> components =
                new ArrayList<>();

        while (matcher.find()) {
            Double number =
                    parseNumber(
                            matcher.group(1)
                    );

            var unit =
                    units.get(
                            fold(
                                    matcher.group(2)
                            )
                    );

            if (number == null
                    || unit == null) {
                return null;
            }

            components.add(
                    new DurationComponent(
                            number,
                            unit
                    )
            );
        }

        int numberCount =
                countNumbers(
                        value
                );

        if (!components.isEmpty()) {
            if (numberCount
                    != components.size()) {
                return null;
            }

            double years =
                    components
                            .stream()
                            .mapToDouble(
                                    this::toYears
                            )
                            .sum();

            var singleUnit =
                    components
                            .stream()
                            .map(
                                    DurationComponent::unit
                            )
                            .distinct()
                            .count() == 1
                            ? components
                            .getFirst()
                            .unit()
                            : null;

            return new ParsedDuration(
                    round(years),
                    components.size(),
                    singleUnit
            );
        }

        if (defaultUnit == null
                || numberCount != 1) {
            return null;
        }

        Matcher numberMatcher =
                NUMBER.matcher(
                        value
                );

        if (!numberMatcher.find()) {
            return null;
        }

        Double number =
                parseNumber(
                        numberMatcher.group(1)
                );

        return number == null
                ? null
                : new ParsedDuration(
                round(
                        toYears(
                                new DurationComponent(
                                        number,
                                        defaultUnit
                                )
                        )
                ),
                1,
                defaultUnit
        );
    }

    private NormalizationTaxonomyProperties.ExperienceUnit
    singleUnit(
            String value
    ) {
        Matcher matcher =
                DURATION.matcher(
                        value
                );

        NormalizationTaxonomyProperties.ExperienceUnit
                unit = null;

        int count = 0;

        while (matcher.find()) {
            var current =
                    units.get(
                            fold(
                                    matcher.group(2)
                            )
                    );

            if (current == null
                    || (
                    unit != null
                            && unit != current
            )) {
                return null;
            }

            unit =
                    current;

            count++;
        }

        return count > 0
                && countNumbers(value) == count
                ? unit
                : null;
    }

    private double toYears(
            DurationComponent component
    ) {
        return component.unit()
                == NormalizationTaxonomyProperties
                .ExperienceUnit.MONTH
                ? component.value() / 12.0
                : component.value();
    }

    private Double parseNumber(
            String value
    ) {
        try {
            return Double.parseDouble(
                    value.replace(
                            ',',
                            '.'
                    )
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int countNumbers(
            String value
    ) {
        int count = 0;

        Matcher matcher =
                NUMBER.matcher(
                        value
                );

        while (matcher.find()) {
            count++;
        }

        return count;
    }

    private boolean containsNumber(
            String value
    ) {
        return NUMBER
                .matcher(value)
                .find();
    }

    private Map<
            String,
            NormalizationTaxonomyProperties.ExperienceUnit
            > buildUnits(
            List<NormalizationTaxonomyProperties.ExperienceUnitRule>
                    rules
    ) {
        Map<
                String,
                NormalizationTaxonomyProperties.ExperienceUnit
                > result =
                new LinkedHashMap<>();

        for (var rule : rules) {
            for (String alias
                    : rule.getAliases()) {

                result.put(
                        fold(alias),
                        rule.getUnit()
                );
            }
        }

        return Map.copyOf(result);
    }

    private Set<String> foldSet(
            Iterable<String> values
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        if (values != null) {
            for (String value : values) {
                String folded =
                        fold(value);

                if (!folded.isBlank()) {
                    result.add(folded);
                }
            }
        }

        return Set.copyOf(result);
    }

    private String fold(
            String value
    ) {
        return value == null
                ? ""
                : NormalizationTextSupport.fold(
                value
        );
    }

    private boolean containsAny(
            String value,
            Set<String> phrases
    ) {
        return phrases
                .stream()
                .anyMatch(
                        phrase ->
                                findPhrase(
                                        value,
                                        phrase
                                ) >= 0
                );
    }

    private int findPhrase(
            String value,
            String phrase
    ) {
        int from = 0;

        while (from
                <= value.length()
                - phrase.length()) {

            int index =
                    value.indexOf(
                            phrase,
                            from
                    );

            if (index < 0) {
                return -1;
            }

            int end =
                    index
                            + phrase.length();

            boolean left =
                    index == 0
                            || !Character.isLetterOrDigit(
                            value.charAt(
                                    index - 1
                            )
                    );

            boolean right =
                    end == value.length()
                            || !Character.isLetterOrDigit(
                            value.charAt(end)
                    );

            if (left && right) {
                return index;
            }

            from =
                    index + 1;
        }

        return -1;
    }

    private double round(
            double value
    ) {
        return BigDecimal
                .valueOf(value)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }

    private ExperienceNormalizationResult unknown() {
        return new ExperienceNormalizationResult(
                null,
                null
        );
    }

    private record RangeSeparator(
            int start,
            int end
    ) {
    }

    private record DurationComponent(
            double value,
            NormalizationTaxonomyProperties.ExperienceUnit unit
    ) {
    }

    private record ParsedDuration(
            double value,
            int componentCount,
            NormalizationTaxonomyProperties.ExperienceUnit singleUnit
    ) {
    }
}