package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeniorityNormalizerTest {

    private SeniorityNormalizer seniorityNormalizer;

    @BeforeEach
    void setUp() {
        seniorityNormalizer = new SeniorityNormalizer();
    }

    @Test
    void shouldPrioritizeSeniorityTextOverTitle() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                "Junior",
                "Senior Java Developer",
                new ExperienceNormalizationResult(5.0, null)
        );

        assertThat(result).isEqualTo(SeniorityLevel.JUNIOR);
    }

    @Test
    void shouldNormalizeInternFromSeniorityText() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                "Thực tập sinh",
                "Nhân viên Marketing",
                null
        );

        assertThat(result).isEqualTo(SeniorityLevel.INTERN);
    }

    @Test
    void shouldNormalizeFresherFromTitle() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                null,
                "Fresher Kế Toán",
                null
        );

        assertThat(result).isEqualTo(SeniorityLevel.FRESHER);
    }

    @Test
    void shouldNormalizeJuniorFromTitle() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                null,
                "Junior Sales Executive",
                null
        );

        assertThat(result).isEqualTo(SeniorityLevel.JUNIOR);
    }

    @Test
    void shouldNormalizeMidFromTitle() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                null,
                "Mid-level Graphic Designer",
                null
        );

        assertThat(result).isEqualTo(SeniorityLevel.MID);
    }

    @Test
    void shouldNormalizeSeniorFromTitle() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                null,
                "Senior Java Backend Engineer",
                null
        );

        assertThat(result).isEqualTo(SeniorityLevel.SENIOR);
    }

    @Test
    void shouldNormalizeLeadFromTitle() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                null,
                "Team Lead Customer Service",
                null
        );

        assertThat(result).isEqualTo(SeniorityLevel.LEAD);
    }

    @Test
    void shouldNormalizeManagerFromVietnameseTitle() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                null,
                "Trưởng Phòng Kinh Doanh",
                null
        );

        assertThat(result).isEqualTo(SeniorityLevel.MANAGER);
    }

    @Test
    void shouldNormalizeDirectorFromTitle() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                null,
                "Director of Operations",
                null
        );

        assertThat(result).isEqualTo(SeniorityLevel.DIRECTOR);
    }

    @Test
    void shouldPreferManagerOverSeniorWhenTitleContainsBoth() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                null,
                "Senior Engineering Manager",
                null
        );

        assertThat(result).isEqualTo(SeniorityLevel.MANAGER);
    }

    @Test
    void shouldInferFresherFromZeroExperience() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                null,
                "Nhân viên",
                new ExperienceNormalizationResult(0.0, null)
        );

        assertThat(result).isEqualTo(SeniorityLevel.FRESHER);
    }

    @Test
    void shouldInferJuniorFromOneYearExperience() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                null,
                "Nhân viên",
                new ExperienceNormalizationResult(1.0, 1.0)
        );

        assertThat(result).isEqualTo(SeniorityLevel.JUNIOR);
    }

    @Test
    void shouldInferMidFromTwoYearsExperience() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                null,
                "Chuyên viên",
                new ExperienceNormalizationResult(2.0, 4.0)
        );

        assertThat(result).isEqualTo(SeniorityLevel.MID);
    }

    @Test
    void shouldInferSeniorFromFiveYearsExperience() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                null,
                "Chuyên viên",
                new ExperienceNormalizationResult(5.0, null)
        );

        assertThat(result).isEqualTo(SeniorityLevel.SENIOR);
    }

    @Test
    void shouldReturnUnknownWhenNoInformationIsAvailable() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                null,
                null,
                new ExperienceNormalizationResult(null, null)
        );

        assertThat(result).isEqualTo(SeniorityLevel.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownForUnrecognizedTextWithoutExperience() {
        SeniorityLevel result = seniorityNormalizer.normalize(
                "Nhân viên",
                "Nhân viên vận hành",
                null
        );

        assertThat(result).isEqualTo(SeniorityLevel.UNKNOWN);
    }
}