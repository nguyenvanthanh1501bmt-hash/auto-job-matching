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

    /**
     * Chia requirements/description thành các segment
     * tương đối độc lập trước khi scan experience.
     *
     * Không split bằng dấu phẩy vì:
     *
     * 1,5 năm
     *
     * là decimal hợp lệ.
     */
    private static final Pattern CONTEXT_SEGMENT_SPLIT =
            Pattern.compile(
                    "(?:\\r?\\n)+"
                            + "|[;•●▪◦]+"
                            + "|(?<=[.!?])\\s+"
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
    private final Set<String> contextPhrases;

    private final int contextWindowChars;

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

        this.contextPhrases =
                foldSet(
                        config.getContextPhrases()
                );

        this.contextWindowChars =
                config.getContextWindowChars();
    }

    /**
     * Multi-source normalization.
     *
     * Priority:
     *
     * 1. rawJob.experienceText
     * 2. requirementsText
     * 3. descriptionText
     *
     * Requirements / description chỉ được dùng
     * thông qua contextual fallback.
     */
    public ExperienceNormalizationResult normalize(
            String experienceText,
            String requirementsText,
            String descriptionText
    ) {
        /*
         * Explicit crawler field luôn có priority cao nhất.
         */
        ExperienceNormalizationResult explicit =
                normalize(
                        experienceText
                );

        if (explicit.known()) {
            return explicit;
        }

        /*
         * Requirements thường chứa yêu cầu kinh nghiệm
         * rõ hơn description.
         */
        ExperienceNormalizationResult requirements =
                normalizeContextualText(
                        requirementsText
                );

        if (requirements.known()) {
            return requirements;
        }

        /*
         * Description chỉ là fallback cuối.
         */
        return normalizeContextualText(
                descriptionText
        );
    }

    /**
     * Normalize một expression tương đối sạch.
     *
     * Ví dụ:
     *
     * 2 - 4 years
     * at least 3 years
     * dưới 1 năm
     * 18 months
     * 1 year 6 months
     */
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

        /*
         * Không yêu cầu kinh nghiệm.
         */
        if (containsAny(
                folded,
                noExperience
        )) {
            return new ExperienceNormalizationResult(
                    0.0,
                    null
            );
        }

        /*
         * Range:
         *
         * 2 - 4 years
         * 2 đến 4 năm
         * 6 months - 1 year
         */
        ExperienceNormalizationResult range =
                parseRange(
                        folded
                );

        if (range != null) {
            return range;
        }

        /*
         * Single duration hoặc compound duration:
         *
         * 3 years
         * 18 months
         * 1 year 6 months
         */
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

        /*
         * Upper bound:
         *
         * under 1 year
         * dưới 1 năm
         * up to 2 years
         */
        if (containsAny(
                folded,
                upperBounds
        )) {
            return new ExperienceNormalizationResult(
                    0.0,
                    value
            );
        }

        /*
         * Lower bound:
         *
         * at least 3 years
         * từ 2 năm
         * 3+ years
         */
        if (containsAny(
                folded,
                lowerBounds
        )
                || PLUS
                .matcher(folded)
                .find()) {

            return new ExperienceNormalizationResult(
                    value,
                    null
            );
        }

        /*
         * Giữ behavior hiện tại của repo:
         *
         * "18 months"
         *
         * thường được xem là minimum requirement
         * chứ không phải exact professional duration.
         */
        if (duration.componentCount() == 1
                && duration.singleUnit()
                == NormalizationTaxonomyProperties
                .ExperienceUnit.MONTH) {

            return new ExperienceNormalizationResult(
                    value,
                    null
            );
        }

        /*
         * Exact duration.
         *
         * "1 year"
         * => 1..1
         */
        return new ExperienceNormalizationResult(
                value,
                value
        );
    }

    /**
     * Extract experience từ long-form text.
     *
     * Quan trọng:
     *
     * KHÔNG:
     *
     * normalize(requirementsText)
     *
     * vì requirements có thể chứa:
     *
     * 2025
     * 20 triệu
     * 5 người
     * 3 project
     *
     * Ta chỉ parse segment có context experience.
     */
    private ExperienceNormalizationResult
    normalizeContextualText(
            String sourceText
    ) {
        String cleaned =
                textNormalizer.normalizeMultiline(
                        sourceText
                );

        if (cleaned == null
                || contextPhrases.isEmpty()) {

            return unknown();
        }

        String[] segments =
                CONTEXT_SEGMENT_SPLIT.split(
                        cleaned
                );

        for (String segment : segments) {

            String foldedSegment =
                    fold(
                            segment
                    );

            if (foldedSegment.isBlank()) {
                continue;
            }

            /*
             * "Không yêu cầu kinh nghiệm"
             * tự bản thân đã là tín hiệu mạnh.
             */
            if (containsAny(
                    foldedSegment,
                    noExperience
            )) {

                ExperienceNormalizationResult
                        noExperienceResult =
                        normalize(
                                foldedSegment
                        );

                if (noExperienceResult.known()) {
                    return noExperienceResult;
                }
            }

            /*
             * Có thể một segment chứa nhiều context phrase.
             *
             * Ví dụ:
             *
             * "At least 3 years of relevant experience..."
             */
            for (String contextPhrase
                    : contextPhrases) {

                int from = 0;

                while (
                        from
                                <= foldedSegment.length()
                                - contextPhrase.length()
                ) {

                    int index =
                            findPhraseFrom(
                                    foldedSegment,
                                    contextPhrase,
                                    from
                            );

                    if (index < 0) {
                        break;
                    }

                    /*
                     * Chỉ lấy local window.
                     */
                    int start =
                            Math.max(
                                    0,
                                    index
                                            - contextWindowChars
                            );

                    int end =
                            Math.min(
                                    foldedSegment.length(),
                                    index
                                            + contextPhrase.length()
                                            + contextWindowChars
                            );

                    String fragment =
                            foldedSegment.substring(
                                    start,
                                    end
                            );

                    ExperienceNormalizationResult result =
                            normalize(
                                    fragment
                            );

                    if (result.known()) {
                        return result;
                    }

                    from =
                            index
                                    + contextPhrase.length();
                }
            }
        }

        return unknown();
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

            /*
             * 2 - 4 years
             *
             * left không có unit.
             * right có YEAR.
             *
             * => dùng YEAR cho left.
             */
            var leftDefault =
                    leftExplicit == null
                            ? singleUnit(right)
                            : null;

            /*
             * 2 years - 4
             *
             * right không có unit.
             * => dùng unit của left.
             */
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

        /*
         * Symbol separators.
         */
        for (
                int index = 0;
                index < value.length();
                index++
        ) {

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

        /*
         * Word separators:
         *
         * to
         * den
         */
        for (String word : rangeWords) {

            int from = 0;

            while (
                    from
                            <= value.length()
                            - word.length()
            ) {

                int index =
                        findPhraseFrom(
                                value,
                                word,
                                from
                        );

                if (index < 0) {
                    break;
                }

                result.add(
                        new RangeSeparator(
                                index,
                                index
                                        + word.length()
                        )
                );

                from =
                        index
                                + word.length();
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

    /**
     * Parse:
     *
     * 3 years
     * 18 months
     * 1 year 6 months
     */
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

        /*
         * Có duration explicit.
         */
        if (!components.isEmpty()) {

            /*
             * Nếu fragment có thêm numeric token
             * không gắn với experience unit thì không đoán.
             *
             * Ví dụ:
             *
             * "2 years, salary 20 million"
             *
             * => reject fragment.
             *
             * Context scanner sẽ tránh trường hợp này
             * bằng local window.
             */
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

        /*
         * Không có unit explicit.
         *
         * Chỉ cho phép nếu caller đã infer được
         * default unit từ phía bên kia của range.
         */
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

        if (number == null) {
            return null;
        }

        return new ParsedDuration(
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
                ? component.value()
                / 12.0d
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
            >
    buildUnits(
            List<
                    NormalizationTaxonomyProperties
                            .ExperienceUnitRule
                    > rules
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

        return Map.copyOf(
                result
        );
    }

    private Set<String> foldSet(
            Iterable<String> values
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        if (values != null) {

            for (String value : values) {

                String folded =
                        fold(
                                value
                        );

                if (!folded.isBlank()) {

                    result.add(
                            folded
                    );
                }
            }
        }

        return Set.copyOf(
                result
        );
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
        for (String phrase : phrases) {

            if (findPhrase(
                    value,
                    phrase
            ) >= 0) {

                return true;
            }
        }

        return false;
    }

    private int findPhrase(
            String value,
            String phrase
    ) {
        return findPhraseFrom(
                value,
                phrase,
                0
        );
    }

    private int findPhraseFrom(
            String value,
            String phrase,
            int fromIndex
    ) {
        int from =
                Math.max(
                        0,
                        fromIndex
                );

        while (
                from
                        <= value.length()
                        - phrase.length()
        ) {

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
                            || !Character
                            .isLetterOrDigit(
                                    value.charAt(
                                            index - 1
                                    )
                            );

            boolean right =
                    end == value.length()
                            || !Character
                            .isLetterOrDigit(
                                    value.charAt(
                                            end
                                    )
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