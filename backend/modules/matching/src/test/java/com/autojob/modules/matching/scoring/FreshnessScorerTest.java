package com.autojob.modules.matching.scoring;

import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.config.MatchingProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessScorerTest {

    private static final Instant NOW =
            Instant.parse(
                    "2026-09-06T00:00:00Z"
            );

    private static final double EPSILON =
            0.000001d;

    @Test
    void recentPostedJobShouldBeKnownAndFresh() {

        FreshnessScorer scorer =
                scorer();

        NormalizedJob job =
                NormalizedJob
                        .builder()
                        .postedAt(
                                Instant.parse(
                                        "2026-09-03T00:00:00Z"
                                )
                        )
                        .normalizedAt(
                                NOW
                        )
                        .build();

        FreshnessScorer.Result result =
                scorer.evaluate(
                        job
                );

        assertThat(
                result.known()
        ).isTrue();

        assertThat(
                result.score()
        )
                .isCloseTo(
                        1.0d,
                        org.assertj.core.data.Offset.offset(
                                EPSILON
                        )
                );
    }

    @Test
    void missingPostedAtMustRemainUnknownEvenWhenNormalizedToday() {

        FreshnessScorer scorer =
                scorer();

        NormalizedJob job =
                NormalizedJob
                        .builder()

                        /*
                         * Crawler không lấy được datePosted.
                         */
                        .postedAt(
                                null
                        )

                        /*
                         * Hệ thống vừa normalize hôm nay.
                         *
                         * Đây KHÔNG phải bằng chứng rằng job mới.
                         */
                        .normalizedAt(
                                NOW
                        )

                        .build();

        FreshnessScorer.Result result =
                scorer.evaluate(
                        job
                );

        assertThat(
                result.known()
        ).isFalse();

        /*
         * 0.50 chỉ là numeric placeholder.
         *
         * Ranking phải nhìn known=false.
         */
        assertThat(
                result.score()
        )
                .isCloseTo(
                        0.50d,
                        org.assertj.core.data.Offset.offset(
                                EPSILON
                        )
                );
    }

    @Test
    void oldNormalizedAtMustNotMakeFreshnessKnownWithoutPostedAt() {

        FreshnessScorer scorer =
                scorer();

        NormalizedJob job =
                NormalizedJob
                        .builder()
                        .postedAt(
                                null
                        )
                        .normalizedAt(
                                Instant.parse(
                                        "2025-01-01T00:00:00Z"
                                )
                        )
                        .build();

        FreshnessScorer.Result result =
                scorer.evaluate(
                        job
                );

        assertThat(
                result.known()
        ).isFalse();

        assertThat(
                result.score()
        )
                .isCloseTo(
                        0.50d,
                        org.assertj.core.data.Offset.offset(
                                EPSILON
                        )
                );
    }

    @Test
    void jobAtFreshBoundaryShouldReceiveFullFreshnessScore() {

        FreshnessScorer scorer =
                scorer();

        NormalizedJob job =
                NormalizedJob
                        .builder()
                        .postedAt(
                                Instant.parse(
                                        "2026-08-30T00:00:00Z"
                                )
                        )
                        .build();

        FreshnessScorer.Result result =
                scorer.evaluate(
                        job
                );

        assertThat(
                result.known()
        ).isTrue();

        assertThat(
                result.score()
        )
                .isCloseTo(
                        1.0d,
                        org.assertj.core.data.Offset.offset(
                                EPSILON
                        )
                );
    }

    @Test
    void jobBetweenFreshAndMaximumAgeShouldDecayGradually() {

        FreshnessScorer scorer =
                scorer();

        /*
         * 18 days old.
         *
         * Config:
         *
         * freshDays = 7
         * maxAgeDays = 30
         *
         * progress:
         *
         * (18 - 7) / (30 - 7)
         * = 11 / 23
         *
         * score:
         *
         * 1 - 11/23
         * ~= 0.521739
         */
        NormalizedJob job =
                NormalizedJob
                        .builder()
                        .postedAt(
                                Instant.parse(
                                        "2026-08-19T00:00:00Z"
                                )
                        )
                        .build();

        FreshnessScorer.Result result =
                scorer.evaluate(
                        job
                );

        assertThat(
                result.known()
        ).isTrue();

        assertThat(
                result.score()
        )
                .isCloseTo(
                        12.0d / 23.0d,
                        org.assertj.core.data.Offset.offset(
                                EPSILON
                        )
                );
    }

    @Test
    void jobAtMaximumAgeShouldReceiveZeroFreshnessScore() {

        FreshnessScorer scorer =
                scorer();

        NormalizedJob job =
                NormalizedJob
                        .builder()
                        .postedAt(
                                Instant.parse(
                                        "2026-08-07T00:00:00Z"
                                )
                        )
                        .build();

        FreshnessScorer.Result result =
                scorer.evaluate(
                        job
                );

        assertThat(
                result.known()
        ).isTrue();

        assertThat(
                result.score()
        )
                .isCloseTo(
                        0.0d,
                        org.assertj.core.data.Offset.offset(
                                EPSILON
                        )
                );
    }

    @Test
    void futurePostedAtShouldBeKnownAndFresh() {

        FreshnessScorer scorer =
                scorer();

        NormalizedJob job =
                NormalizedJob
                        .builder()
                        .postedAt(
                                Instant.parse(
                                        "2026-09-07T00:00:00Z"
                                )
                        )
                        .build();

        FreshnessScorer.Result result =
                scorer.evaluate(
                        job
                );

        assertThat(
                result.known()
        ).isTrue();

        assertThat(
                result.score()
        )
                .isCloseTo(
                        1.0d,
                        org.assertj.core.data.Offset.offset(
                                EPSILON
                        )
                );
    }

    private static FreshnessScorer scorer() {

        MatchingProperties properties =
                new MatchingProperties();

        properties
                .getFreshness()
                .setFreshDays(
                        7
                );

        properties
                .getFreshness()
                .setMaxAgeDays(
                        30
                );

        Clock clock =
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                );

        return new FreshnessScorer(
                properties,
                clock
        );
    }
}