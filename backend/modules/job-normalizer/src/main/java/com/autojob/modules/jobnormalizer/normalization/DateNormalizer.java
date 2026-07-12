package com.autojob.modules.jobnormalizer.normalization;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
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
     * Posted date chỉ có ngày:
     *
     * 2026-07-07
     * → đầu ngày theo Asia/Ho_Chi_Minh.
     */
    public Instant normalizePostedAt(String postedText) {
        return normalize(
                postedText,
                DateBoundary.START_OF_DAY
        );
    }

    /**
     * Deadline chỉ có ngày:
     *
     * 2026-07-30
     * → cuối ngày theo Asia/Ho_Chi_Minh.
     */
    public Instant normalizeDeadlineAt(String deadlineText) {
        return normalize(
                deadlineText,
                DateBoundary.END_OF_DAY
        );
    }

    private Instant normalize(
            String value,
            DateBoundary dateBoundary
    ) {
        String cleaned = textNormalizer.normalizeInline(value);

        if (cleaned == null) {
            return null;
        }

        Instant exactInstant = parseExactInstant(cleaned);

        if (exactInstant != null) {
            return exactInstant;
        }

        LocalDateTime localDateTime = parseLocalDateTime(cleaned);

        if (localDateTime != null) {
            return localDateTime
                    .atZone(normalizationClock.getZone())
                    .toInstant();
        }

        LocalDate relativeDate = parseRelativeDate(cleaned);

        if (relativeDate != null) {
            return toInstant(relativeDate, dateBoundary);
        }

        LocalDate localDate = parseLocalDate(cleaned);

        if (localDate != null) {
            return toInstant(localDate, dateBoundary);
        }

        /*
         * Không đoán khi format không chắc chắn.
         */
        return null;
    }

    private Instant parseExactInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // Try the next exact date-time format.
        }

        try {
            return OffsetDateTime
                    .parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            // Try zoned date-time.
        }

        try {
            return ZonedDateTime
                    .parse(value, DateTimeFormatter.ISO_ZONED_DATE_TIME)
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

    private LocalDate parseRelativeDate(String value) {
        String folded = NormalizationTextSupport.fold(value);

        LocalDate today = LocalDate.now(normalizationClock);

        if (folded.equals("hom nay") || folded.equals("today")) {
            return today;
        }

        if (folded.equals("hom qua") || folded.equals("yesterday")) {
            return today.minusDays(1);
        }

        Matcher matcher = DAYS_AGO_PATTERN.matcher(folded);

        if (!matcher.matches()) {
            return null;
        }

        try {
            long days = Long.parseLong(matcher.group(1));

            return today.minusDays(days);
        } catch (NumberFormatException exception) {
            return null;
        }
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
}