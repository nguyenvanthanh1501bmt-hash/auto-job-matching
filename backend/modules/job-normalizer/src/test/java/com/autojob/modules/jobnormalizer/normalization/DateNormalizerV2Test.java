package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DateNormalizerV2Test {

    private static final ZoneId ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Instant FIXED_NOW =
            Instant.parse("2026-07-12T03:00:00Z");

    private DateNormalizer normalizer;

    @BeforeEach
    void setUp() {
        NormalizationTaxonomyProperties taxonomy =
                TaxonomyTestLoader.load();

        normalizer =
                new DateNormalizer(
                        new TextNormalizer(),
                        taxonomy,
                        Clock.fixed(
                                FIXED_NOW,
                                ZONE
                        )
                );
    }

    @Test
    void shouldNormalizeEnglishAndVietnameseHoursAgo() {
        assertThat(
                normalizer.normalizePostedAt(
                        "2 hours ago"
                )
        ).isEqualTo(
                Instant.parse(
                        "2026-07-12T01:00:00Z"
                )
        );

        assertThat(
                normalizer.normalizePostedAt(
                        "2 giờ trước"
                )
        ).isEqualTo(
                Instant.parse(
                        "2026-07-12T01:00:00Z"
                )
        );
    }

    @Test
    void shouldNormalizeEnglishAndVietnameseMinutesAgo() {
        assertThat(
                normalizer.normalizePostedAt(
                        "30 minutes ago"
                )
        ).isEqualTo(
                Instant.parse(
                        "2026-07-12T02:30:00Z"
                )
        );

        assertThat(
                normalizer.normalizePostedAt(
                        "30 phút trước"
                )
        ).isEqualTo(
                Instant.parse(
                        "2026-07-12T02:30:00Z"
                )
        );
    }

    @Test
    void shouldNormalizeWeeksAgoAtStartOfResolvedDate() {
        Instant expected =
                Instant.parse(
                        "2026-06-27T17:00:00Z"
                );

        assertThat(
                normalizer.normalizePostedAt(
                        "2 weeks ago"
                )
        ).isEqualTo(
                expected
        );

        assertThat(
                normalizer.normalizePostedAt(
                        "2 tuần trước"
                )
        ).isEqualTo(
                expected
        );
    }

    @Test
    void shouldNormalizeMonthsAgoAtStartOfResolvedDate() {
        Instant expected =
                Instant.parse(
                        "2026-06-11T17:00:00Z"
                );

        assertThat(
                normalizer.normalizePostedAt(
                        "1 month ago"
                )
        ).isEqualTo(
                expected
        );

        assertThat(
                normalizer.normalizePostedAt(
                        "1 tháng trước"
                )
        ).isEqualTo(
                expected
        );
    }

    @Test
    void shouldNormalizeTomorrowDeadlineAtEndOfDay() {
        Instant expected =
                Instant.parse(
                        "2026-07-13T16:59:59.999999999Z"
                );

        assertThat(
                normalizer.normalizeDeadlineAt(
                        "tomorrow"
                )
        ).isEqualTo(
                expected
        );

        assertThat(
                normalizer.normalizeDeadlineAt(
                        "ngày mai"
                )
        ).isEqualTo(
                expected
        );
    }

    @Test
    void shouldNormalizeClearFutureDayDeadlinePhrases() {
        Instant expected =
                Instant.parse(
                        "2026-07-15T16:59:59.999999999Z"
                );

        assertThat(
                normalizer.normalizeDeadlineAt(
                        "in 3 days"
                )
        ).isEqualTo(
                expected
        );

        assertThat(
                normalizer.normalizeDeadlineAt(
                        "3 days left"
                )
        ).isEqualTo(
                expected
        );

        assertThat(
                normalizer.normalizeDeadlineAt(
                        "3 ngày nữa"
                )
        ).isEqualTo(
                expected
        );

        assertThat(
                normalizer.normalizeDeadlineAt(
                        "còn 3 ngày"
                )
        ).isEqualTo(
                expected
        );

        assertThat(
                normalizer.normalizeDeadlineAt(
                        "còn 3 ngày nữa"
                )
        ).isEqualTo(
                expected
        );
    }

    @Test
    void shouldNotGuessAmbiguousDeadlinePhrases() {
        assertThat(
                normalizer.normalizeDeadlineAt(
                        "cuối tháng"
                )
        ).isNull();

        assertThat(
                normalizer.normalizeDeadlineAt(
                        "sắp hết hạn"
                )
        ).isNull();

        assertThat(
                normalizer.normalizeDeadlineAt(
                        "ứng tuyển sớm"
                )
        ).isNull();
    }
}