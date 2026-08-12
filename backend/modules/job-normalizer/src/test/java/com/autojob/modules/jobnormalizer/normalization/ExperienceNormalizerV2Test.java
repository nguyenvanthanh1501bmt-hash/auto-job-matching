package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceNormalizerV2Test {

    private ExperienceNormalizer normalizer;

    @BeforeEach
    void setUp() {
        NormalizationTaxonomyProperties taxonomy =
                TaxonomyTestLoader.load();

        normalizer =
                new ExperienceNormalizer(
                        new TextNormalizer(),
                        taxonomy
                );
    }

    @Test
    void shouldParseMixedEnglishUnitsAsOneDuration() {
        ExperienceNormalizationResult result =
                normalizer.normalize(
                        "1 year 6 months"
                );

        assertThat(result.min())
                .isEqualTo(1.5);

        assertThat(result.max())
                .isEqualTo(1.5);
    }

    @Test
    void shouldParseMixedVietnameseUnitsAsOneDuration() {
        ExperienceNormalizationResult result =
                normalizer.normalize(
                        "1 năm 6 tháng"
                );

        assertThat(result.min())
                .isEqualTo(1.5);

        assertThat(result.max())
                .isEqualTo(1.5);
    }

    @Test
    void shouldConvertStandaloneMonthsWithoutTreatingNumberAsYears() {
        ExperienceNormalizationResult english =
                normalizer.normalize(
                        "18 months"
                );

        ExperienceNormalizationResult vietnamese =
                normalizer.normalize(
                        "18 tháng"
                );

        assertThat(english.min())
                .isEqualTo(1.5);

        assertThat(english.max())
                .isNull();

        assertThat(vietnamese.min())
                .isEqualTo(1.5);

        assertThat(vietnamese.max())
                .isNull();
    }

    @Test
    void shouldParseMonthLowerBounds() {
        ExperienceNormalizationResult plus =
                normalizer.normalize(
                        "18+ months"
                );

        ExperienceNormalizationResult english =
                normalizer.normalize(
                        "at least 18 months"
                );

        ExperienceNormalizationResult vietnamese =
                normalizer.normalize(
                        "tối thiểu 18 tháng"
                );

        assertThat(plus.min())
                .isEqualTo(1.5);

        assertThat(plus.max())
                .isNull();

        assertThat(english.min())
                .isEqualTo(1.5);

        assertThat(english.max())
                .isNull();

        assertThat(vietnamese.min())
                .isEqualTo(1.5);

        assertThat(vietnamese.max())
                .isNull();
    }

    @Test
    void shouldParseRangesWithDifferentUnitsOnEachSide() {
        ExperienceNormalizationResult english =
                normalizer.normalize(
                        "6 months - 1 year"
                );

        ExperienceNormalizationResult vietnamese =
                normalizer.normalize(
                        "6 tháng - 1 năm"
                );

        assertThat(english.min())
                .isEqualTo(0.5);

        assertThat(english.max())
                .isEqualTo(1.0);

        assertThat(vietnamese.min())
                .isEqualTo(0.5);

        assertThat(vietnamese.max())
                .isEqualTo(1.0);
    }

    @Test
    void shouldApplySharedYearUnitInsideRangeOnly() {
        ExperienceNormalizationResult english =
                normalizer.normalize(
                        "2 - 4 years"
                );

        ExperienceNormalizationResult vietnamese =
                normalizer.normalize(
                        "2 đến 4 năm"
                );

        assertThat(english.min())
                .isEqualTo(2.0);

        assertThat(english.max())
                .isEqualTo(4.0);

        assertThat(vietnamese.min())
                .isEqualTo(2.0);

        assertThat(vietnamese.max())
                .isEqualTo(4.0);
    }

    @Test
    void shouldParseLowerAndUpperYearBounds() {
        ExperienceNormalizationResult plus =
                normalizer.normalize(
                        "3+ years"
                );

        ExperienceNormalizationResult above =
                normalizer.normalize(
                        "trên 3 năm"
                );

        ExperienceNormalizationResult under =
                normalizer.normalize(
                        "under 1 year"
                );

        ExperienceNormalizationResult upTo =
                normalizer.normalize(
                        "up to 2 years"
                );

        assertThat(plus.min())
                .isEqualTo(3.0);

        assertThat(plus.max())
                .isNull();

        assertThat(above.min())
                .isEqualTo(3.0);

        assertThat(above.max())
                .isNull();

        assertThat(under.max())
                .isEqualTo(1.0);

        assertThat(upTo.max())
                .isEqualTo(2.0);
    }

    @Test
    void shouldNormalizeCommonNoExperiencePhrases() {
        assertThat(
                normalizer.normalize(
                        "no experience required"
                ).min()
        ).isEqualTo(0.0);

        assertThat(
                normalizer.normalize(
                        "no experience"
                ).min()
        ).isEqualTo(0.0);

        assertThat(
                normalizer.normalize(
                        "không yêu cầu kinh nghiệm"
                ).min()
        ).isEqualTo(0.0);

        assertThat(
                normalizer.normalize(
                        "không cần kinh nghiệm"
                ).min()
        ).isEqualTo(0.0);

        assertThat(
                normalizer.normalize(
                        "fresher"
                ).min()
        ).isEqualTo(0.0);
    }

    @Test
    void shouldStayUnknownWhenNumericTextHasNoExperienceUnit() {
        ExperienceNormalizationResult result =
                normalizer.normalize(
                        "Có thể trao đổi sau 2 vòng phỏng vấn"
                );

        assertThat(result.known())
                .isFalse();
    }
}