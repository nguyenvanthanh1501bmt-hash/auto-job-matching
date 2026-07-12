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
public class SalaryNormalizer {

    private static final BigDecimal ONE_MILLION =
            BigDecimal.valueOf(1_000_000L);

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("(?<!\\d)(\\d+(?:[.,]\\d+)*)(?!\\d)");

    private static final Pattern GROUPED_THOUSANDS_PATTERN =
            Pattern.compile("\\d{1,3}(?:[.,]\\d{3})+");

    private static final Pattern PLUS_SUFFIX_PATTERN =
            Pattern.compile("\\d(?:[\\d.,]*)\\s*\\+");

    private final TextNormalizer textNormalizer;

    public SalaryNormalizationResult normalize(String salaryText) {
        String cleaned = textNormalizer.normalizeInline(salaryText);

        if (cleaned == null) {
            return emptyResult();
        }

        String folded = NormalizationTextSupport.fold(cleaned);
        String currency = detectCurrency(cleaned, folded);

        if (isNegotiable(folded)) {
            return new SalaryNormalizationResult(
                    null,
                    null,
                    currency
            );
        }

        BigDecimal multiplier = hasMillionUnit(folded)
                ? ONE_MILLION
                : BigDecimal.ONE;

        List<Long> amounts = extractAmounts(cleaned, multiplier);

        if (amounts.isEmpty()) {
            return new SalaryNormalizationResult(
                    null,
                    null,
                    currency
            );
        }

        if (amounts.size() >= 2) {
            long first = amounts.get(0);
            long second = amounts.get(1);

            return new SalaryNormalizationResult(
                    Math.min(first, second),
                    Math.max(first, second),
                    currency
            );
        }

        long amount = amounts.get(0);

        if (isUpperBoundOnly(folded)) {
            return new SalaryNormalizationResult(
                    null,
                    amount,
                    currency
            );
        }

        if (isLowerBoundOnly(cleaned, folded)) {
            return new SalaryNormalizationResult(
                    amount,
                    null,
                    currency
            );
        }

        /*
         * "20 triệu" được xem là mức salary cố định.
         */
        return new SalaryNormalizationResult(
                amount,
                amount,
                currency
        );
    }

    private List<Long> extractAmounts(
            String value,
            BigDecimal multiplier
    ) {
        List<Long> amounts = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(value);

        while (matcher.find()) {
            parseAmount(matcher.group(1), multiplier)
                    .ifPresent(amounts::add);
        }

        return amounts;
    }

    private java.util.Optional<Long> parseAmount(
            String token,
            BigDecimal multiplier
    ) {
        try {
            BigDecimal numericValue = parseNumericToken(token);

            long amount = numericValue
                    .multiply(multiplier)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

            return java.util.Optional.of(amount);
        } catch (ArithmeticException | NumberFormatException exception) {
            return java.util.Optional.empty();
        }
    }

    private BigDecimal parseNumericToken(String token) {
        String normalized = token.replace(" ", "");

        if (GROUPED_THOUSANDS_PATTERN.matcher(normalized).matches()) {
            normalized = normalized.replace(".", "")
                    .replace(",", "");

            return new BigDecimal(normalized);
        }

        boolean hasComma = normalized.contains(",");
        boolean hasDot = normalized.contains(".");

        if (hasComma && hasDot) {
            int lastComma = normalized.lastIndexOf(',');
            int lastDot = normalized.lastIndexOf('.');

            if (lastComma > lastDot) {
                normalized = normalized
                        .replace(".", "")
                        .replace(',', '.');
            } else {
                normalized = normalized.replace(",", "");
            }

            return new BigDecimal(normalized);
        }

        if (hasComma) {
            normalized = normalizeSingleSeparator(normalized, ',');
        } else if (hasDot) {
            normalized = normalizeSingleSeparator(normalized, '.');
        }

        return new BigDecimal(normalized);
    }

    private String normalizeSingleSeparator(
            String value,
            char separator
    ) {
        int separatorCount = countCharacter(value, separator);
        int lastSeparator = value.lastIndexOf(separator);
        int trailingDigits = value.length() - lastSeparator - 1;

        if (separatorCount > 1 || trailingDigits == 3) {
            return value.replace(String.valueOf(separator), "");
        }

        if (separator == ',') {
            return value.replace(',', '.');
        }

        return value;
    }

    private int countCharacter(String value, char character) {
        int count = 0;

        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == character) {
                count++;
            }
        }

        return count;
    }

    private String detectCurrency(
            String original,
            String folded
    ) {
        if (original.contains("$")
                || containsWord(folded, "usd")
                || folded.contains("us dollar")) {
            return "USD";
        }

        if (original.contains("₫")
                || containsWord(folded, "vnd")
                || folded.contains("trieu")) {
            return "VND";
        }

        return null;
    }

    private boolean isNegotiable(String folded) {
        return containsAny(
                folded,
                "thoa thuan",
                "canh tranh",
                "negotiable",
                "competitive salary",
                "luong thuong luong"
        );
    }

    private boolean hasMillionUnit(String folded) {
        return containsAny(
                folded,
                "trieu",
                "million"
        );
    }

    private boolean isUpperBoundOnly(String folded) {
        return containsAny(
                folded,
                "len den",
                "up to",
                "toi da",
                "duoi ",
                "under ",
                "less than",
                "khong qua"
        );
    }

    private boolean isLowerBoundOnly(
            String original,
            String folded
    ) {
        return containsAny(
                folded,
                "tu ",
                "tren ",
                "at least",
                "from ",
                "minimum",
                "toi thieu",
                "more than"
        ) || PLUS_SUFFIX_PATTERN.matcher(original).find();
    }

    private boolean containsWord(String text, String word) {
        return Pattern.compile(
                "(?:^|\\s)" + Pattern.quote(word) + "(?:\\s|$)"
        ).matcher(text).find();
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

    private SalaryNormalizationResult emptyResult() {
        return new SalaryNormalizationResult(
                null,
                null,
                null
        );
    }
}