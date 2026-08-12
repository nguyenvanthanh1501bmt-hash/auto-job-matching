package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DateNormalizer {

    private static final List<DateTimeFormatter> DATE_FORMATS =
            List.of(
                    DateTimeFormatter.ISO_LOCAL_DATE,
                    DateTimeFormatter.ofPattern(
                            "dd/MM/yyyy"
                    ),
                    DateTimeFormatter.ofPattern(
                            "dd-MM-yyyy"
                    )
            );

    private static final List<DateTimeFormatter>
            DATE_TIME_FORMATS =
            List.of(
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                    DateTimeFormatter.ofPattern(
                            "yyyy-MM-dd HH:mm:ss"
                    ),
                    DateTimeFormatter.ofPattern(
                            "yyyy-MM-dd HH:mm"
                    ),
                    DateTimeFormatter.ofPattern(
                            "dd/MM/yyyy HH:mm:ss"
                    ),
                    DateTimeFormatter.ofPattern(
                            "dd/MM/yyyy HH:mm"
                    )
            );

    private final TextNormalizer textNormalizer;
    private final Clock clock;

    private final Set<String> today;
    private final Set<String> yesterday;
    private final Set<String> tomorrow;

    private final Map<
            String,
            NormalizationTaxonomyProperties.DateUnit
            > units;

    private final Set<String> ago;
    private final Set<String> inPrefixes;
    private final Set<String> leftSuffixes;
    private final Set<String> futureSuffixes;
    private final Set<String> remainingPrefixes;

    public DateNormalizer(
            TextNormalizer textNormalizer,
            NormalizationTaxonomyProperties taxonomy,
            Clock normalizationClock
    ) {
        this.textNormalizer =
                textNormalizer;

        this.clock =
                normalizationClock;

        var config =
                taxonomy.getDate();

        this.today =
                foldSet(
                        config.getTodayPhrases()
                );

        this.yesterday =
                foldSet(
                        config.getYesterdayPhrases()
                );

        this.tomorrow =
                foldSet(
                        config.getTomorrowPhrases()
                );

        this.units =
                buildUnits(
                        config.getUnits()
                );

        this.ago =
                foldSet(
                        config.getAgoWords()
                );

        this.inPrefixes =
                foldSet(
                        config.getDeadlineInPrefixes()
                );

        this.leftSuffixes =
                foldSet(
                        config.getDeadlineLeftSuffixes()
                );

        this.futureSuffixes =
                foldSet(
                        config.getDeadlineFutureSuffixes()
                );

        this.remainingPrefixes =
                foldSet(
                        config.getDeadlineRemainingPrefixes()
                );
    }

    public Instant normalizePostedAt(
            String postedText
    ) {
        String cleaned =
                textNormalizer.normalizeInline(
                        postedText
                );

        if (cleaned == null) {
            return null;
        }

        Instant exact =
                parseExactDateTime(
                        cleaned
                );

        if (exact != null) {
            return exact;
        }

        String folded =
                NormalizationTextSupport.fold(
                        cleaned
                );

        Relative relative =
                parseAgo(
                        folded
                );

        if (relative != null
                && isRecent(
                relative.unit()
        )) {
            return subtractRecent(
                    relative
            );
        }

        LocalDate date =
                resolveRelativeDate(
                        folded,
                        false
                );

        if (date == null) {
            date =
                    parseDate(
                            cleaned
                    );
        }

        return date == null
                ? null
                : atBoundary(
                date,
                false
        );
    }

    public Instant normalizeDeadlineAt(
            String deadlineText
    ) {
        String cleaned =
                textNormalizer.normalizeInline(
                        deadlineText
                );

        if (cleaned == null) {
            return null;
        }

        Instant exact =
                parseExactDateTime(
                        cleaned
                );

        if (exact != null) {
            return exact;
        }

        String folded =
                NormalizationTextSupport.fold(
                        cleaned
                );

        LocalDate date =
                resolveRelativeDate(
                        folded,
                        true
                );

        if (date == null) {
            date =
                    parseDate(
                            cleaned
                    );
        }

        return date == null
                ? null
                : atBoundary(
                date,
                true
        );
    }

    private LocalDate resolveRelativeDate(
            String value,
            boolean deadline
    ) {
        LocalDate current =
                LocalDate.now(
                        clock
                );

        if (today.contains(value)) {
            return current;
        }

        if (yesterday.contains(value)) {
            return current.minusDays(1);
        }

        Relative relative =
                parseAgo(
                        value
                );

        LocalDate past =
                relative == null
                        ? null
                        : subtractDate(
                        current,
                        relative
                );

        if (past != null) {
            return past;
        }

        if (!deadline) {
            return null;
        }

        if (tomorrow.contains(value)) {
            return current.plusDays(1);
        }

        Long futureDays =
                parseFutureDays(
                        value
                );

        if (futureDays == null) {
            return null;
        }

        try {
            return current.plusDays(
                    futureDays
            );
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private Relative parseAgo(
            String value
    ) {
        for (String suffix : ago) {
            String body =
                    stripSuffix(
                            value,
                            suffix
                    );

            AmountUnit parsed =
                    body == null
                            ? null
                            : parseAmountUnit(
                            body
                    );

            if (parsed != null) {
                return new Relative(
                        parsed.amount(),
                        parsed.unit()
                );
            }
        }

        return null;
    }

    private Long parseFutureDays(
            String value
    ) {
        Long amount =
                parsePrefixedDay(
                        value,
                        inPrefixes
                );

        if (amount != null) {
            return amount;
        }

        amount =
                parseSuffixedDay(
                        value,
                        leftSuffixes
                );

        if (amount != null) {
            return amount;
        }

        amount =
                parseSuffixedDay(
                        value,
                        futureSuffixes
                );

        if (amount != null) {
            return amount;
        }

        for (String prefix
                : remainingPrefixes) {

            String body =
                    stripPrefix(
                            value,
                            prefix
                    );

            if (body == null) {
                continue;
            }

            AmountUnit parsed =
                    parseAmountUnit(
                            body
                    );

            if (isDay(parsed)) {
                return parsed.amount();
            }

            for (String suffix
                    : futureSuffixes) {

                String withoutSuffix =
                        stripSuffix(
                                body,
                                suffix
                        );

                parsed =
                        withoutSuffix == null
                                ? null
                                : parseAmountUnit(
                                withoutSuffix
                        );

                if (isDay(parsed)) {
                    return parsed.amount();
                }
            }
        }

        return null;
    }

    private Long parsePrefixedDay(
            String value,
            Set<String> prefixes
    ) {
        for (String prefix : prefixes) {
            String body =
                    stripPrefix(
                            value,
                            prefix
                    );

            AmountUnit parsed =
                    body == null
                            ? null
                            : parseAmountUnit(
                            body
                    );

            if (isDay(parsed)) {
                return parsed.amount();
            }
        }

        return null;
    }

    private Long parseSuffixedDay(
            String value,
            Set<String> suffixes
    ) {
        for (String suffix : suffixes) {
            String body =
                    stripSuffix(
                            value,
                            suffix
                    );

            AmountUnit parsed =
                    body == null
                            ? null
                            : parseAmountUnit(
                            body
                    );

            if (isDay(parsed)) {
                return parsed.amount();
            }
        }

        return null;
    }

    private AmountUnit parseAmountUnit(
            String value
    ) {
        String[] parts =
                value
                        .trim()
                        .split("\\s+");

        if (parts.length != 2) {
            return null;
        }

        try {
            var unit =
                    units.get(
                            parts[1]
                    );

            return unit == null
                    ? null
                    : new AmountUnit(
                    Long.parseLong(
                            parts[0]
                    ),
                    unit
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isDay(
            AmountUnit value
    ) {
        return value != null
                && value.unit()
                == NormalizationTaxonomyProperties
                .DateUnit.DAY;
    }

    private boolean isRecent(
            NormalizationTaxonomyProperties.DateUnit
                    unit
    ) {
        return unit
                == NormalizationTaxonomyProperties
                .DateUnit.HOUR
                || unit
                == NormalizationTaxonomyProperties
                .DateUnit.MINUTE;
    }

    private LocalDate subtractDate(
            LocalDate date,
            Relative relative
    ) {
        try {
            return switch (relative.unit()) {
                case DAY ->
                        date.minusDays(
                                relative.amount()
                        );

                case WEEK ->
                        date.minusWeeks(
                                relative.amount()
                        );

                case MONTH ->
                        date.minusMonths(
                                relative.amount()
                        );

                case HOUR, MINUTE ->
                        null;
            };
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private Instant subtractRecent(
            Relative relative
    ) {
        long secondsPerUnit =
                relative.unit()
                        == NormalizationTaxonomyProperties
                        .DateUnit.HOUR
                        ? 3_600L
                        : 60L;

        try {
            return clock
                    .instant()
                    .minusSeconds(
                            Math.multiplyExact(
                                    relative.amount(),
                                    secondsPerUnit
                            )
                    );
        } catch (ArithmeticException
                 | DateTimeException exception) {
            return null;
        }
    }

    private Instant parseExactDateTime(
            String value
    ) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // Try next format.
        }

        try {
            return OffsetDateTime
                    .parse(
                            value,
                            DateTimeFormatter
                                    .ISO_OFFSET_DATE_TIME
                    )
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            // Try next format.
        }

        try {
            return ZonedDateTime
                    .parse(
                            value,
                            DateTimeFormatter
                                    .ISO_ZONED_DATE_TIME
                    )
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            // Try local date-time.
        }

        for (DateTimeFormatter formatter
                : DATE_TIME_FORMATS) {

            try {
                return LocalDateTime
                        .parse(
                                value,
                                formatter
                        )
                        .atZone(
                                clock.getZone()
                        )
                        .toInstant();
            } catch (DateTimeParseException ignored) {
                // Try next format.
            }
        }

        return null;
    }

    private LocalDate parseDate(
            String value
    ) {
        for (DateTimeFormatter formatter
                : DATE_FORMATS) {

            try {
                return LocalDate.parse(
                        value,
                        formatter
                );
            } catch (DateTimeParseException ignored) {
                // Try next format.
            }
        }

        return null;
    }

    private Map<
            String,
            NormalizationTaxonomyProperties.DateUnit
            > buildUnits(
            List<NormalizationTaxonomyProperties.DateUnitRule>
                    rules
    ) {
        Map<
                String,
                NormalizationTaxonomyProperties.DateUnit
                > result =
                new LinkedHashMap<>();

        for (var rule : rules) {
            for (String alias
                    : rule.getAliases()) {

                result.put(
                        NormalizationTextSupport.fold(
                                alias
                        ),
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
                        NormalizationTextSupport.fold(
                                value
                        );

                if (!folded.isBlank()) {
                    result.add(folded);
                }
            }
        }

        return Set.copyOf(result);
    }

    private String stripPrefix(
            String value,
            String prefix
    ) {
        String marker =
                prefix + " ";

        return value.startsWith(marker)
                ? value
                .substring(
                        marker.length()
                )
                .trim()
                : null;
    }

    private String stripSuffix(
            String value,
            String suffix
    ) {
        String marker =
                " " + suffix;

        return value.endsWith(marker)
                ? value
                .substring(
                        0,
                        value.length()
                                - marker.length()
                )
                .trim()
                : null;
    }

    private Instant atBoundary(
            LocalDate date,
            boolean endOfDay
    ) {
        ZoneId zone =
                clock.getZone();

        return endOfDay
                ? date
                .plusDays(1)
                .atStartOfDay(zone)
                .minusNanos(1)
                .toInstant()
                : date
                .atStartOfDay(zone)
                .toInstant();
    }

    private record AmountUnit(
            long amount,
            NormalizationTaxonomyProperties.DateUnit unit
    ) {
    }

    private record Relative(
            long amount,
            NormalizationTaxonomyProperties.DateUnit unit
    ) {
    }
}