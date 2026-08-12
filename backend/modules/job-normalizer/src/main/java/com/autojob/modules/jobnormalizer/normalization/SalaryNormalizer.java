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

    private static final BigDecimal ONE_THOUSAND =
            BigDecimal.valueOf(1_000L);

    private static final BigDecimal ONE_MILLION =
            BigDecimal.valueOf(1_000_000L);

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(\\$\\s*)?(?<!\\d)(\\d+(?:[.,]\\d+)*)(?!\\d)"
                    + "(?:\\s*(k|thousand|trieu|tr|million)(?![a-z]))?"
    );

    private static final Pattern GROUPED_THOUSANDS_PATTERN =
            Pattern.compile("\\d{1,3}(?:[.,]\\d{3})+");

    private static final Pattern PLUS_SUFFIX_PATTERN = Pattern.compile(
            "\\d(?:[\\d.,]*)\\s*"
                    + "(?:k|thousand|trieu|tr|million)?\\s*\\+"
    );

    private static final Pattern VND_CODE_PATTERN = Pattern.compile(
            "(?<![a-z0-9])vnd(?![a-z0-9])"
    );

    private static final Pattern USD_CODE_PATTERN = Pattern.compile(
            "(?<![a-z0-9])usd(?![a-z0-9])"
    );

    private static final Pattern VIETNAMESE_MILLION_PATTERN = Pattern.compile(
            "(?<![a-z])(?:tr|trieu)\\b"
    );

    private final TextNormalizer textNormalizer;

    public SalaryNormalizationResult normalize(String salaryText) {
        String cleaned = textNormalizer.normalizeInline(salaryText);

        if (cleaned == null) {
            return emptyResult();
        }

        String folded = NormalizationTextSupport.fold(cleaned);
        CurrencyDetection currencyDetection = detectCurrency(
                cleaned,
                folded
        );

        if (currencyDetection.conflicting()) {
            return emptyResult();
        }

        String currency = currencyDetection.currency();

        if (isNegotiable(folded)) {
            return new SalaryNormalizationResult(
                    null,
                    null,
                    currency
            );
        }

        List<AmountToken> amountTokens = extractAmountTokens(folded);

        if (amountTokens.isEmpty()) {
            return new SalaryNormalizationResult(
                    null,
                    null,
                    currency
            );
        }

        boolean range = hasRangeMarker(folded);

        if (range && amountTokens.size() >= 2) {
            AmountToken first = amountTokens.get(0);
            AmountToken second = amountTokens.get(1);

            SharedMultipliers sharedMultipliers = resolveSharedMultipliers(
                    first,
                    second
            );

            Long firstAmount = toLongAmount(
                    first.numericValue(),
                    sharedMultipliers.firstMultiplier()
            );

            Long secondAmount = toLongAmount(
                    second.numericValue(),
                    sharedMultipliers.secondMultiplier()
            );

            if (firstAmount == null || secondAmount == null) {
                return new SalaryNormalizationResult(
                        null,
                        null,
                        currency
                );
            }

            return new SalaryNormalizationResult(
                    Math.min(firstAmount, secondAmount),
                    Math.max(firstAmount, secondAmount),
                    currency
            );
        }

        Long amount = toLongAmount(
                amountTokens.getFirst().numericValue(),
                amountTokens.getFirst().multiplier()
        );

        if (amount == null) {
            return new SalaryNormalizationResult(
                    null,
                    null,
                    currency
            );
        }

        if (isUpperBoundOnly(folded)) {
            return new SalaryNormalizationResult(
                    null,
                    amount,
                    currency
            );
        }

        if (isLowerBoundOnly(folded)) {
            return new SalaryNormalizationResult(
                    amount,
                    null,
                    currency
            );
        }

        /*
         * Có nhiều số nhưng không có range/lower/upper marker rõ ràng có thể
         * là salary + bonus/allowance. Không đoán min/max trong trường hợp đó.
         */
        if (amountTokens.size() > 1) {
            return new SalaryNormalizationResult(
                    null,
                    null,
                    currency
            );
        }

        return new SalaryNormalizationResult(
                amount,
                amount,
                currency
        );
    }

    private List<AmountToken> extractAmountTokens(String folded) {
        List<AmountToken> tokens = new ArrayList<>();
        Matcher matcher = AMOUNT_PATTERN.matcher(folded);

        while (matcher.find()) {
            try {
                BigDecimal numericValue = parseNumericToken(
                        matcher.group(2)
                );

                BigDecimal multiplier = multiplierFor(matcher.group(3));

                tokens.add(new AmountToken(
                        numericValue,
                        multiplier,
                        matcher.group(3) != null
                ));
            } catch (NumberFormatException exception) {
                // Skip malformed numeric token.
            }
        }

        return tokens;
    }

    private SharedMultipliers resolveSharedMultipliers(
            AmountToken first,
            AmountToken second
    ) {
        BigDecimal firstMultiplier = first.multiplier();
        BigDecimal secondMultiplier = second.multiplier();

        if (!first.explicitMultiplier()
                && second.explicitMultiplier()
                && shouldPropagateMillionMultiplier(
                secondMultiplier,
                first.numericValue()
        )) {
            firstMultiplier = secondMultiplier;
        }

        if (!second.explicitMultiplier()
                && first.explicitMultiplier()
                && shouldPropagateMillionMultiplier(
                firstMultiplier,
                second.numericValue()
        )) {
            secondMultiplier = firstMultiplier;
        }

        return new SharedMultipliers(
                firstMultiplier,
                secondMultiplier
        );
    }

    private boolean shouldPropagateMillionMultiplier(
            BigDecimal multiplier,
            BigDecimal unscaledNumericValue
    ) {
        if (multiplier.compareTo(ONE_MILLION) < 0) {
            return false;
        }

        /*
         * "15 - 25 triệu" và "15.5 - 20.5 million" dùng unit chung ở
         * cuối range. Nhưng không được biến "15,000,000 - 20 triệu" thành
         * 15,000,000,000,000.
         */
        return unscaledNumericValue.compareTo(
                BigDecimal.valueOf(100_000L)
        ) < 0;
    }

    private Long toLongAmount(
            BigDecimal numericValue,
            BigDecimal multiplier
    ) {
        try {
            return numericValue
                    .multiply(multiplier)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private BigDecimal multiplierFor(String unit) {
        if (unit == null) {
            return BigDecimal.ONE;
        }

        return switch (unit) {
            case "k", "thousand" -> ONE_THOUSAND;
            case "tr", "trieu", "million" -> ONE_MILLION;
            default -> BigDecimal.ONE;
        };
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

    private CurrencyDetection detectCurrency(
            String original,
            String folded
    ) {
        boolean usd = original.contains("$")
                || USD_CODE_PATTERN.matcher(folded).find()
                || folded.contains("us dollar");

        boolean vnd = original.contains("₫")
                || VND_CODE_PATTERN.matcher(folded).find()
                || VIETNAMESE_MILLION_PATTERN.matcher(folded).find();

        if (usd && vnd) {
            return new CurrencyDetection(null, true);
        }

        if (usd) {
            return new CurrencyDetection("USD", false);
        }

        if (vnd) {
            return new CurrencyDetection("VND", false);
        }

        return new CurrencyDetection(null, false);
    }

    private boolean isNegotiable(String folded) {
        return containsAny(
                folded,
                "thoa thuan",
                "canh tranh",
                "negotiable",
                "competitive salary",
                "competitive",
                "luong thuong luong"
        );
    }

    private boolean hasRangeMarker(String folded) {
        if (folded.contains(" den ") || folded.contains(" to ")) {
            return true;
        }

        Matcher matcher = Pattern.compile(
                "\\d(?:[\\d.,]*)\\s*"
                        + "(?:k|thousand|trieu|tr|million)?\\s*"
                        + "[-–—]\\s*\\$?\\s*\\d"
        ).matcher(folded);

        return matcher.find();
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

    private boolean isLowerBoundOnly(String folded) {
        return containsAny(
                folded,
                "tu ",
                "from ",
                "tren ",
                "at least",
                "minimum",
                "toi thieu",
                "more than"
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

    private SalaryNormalizationResult emptyResult() {
        return new SalaryNormalizationResult(
                null,
                null,
                null
        );
    }

    private record AmountToken(
            BigDecimal numericValue,
            BigDecimal multiplier,
            boolean explicitMultiplier
    ) {
    }

    private record SharedMultipliers(
            BigDecimal firstMultiplier,
            BigDecimal secondMultiplier
    ) {
    }

    private record CurrencyDetection(
            String currency,
            boolean conflicting
    ) {
    }
}