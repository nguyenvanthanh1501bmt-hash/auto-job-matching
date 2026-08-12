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
public class SalaryNormalizer {

    private static final Pattern AMOUNT = Pattern.compile(
            "(?<!\\d)(\\d+(?:[.,]\\d+)*)(?!\\d)(?:\\s*([a-z]+))?"
    );
    private static final Pattern GROUPED =
            Pattern.compile("\\d{1,3}(?:[.,]\\d{3})+");

    private static final Pattern DASH_RANGE = Pattern.compile(
            "\\d(?:[\\d.,]*)\\s*[a-z]*\\s*[-–—]\\s*\\$?\\s*\\d"
    );

    private static final Pattern PLUS =
            Pattern.compile("\\d(?:[\\d.,]*)\\s*[a-z]*\\s*\\+");

    private final TextNormalizer textNormalizer;

    private final Map<String, Long> multipliers;
    private final List<CurrencyRule> currencies;

    private final Set<String> negotiable;
    private final Set<String> rangeWords;
    private final Set<String> upperBounds;
    private final Set<String> lowerBounds;

    private final long sharedMultiplierMin;
    private final long sharedMultiplierMaxUnscaledValue;

    public SalaryNormalizer(
            TextNormalizer textNormalizer,
            NormalizationTaxonomyProperties taxonomy
    ) {
        this.textNormalizer = textNormalizer;

        var config = taxonomy.getSalary();

        this.multipliers =
                buildMultipliers(config.getMultipliers());

        this.currencies =
                buildCurrencies(config.getCurrencies());

        this.negotiable =
                foldSet(config.getNegotiablePhrases());

        this.rangeWords =
                foldSet(config.getRangeWords());

        this.upperBounds =
                foldSet(config.getUpperBoundPhrases());

        this.lowerBounds =
                foldSet(config.getLowerBoundPhrases());

        this.sharedMultiplierMin =
                config.getSharedMultiplierMin();

        this.sharedMultiplierMaxUnscaledValue =
                config.getSharedMultiplierMaxUnscaledValue();
    }

    public SalaryNormalizationResult normalize(
            String salaryText
    ) {
        String cleaned =
                textNormalizer.normalizeInline(salaryText);

        if (cleaned == null) {
            return empty();
        }

        String folded =
                NormalizationTextSupport.fold(cleaned);

        CurrencyDetection currency =
                detectCurrency(cleaned, folded);

        if (currency.conflicting()) {
            return empty();
        }

        if (containsAny(folded, negotiable)) {
            return new SalaryNormalizationResult(
                    null,
                    null,
                    currency.code()
            );
        }

        List<AmountToken> amounts =
                extractAmounts(folded);

        if (amounts.isEmpty()) {
            return new SalaryNormalizationResult(
                    null,
                    null,
                    currency.code()
            );
        }

        if (hasRange(folded)
                && amounts.size() >= 2) {

            AmountToken first =
                    amounts.get(0);

            AmountToken second =
                    amounts.get(1);

            long firstMultiplier =
                    first.multiplier();

            long secondMultiplier =
                    second.multiplier();

            if (!first.explicitMultiplier()
                    && second.explicitMultiplier()
                    && shouldPropagate(
                    secondMultiplier,
                    first.value()
            )) {
                firstMultiplier =
                        secondMultiplier;
            }

            if (!second.explicitMultiplier()
                    && first.explicitMultiplier()
                    && shouldPropagate(
                    firstMultiplier,
                    second.value()
            )) {
                secondMultiplier =
                        firstMultiplier;
            }

            Long min =
                    toLong(
                            first.value(),
                            firstMultiplier
                    );

            Long max =
                    toLong(
                            second.value(),
                            secondMultiplier
                    );

            if (min == null || max == null) {
                return new SalaryNormalizationResult(
                        null,
                        null,
                        currency.code()
                );
            }

            return new SalaryNormalizationResult(
                    Math.min(min, max),
                    Math.max(min, max),
                    currency.code()
            );
        }

        AmountToken first =
                amounts.getFirst();

        Long amount =
                toLong(
                        first.value(),
                        first.multiplier()
                );

        if (amount == null) {
            return new SalaryNormalizationResult(
                    null,
                    null,
                    currency.code()
            );
        }

        if (containsAny(
                folded,
                upperBounds
        )) {
            return new SalaryNormalizationResult(
                    null,
                    amount,
                    currency.code()
            );
        }

        if (containsAny(
                folded,
                lowerBounds
        ) || PLUS.matcher(folded).find()) {

            return new SalaryNormalizationResult(
                    amount,
                    null,
                    currency.code()
            );
        }

        if (amounts.size() > 1) {
            return new SalaryNormalizationResult(
                    null,
                    null,
                    currency.code()
            );
        }

        return new SalaryNormalizationResult(
                amount,
                amount,
                currency.code()
        );
    }

    private List<AmountToken> extractAmounts(
            String folded
    ) {
        List<AmountToken> result =
                new ArrayList<>();

        Matcher matcher =
                AMOUNT.matcher(folded);

        while (matcher.find()) {
            try {
                BigDecimal value =
                        parseNumber(
                                matcher.group(1)
                        );

                Long multiplier =
                        multipliers.get(
                                fold(
                                        matcher.group(2)
                                )
                        );

                result.add(
                        new AmountToken(
                                value,
                                multiplier == null
                                        ? 1L
                                        : multiplier,
                                multiplier != null
                        )
                );
            } catch (NumberFormatException ignored) {
                // Ignore malformed number.
            }
        }

        return result;
    }

    private boolean shouldPropagate(
            long multiplier,
            BigDecimal value
    ) {
        return multiplier >= sharedMultiplierMin
                && value.compareTo(
                BigDecimal.valueOf(
                        sharedMultiplierMaxUnscaledValue
                )
        ) < 0;
    }

    private Long toLong(
            BigDecimal value,
            long multiplier
    ) {
        try {
            return value
                    .multiply(
                            BigDecimal.valueOf(multiplier)
                    )
                    .setScale(
                            0,
                            RoundingMode.HALF_UP
                    )
                    .longValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private BigDecimal parseNumber(
            String token
    ) {
        String value =
                token.replace(" ", "");

        if (GROUPED.matcher(value).matches()) {
            return new BigDecimal(
                    value
                            .replace(".", "")
                            .replace(",", "")
            );
        }

        boolean comma =
                value.contains(",");

        boolean dot =
                value.contains(".");

        if (comma && dot) {
            value =
                    value.lastIndexOf(',')
                            > value.lastIndexOf('.')
                            ? value
                            .replace(".", "")
                            .replace(',', '.')
                            : value.replace(",", "");
        } else if (comma) {
            value =
                    normalizeSeparator(
                            value,
                            ','
                    );
        } else if (dot) {
            value =
                    normalizeSeparator(
                            value,
                            '.'
                    );
        }

        return new BigDecimal(value);
    }

    private String normalizeSeparator(
            String value,
            char separator
    ) {
        long count =
                value
                        .chars()
                        .filter(ch ->
                                ch == separator
                        )
                        .count();

        int trailing =
                value.length()
                        - value.lastIndexOf(separator)
                        - 1;

        if (count > 1
                || trailing == 3) {

            return value.replace(
                    String.valueOf(separator),
                    ""
            );
        }

        return separator == ','
                ? value.replace(',', '.')
                : value;
    }

    private CurrencyDetection detectCurrency(
            String original,
            String folded
    ) {
        Set<String> matched =
                new LinkedHashSet<>();

        for (CurrencyRule rule : currencies) {
            boolean found =
                    rule.markers()
                            .stream()
                            .filter(marker ->
                                    marker != null
                                            && !marker.isBlank()
                            )
                            .anyMatch(
                                    original::contains
                            )
                            || rule.phrases()
                            .stream()
                            .anyMatch(
                                    phrase ->
                                            contains(
                                                    folded,
                                                    phrase,
                                                    true
                                            )
                            )
                            || rule.unitAliases()
                            .stream()
                            .anyMatch(
                                    alias ->
                                            contains(
                                                    folded,
                                                    alias,
                                                    false
                                            )
                            );

            if (found) {
                matched.add(
                        rule.code()
                );
            }
        }

        if (matched.size() > 1) {
            return new CurrencyDetection(
                    null,
                    true
            );
        }

        return new CurrencyDetection(
                matched
                        .stream()
                        .findFirst()
                        .orElse(null),
                false
        );
    }

    private boolean hasRange(
            String folded
    ) {
        return containsAny(
                folded,
                rangeWords
        )
                || DASH_RANGE
                .matcher(folded)
                .find();
    }

    private Map<String, Long> buildMultipliers(
            List<NormalizationTaxonomyProperties.SalaryMultiplierRule>
                    rules
    ) {
        Map<String, Long> result =
                new LinkedHashMap<>();

        for (var rule : rules) {
            for (String alias
                    : rule.getAliases()) {

                result.put(
                        fold(alias),
                        rule.getMultiplier()
                );
            }
        }

        return Map.copyOf(result);
    }

    private List<CurrencyRule> buildCurrencies(
            List<NormalizationTaxonomyProperties.SalaryCurrencyRule>
                    rules
    ) {
        return rules
                .stream()
                .map(rule ->
                        new CurrencyRule(
                                rule.getCode(),
                                List.copyOf(
                                        rule.getOriginalMarkers()
                                ),
                                foldSet(
                                        rule.getFoldedPhrases()
                                ),
                                foldSet(
                                        rule.getInferredUnitAliases()
                                )
                        )
                )
                .toList();
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
                                contains(
                                        value,
                                        phrase,
                                        true
                                )
                );
    }

    private boolean contains(
            String value,
            String candidate,
            boolean digitBlocksLeft
    ) {
        int from = 0;

        while (from
                <= value.length()
                - candidate.length()) {

            int index =
                    value.indexOf(
                            candidate,
                            from
                    );

            if (index < 0) {
                return false;
            }

            int end =
                    index
                            + candidate.length();

            char left =
                    index == 0
                            ? '\0'
                            : value.charAt(
                            index - 1
                    );

            boolean leftOk =
                    index == 0
                            || (
                            !Character.isLetter(left)
                                    && (
                                    !digitBlocksLeft
                                            || !Character.isDigit(
                                            left
                                    )
                            )
                    );

            boolean rightOk =
                    end == value.length()
                            || !Character.isLetterOrDigit(
                            value.charAt(end)
                    );

            if (leftOk && rightOk) {
                return true;
            }

            from =
                    index + 1;
        }

        return false;
    }

    private SalaryNormalizationResult empty() {
        return new SalaryNormalizationResult(
                null,
                null,
                null
        );
    }

    private record AmountToken(
            BigDecimal value,
            long multiplier,
            boolean explicitMultiplier
    ) {
    }

    private record CurrencyDetection(
            String code,
            boolean conflicting
    ) {
    }

    private record CurrencyRule(
            String code,
            List<String> markers,
            Set<String> phrases,
            Set<String> unitAliases
    ) {
    }
}