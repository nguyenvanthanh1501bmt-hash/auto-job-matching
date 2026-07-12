package com.autojob.modules.jobnormalizer.normalization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceNormalizerTest {

    private ExperienceNormalizer experienceNormalizer;

    @BeforeEach
    void setUp() {
        experienceNormalizer = new ExperienceNormalizer(
                new TextNormalizer()
        );
    }

    @Test
    void shouldNormalizeNoExperienceRequired() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize(
                        "Không yêu cầu kinh nghiệm"
                );

        assertThat(result.min()).isEqualTo(0.0);
        assertThat(result.max()).isNull();
    }

    @Test
    void shouldNormalizeFresherAsZeroExperience() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize("Fresher");

        assertThat(result.min()).isEqualTo(0.0);
        assertThat(result.max()).isNull();
    }

    @Test
    void shouldNormalizeUnderOneYear() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize("Dưới 1 năm");

        assertThat(result.min()).isEqualTo(0.0);
        assertThat(result.max()).isEqualTo(1.0);
    }

    @Test
    void shouldNormalizeExactYear() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize("1 năm");

        assertThat(result.min()).isEqualTo(1.0);
        assertThat(result.max()).isEqualTo(1.0);
    }

    @Test
    void shouldNormalizeVietnameseLowerBound() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize("Ít nhất 2 năm");

        assertThat(result.min()).isEqualTo(2.0);
        assertThat(result.max()).isNull();
    }

    @Test
    void shouldNormalizeVietnameseFromKeyword() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize("Từ 2 năm");

        assertThat(result.min()).isEqualTo(2.0);
        assertThat(result.max()).isNull();
    }

    @Test
    void shouldNormalizeVietnameseAboveKeyword() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize("Trên 3 năm");

        assertThat(result.min()).isEqualTo(3.0);
        assertThat(result.max()).isNull();
    }

    @Test
    void shouldNormalizeVietnameseYearRange() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize("2 - 4 năm");

        assertThat(result.min()).isEqualTo(2.0);
        assertThat(result.max()).isEqualTo(4.0);
    }

    @Test
    void shouldNormalizeVietnameseRangeUsingDenKeyword() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize("2 đến 4 năm");

        assertThat(result.min()).isEqualTo(2.0);
        assertThat(result.max()).isEqualTo(4.0);
    }

    @Test
    void shouldNormalizeEnglishPlusYears() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize("3+ years");

        assertThat(result.min()).isEqualTo(3.0);
        assertThat(result.max()).isNull();
    }

    @Test
    void shouldNormalizeEnglishLowerBound() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize(
                        "At least 3 years"
                );

        assertThat(result.min()).isEqualTo(3.0);
        assertThat(result.max()).isNull();
    }

    @Test
    void shouldConvertMonthsToYears() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize("24 months");

        assertThat(result.min()).isEqualTo(2.0);
        assertThat(result.max()).isNull();
    }

    @Test
    void shouldRoundMonthsToTwoDecimalPlaces() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize("37 months");

        assertThat(result.min()).isEqualTo(3.08);
        assertThat(result.max()).isNull();
    }

    @Test
    void shouldReturnUnknownForUnrecognizedText() {
        ExperienceNormalizationResult result =
                experienceNormalizer.normalize(
                        "Trao đổi khi phỏng vấn"
                );

        assertThat(result.min()).isNull();
        assertThat(result.max()).isNull();
        assertThat(result.known()).isFalse();
    }

    @Test
    void shouldReturnUnknownForNullOrBlankInput() {
        ExperienceNormalizationResult nullResult =
                experienceNormalizer.normalize(null);

        ExperienceNormalizationResult blankResult =
                experienceNormalizer.normalize("   ");

        assertThat(nullResult.min()).isNull();
        assertThat(nullResult.max()).isNull();

        assertThat(blankResult.min()).isNull();
        assertThat(blankResult.max()).isNull();
    }
}