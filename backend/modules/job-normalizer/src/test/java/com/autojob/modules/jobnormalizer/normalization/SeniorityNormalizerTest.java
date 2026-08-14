package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.SharedSeniorityTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeniorityNormalizerTest {

    private SeniorityNormalizer seniorityNormalizer;

    @BeforeEach
    void setUp() {
        SharedSeniorityTaxonomyProperties taxonomy =
                TaxonomyTestLoader
                        .loadSharedSeniority();

        seniorityNormalizer =
                new SeniorityNormalizer(
                        taxonomy
                );
    }

    @Test
    void shouldPrioritizeSeniorityTextOverTitle() {
        SeniorityLevel result =
                seniorityNormalizer.normalize(
                        "Junior",
                        "Senior Java Developer",
                        new ExperienceNormalizationResult(
                                5.0,
                                null
                        )
                );

        assertThat(
                result
        ).isEqualTo(
                SeniorityLevel.JUNIOR
        );
    }

    @Test
    void shouldNormalizeInternFromSeniorityText() {
        assertThat(
                seniorityNormalizer.normalize(
                        "Thực tập sinh",
                        "Nhân viên Marketing",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.INTERN
        );
    }

    @Test
    void shouldNormalizeTraineeExplicitly() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Management Trainee",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.TRAINEE
        );
    }

    @Test
    void shouldNormalizeFresherExplicitly() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Fresher Kế Toán",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.FRESHER
        );
    }

    @Test
    void shouldNormalizeEntryLevelExplicitly() {
        assertThat(
                seniorityNormalizer.normalize(
                        "Entry Level",
                        null,
                        null
                )
        ).isEqualTo(
                SeniorityLevel.ENTRY_LEVEL
        );

        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Mới tốt nghiệp - Kế toán",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.ENTRY_LEVEL
        );
    }

    @Test
    void shouldNormalizeJuniorFromTitle() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Junior Sales Executive",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.JUNIOR
        );
    }

    @Test
    void shouldNormalizeMidFromTitle() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Mid-level Graphic Designer",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.MID
        );
    }

    @Test
    void shouldNormalizeSeniorFromTitle() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Senior Java Backend Engineer",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.SENIOR
        );
    }

    @Test
    void shouldNormalizeLeadFromTitle() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Team Lead Customer Service",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.LEAD
        );
    }

    @Test
    void shouldNormalizeSupervisor() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Shift Supervisor",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.SUPERVISOR
        );
    }

    @Test
    void shouldNormalizeManagerFromVietnameseTitle() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Trưởng Phòng Kinh Doanh",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.MANAGER
        );
    }

    @Test
    void shouldNormalizeHeadSeparatelyFromManager() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Chief Accountant",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.HEAD
        );
    }

    @Test
    void shouldNormalizeDirectorFromTitle() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Director of Operations",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.DIRECTOR
        );
    }

    @Test
    void shouldNormalizeExecutive() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Chief Executive Officer",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.EXECUTIVE
        );

        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Tổng Giám Đốc",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.EXECUTIVE
        );
    }

    @Test
    void shouldPreferManagerOverSeniorWhenTitleContainsBoth() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Senior Engineering Manager",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.MANAGER
        );
    }

    @Test
    void shouldInferEntryLevelFromZeroExperience() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Nhân viên",
                        new ExperienceNormalizationResult(
                                0.0,
                                null
                        )
                )
        ).isEqualTo(
                SeniorityLevel.ENTRY_LEVEL
        );
    }

    @Test
    void shouldInferEntryLevelFromPointTwoYears() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Nhân viên",
                        new ExperienceNormalizationResult(
                                0.2,
                                0.2
                        )
                )
        ).isEqualTo(
                SeniorityLevel.ENTRY_LEVEL
        );
    }

    @Test
    void shouldInferJuniorFromOneYearExperience() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Nhân viên",
                        new ExperienceNormalizationResult(
                                1.0,
                                1.0
                        )
                )
        ).isEqualTo(
                SeniorityLevel.JUNIOR
        );
    }

    @Test
    void shouldInferMidFromTwoYearsExperience() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Chuyên viên",
                        new ExperienceNormalizationResult(
                                2.0,
                                4.0
                        )
                )
        ).isEqualTo(
                SeniorityLevel.MID
        );
    }

    @Test
    void shouldInferSeniorFromFiveYearsExperience() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Chuyên viên",
                        new ExperienceNormalizationResult(
                                5.0,
                                null
                        )
                )
        ).isEqualTo(
                SeniorityLevel.SENIOR
        );
    }

    @Test
    void shouldNotInferLeadershipFromExperienceOnly() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Chuyên viên",
                        new ExperienceNormalizationResult(
                                15.0,
                                null
                        )
                )
        ).isEqualTo(
                SeniorityLevel.SENIOR
        );
    }

    @Test
    void shouldNotMisclassifyLeadGeneration() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Lead Generation Specialist",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.UNKNOWN
        );
    }

    @Test
    void shouldAllowLeadGenerationLeader() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        "Lead Generation Leader",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.LEAD
        );
    }

    @Test
    void shouldReturnUnknownWhenNoInformationIsAvailable() {
        assertThat(
                seniorityNormalizer.normalize(
                        null,
                        null,
                        new ExperienceNormalizationResult(
                                null,
                                null
                        )
                )
        ).isEqualTo(
                SeniorityLevel.UNKNOWN
        );
    }

    @Test
    void shouldReturnUnknownForUnrecognizedTextWithoutExperience() {
        assertThat(
                seniorityNormalizer.normalize(
                        "Nhân viên",
                        "Nhân viên vận hành",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.UNKNOWN
        );
    }
}