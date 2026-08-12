package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DateNormalizerTest {

    private static final ZoneId HO_CHI_MINH_ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    /*
     * 2026-07-12T03:00:00Z
     * tương ứng 10:00 ngày 12/07/2026 tại Việt Nam.
     */
    private static final Instant FIXED_NOW =
            Instant.parse("2026-07-12T03:00:00Z");

    private DateNormalizer dateNormalizer;

    @BeforeEach
    void setUp() {
        Clock fixedClock =
                Clock.fixed(
                        FIXED_NOW,
                        HO_CHI_MINH_ZONE
                );

        NormalizationTaxonomyProperties taxonomy =
                TaxonomyTestLoader.load();

        dateNormalizer =
                new DateNormalizer(
                        new TextNormalizer(),
                        taxonomy,
                        fixedClock
                );
    }

    @Test
    void shouldNormalizeIsoLocalDateAsPostedAtStartOfDay() {
        Instant result =
                dateNormalizer.normalizePostedAt(
                        "2026-07-12"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-11T17:00:00Z"
                )
        );
    }

    @Test
    void shouldNormalizeIsoLocalDateAsDeadlineAtEndOfDay() {
        Instant result =
                dateNormalizer.normalizeDeadlineAt(
                        "2026-07-12"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-12T16:59:59.999999999Z"
                )
        );
    }

    @Test
    void shouldNormalizeVietnameseDateFormat() {
        Instant result =
                dateNormalizer.normalizePostedAt(
                        "12/07/2026"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-11T17:00:00Z"
                )
        );
    }

    @Test
    void shouldNormalizeDashDateFormat() {
        Instant result =
                dateNormalizer.normalizePostedAt(
                        "12-07-2026"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-11T17:00:00Z"
                )
        );
    }

    @Test
    void shouldNormalizeExactUtcInstantWithoutChangingIt() {
        Instant result =
                dateNormalizer.normalizePostedAt(
                        "2026-07-12T03:30:00Z"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-12T03:30:00Z"
                )
        );
    }

    @Test
    void shouldNormalizeOffsetDateTime() {
        Instant result =
                dateNormalizer.normalizePostedAt(
                        "2026-07-12T10:30:00+07:00"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-12T03:30:00Z"
                )
        );
    }

    @Test
    void shouldNormalizeLocalDateTimeUsingConfiguredTimezone() {
        Instant result =
                dateNormalizer.normalizePostedAt(
                        "2026-07-12 14:30"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-12T07:30:00Z"
                )
        );
    }

    @Test
    void shouldNormalizeVietnameseToday() {
        Instant result =
                dateNormalizer.normalizePostedAt(
                        "Hôm nay"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-11T17:00:00Z"
                )
        );
    }

    @Test
    void shouldNormalizeEnglishToday() {
        Instant result =
                dateNormalizer.normalizePostedAt(
                        "Today"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-11T17:00:00Z"
                )
        );
    }

    @Test
    void shouldNormalizeVietnameseYesterday() {
        Instant result =
                dateNormalizer.normalizePostedAt(
                        "Hôm qua"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-10T17:00:00Z"
                )
        );
    }

    @Test
    void shouldNormalizeEnglishYesterday() {
        Instant result =
                dateNormalizer.normalizePostedAt(
                        "Yesterday"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-10T17:00:00Z"
                )
        );
    }

    @Test
    void shouldNormalizeVietnameseDaysAgo() {
        Instant result =
                dateNormalizer.normalizePostedAt(
                        "3 ngày trước"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-08T17:00:00Z"
                )
        );
    }

    @Test
    void shouldNormalizeEnglishDaysAgo() {
        Instant result =
                dateNormalizer.normalizePostedAt(
                        "3 days ago"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-08T17:00:00Z"
                )
        );
    }

    @Test
    void shouldUseEndOfDayForRelativeDeadline() {
        Instant result =
                dateNormalizer.normalizeDeadlineAt(
                        "Hôm nay"
                );

        assertThat(result).isEqualTo(
                Instant.parse(
                        "2026-07-12T16:59:59.999999999Z"
                )
        );
    }

    @Test
    void shouldReturnNullForUnknownDateFormat() {
        assertThat(
                dateNormalizer.normalizePostedAt(
                        "Khoảng cuối tháng"
                )
        ).isNull();
    }

    @Test
    void shouldReturnNullForNullOrBlankInput() {
        assertThat(
                dateNormalizer.normalizePostedAt(
                        null
                )
        ).isNull();

        assertThat(
                dateNormalizer.normalizePostedAt(
                        "   "
                )
        ).isNull();

        assertThat(
                dateNormalizer.normalizeDeadlineAt(
                        null
                )
        ).isNull();
    }
}