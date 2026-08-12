package com.autojob.modules.jobnormalizer.normalization;

import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class DateNormalizer {

    private static final Pattern DAYS_AGO_PATTERN = Pattern.compile(
            "^(\\d+)\\s+(?:ngay|day|days)\\s+(?:truoc|ago)$"
    );

    private static final Pattern WEEKS_AGO_PATTERN = Pattern.compile(
            "^(\\d+)\\s+(?:tuan|week|weeks)\\s+(?:truoc|ago)$"
    );

    private static final Pattern MONTHS_AGO_PATTERN = Pattern.compile(
            "^(\\d+)\\s+(?:thang|month|months)\\s+(?:truoc|ago)$"
    );

    private static final Pattern HOURS_AGO_PATTERN = Pattern.compile(
            "^(\\d+)\\s+(?:gio|hour|hours)\\s+(?:truoc|ago)$"
    );

    private static final Pattern MINUTES_AGO_PATTERN = Pattern.compile(
            "^(\\d+)\\s+(?:phut|minute|minutes)\\s+(?:truoc|ago)$"
    );

    private static final Pattern DEADLINE_IN_DAYS_PATTERN = Pattern.compile(
            "^in\\s+(\\d+)\\s+(?:day|days)$"
    );

    private static final Pattern DEADLINE_DAYS_LEFT_PATTERN = Pattern.compile(
            "^(\\d+)\\s+(?:day|days)\\s+left$"
    );

    private static final Pattern DEADLINE_VIETNAMESE_DAYS_PATTERN = Pattern.compile(
            "^(\\d+)\\s+ngay\\s+nua$"
    );

    private static final Pattern DEADLINE_VIETNAMESE_REMAINING_PATTERN = Pattern.compile(
            "^con\\s+(\\d+)\\s+ngay(?:\\s+nua)?$"
    );

    private static final List<DateTimeFormatter> LOCAL_DATE_FORMATTERS =
            List.of(
                    DateTimeFormatter.ISO_LOCAL_DATE,
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("dd-MM-yyyy")
            );

    private static final List<DateTimeFormatter> LOCAL_DATE_TIME_FORMATTERS =
            List.of(
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            );

    private final TextNormalizer textNormalizer;
    private final Clock normalizationClock;

    /**
     * Posted date chỉ có ngày được normalize về đầu ngày theo timezone của
     * injected Clock. Relative hour/minute giữ timestamp tương đối chính xác.
     */
    public Instant normalizePostedAt(String postedText) {
        String cleaned = textNormalizer.normalizeInline(postedText);

        if (cleaned == null) {
            return null;
        }

        Instant common = parseCommonExactDateTime(cleaned);

        if (common != null) {
            return common;
        }

        Instant recentRelativeInstant = parseRecentPostedInstant(cleaned);

        if (recentRelativeInstant != null) {
            return recentRelativeInstant;
        }

        LocalDate relativeDate = parseRelativeDate(cleaned, false);

        if (relativeDate != null) {
            return toInstant(
                    relativeDate,
                    DateBoundary.START_OF_DAY
            );
        }

        LocalDate localDate = parseLocalDate(cleaned);

        if (localDate != null) {
            return toInstant(
                    localDate,
                    DateBoundary.START_OF_DAY
            );
        }

        return null;
    }

    /**
     * Deadline chỉ có ngày được normalize về cuối ngày theo timezone của
     * injected Clock.
     */
    public Instant normalizeDeadlineAt(String deadlineText) {
        String cleaned = textNormalizer.normalizeInline(deadlineText);

        if (cleaned == null) {
            return null;
        }

        Instant common = parseCommonExactDateTime(cleaned);

        if (common != null) {
            return common;
        }

        LocalDate relativeDate = parseRelativeDate(cleaned, true);

        if (relativeDate != null) {
            return toInstant(
                    relativeDate,
                    DateBoundary.END_OF_DAY
            );
        }

        LocalDate localDate = parseLocalDate(cleaned);

        if (localDate != null) {
            return toInstant(
                    localDate,
                    DateBoundary.END_OF_DAY
            );
        }

        return null;
    }

    private Instant parseCommonExactDateTime(String value) {
        Instant exactInstant = parseExactInstant(value);

        if (exactInstant != null) {
            return exactInstant;
        }

        LocalDateTime localDateTime = parseLocalDateTime(value);

        if (localDateTime == null) {
            return null;
        }

        return localDateTime
                .atZone(normalizationClock.getZone())
                .toInstant();
    }

    private Instant parseRecentPostedInstant(String value) {
        String folded = NormalizationTextSupport.fold(value);

        Matcher hoursMatcher = HOURS_AGO_PATTERN.matcher(folded);

        if (hoursMatcher.matches()) {
            return subtractSeconds(
                    hoursMatcher.group(1),
                    3_600L
            );
        }

        Matcher minutesMatcher = MINUTES_AGO_PATTERN.matcher(folded);

        if (minutesMatcher.matches()) {
            return subtractSeconds(
                    minutesMatcher.group(1),
                    60L
            );
        }

        return null;
    }

    private Instant subtractSeconds(
            String amountText,
            long secondsPerUnit
    ) {
        try {
            long amount = Long.parseLong(amountText);
            long seconds = Math.multiplyExact(
                    amount,
                    secondsPerUnit
            );

            return normalizationClock.instant().minusSeconds(seconds);
        } catch (ArithmeticException
                 | DateTimeException
                 | NumberFormatException exception) {
            return null;
        }
    }

    private Instant parseExactInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // Try the next exact date-time format.
        }

        try {
            return OffsetDateTime
                    .parse(
                            value,
                            DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    )
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            // Try zoned date-time.
        }

        try {
            return ZonedDateTime
                    .parse(
                            value,
                            DateTimeFormatter.ISO_ZONED_DATE_TIME
                    )
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private LocalDateTime parseLocalDateTime(String value) {
        for (DateTimeFormatter formatter : LOCAL_DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter.
            }
        }

        return null;
    }

    private LocalDate parseLocalDate(String value) {
        for (DateTimeFormatter formatter : LOCAL_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter.
            }
        }

        return null;
    }

    private LocalDate parseRelativeDate(
            String value,
            boolean deadline
    ) {
        String folded = NormalizationTextSupport.fold(value);
        LocalDate today = LocalDate.now(normalizationClock);

        if (folded.equals("hom nay") || folded.equals("today")) {
            return today;
        }

        if (folded.equals("hom qua") || folded.equals("yesterday")) {
            return today.minusDays(1);
        }

        Matcher daysAgoMatcher = DAYS_AGO_PATTERN.matcher(folded);

        if (daysAgoMatcher.matches()) {
            return subtractDateUnits(
                    today,
                    daysAgoMatcher.group(1),
                    RelativeDateUnit.DAY
            );
        }

        Matcher weeksAgoMatcher = WEEKS_AGO_PATTERN.matcher(folded);

        if (weeksAgoMatcher.matches()) {
            return subtractDateUnits(
                    today,
                    weeksAgoMatcher.group(1),
                    RelativeDateUnit.WEEK
            );
        }

        Matcher monthsAgoMatcher = MONTHS_AGO_PATTERN.matcher(folded);

        if (monthsAgoMatcher.matches()) {
            return subtractDateUnits(
                    today,
                    monthsAgoMatcher.group(1),
                    RelativeDateUnit.MONTH
            );
        }

        if (!deadline) {
            return null;
        }

        if (folded.equals("tomorrow")
                || folded.equals("ngay mai")) {
            return today.plusDays(1);
        }

        Long futureDays = parseFutureDeadlineDays(folded);

        if (futureDays == null) {
            return null;
        }

        try {
            return today.plusDays(futureDays);
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private LocalDate subtractDateUnits(
            LocalDate today,
            String amountText,
            RelativeDateUnit unit
    ) {
        try {
            long amount = Long.parseLong(amountText);

            return switch (unit) {
                case DAY -> today.minusDays(amount);
                case WEEK -> today.minusWeeks(amount);
                case MONTH -> today.minusMonths(amount);
            };
        } catch (NumberFormatException
                 | DateTimeException exception) {
            return null;
        }
    }

    private Long parseFutureDeadlineDays(String folded) {
        List<Pattern> patterns = List.of(
                DEADLINE_IN_DAYS_PATTERN,
                DEADLINE_DAYS_LEFT_PATTERN,
                DEADLINE_VIETNAMESE_DAYS_PATTERN,
                DEADLINE_VIETNAMESE_REMAINING_PATTERN
        );

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(folded);

            if (!matcher.matches()) {
                continue;
            }

            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        return null;
    }

    private Instant toInstant(
            LocalDate date,
            DateBoundary dateBoundary
    ) {
        ZoneId zoneId = normalizationClock.getZone();

        if (dateBoundary == DateBoundary.END_OF_DAY) {
            return date
                    .plusDays(1)
                    .atStartOfDay(zoneId)
                    .minusNanos(1)
                    .toInstant();
        }

        return date
                .atStartOfDay(zoneId)
                .toInstant();
    }

    private enum DateBoundary {
        START_OF_DAY,
        END_OF_DAY
    }

    private enum RelativeDateUnit {
        DAY,
        WEEK,
        MONTH
    }
}