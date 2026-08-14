package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.SharedSeniorityTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeniorityNormalizerV2Test {

    private SeniorityNormalizer normalizer;

    @BeforeEach
    void setUp() {
        SharedSeniorityTaxonomyProperties taxonomy =
                TaxonomyTestLoader
                        .loadSharedSeniority();

        normalizer =
                new SeniorityNormalizer(
                        taxonomy
                );
    }

    @Test
    void shouldNormalizeMultiDomainTitles() {
        assertThat(
                normalizer.normalize(
                        null,
                        "Senior Accountant",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.SENIOR
        );

        assertThat(
                normalizer.normalize(
                        null,
                        "Junior Auditor",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.JUNIOR
        );

        assertThat(
                normalizer.normalize(
                        null,
                        "Sales Manager",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.MANAGER
        );

        assertThat(
                normalizer.normalize(
                        null,
                        "Trưởng phòng Kinh doanh",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.MANAGER
        );

        assertThat(
                normalizer.normalize(
                        null,
                        "Trưởng nhóm tuyển dụng",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.LEAD
        );

        assertThat(
                normalizer.normalize(
                        null,
                        "Marketing Director",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.DIRECTOR
        );
    }

    @Test
    void shouldKeepEntryLevelDistinctFromFresher() {
        assertThat(
                normalizer.normalize(
                        "Entry Level",
                        null,
                        null
                )
        ).isEqualTo(
                SeniorityLevel.ENTRY_LEVEL
        );

        assertThat(
                normalizer.normalize(
                        null,
                        "Mới tốt nghiệp - Kế toán",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.ENTRY_LEVEL
        );

        assertThat(
                normalizer.normalize(
                        null,
                        "Fresher Java Developer",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.FRESHER
        );
    }

    @Test
    void shouldTreatNoExperiencePhraseAsEntryLevelWhenExplicit() {
        assertThat(
                normalizer.normalize(
                        "Không yêu cầu kinh nghiệm",
                        "Nhân viên bán hàng",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.ENTRY_LEVEL
        );
    }

    @Test
    void shouldNeverInferManagerFromExperienceOnly() {
        assertThat(
                normalizer.normalize(
                        null,
                        "Chuyên viên vận hành",
                        new ExperienceNormalizationResult(
                                10.0,
                                null
                        )
                )
        ).isEqualTo(
                SeniorityLevel.SENIOR
        );
    }

    @Test
    void shouldNotMisclassifyLeadGenerationAsLeadership() {
        assertThat(
                normalizer.normalize(
                        null,
                        "Lead Generation Specialist",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.UNKNOWN
        );
    }

    @Test
    void shouldMapChiefAccountantToHead() {
        assertThat(
                normalizer.normalize(
                        null,
                        "Chief Accountant",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.HEAD
        );
    }

    @Test
    void shouldSupportSupervisor() {
        assertThat(
                normalizer.normalize(
                        null,
                        "Giám sát bán hàng",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.SUPERVISOR
        );
    }

    @Test
    void shouldSupportTrainee() {
        assertThat(
                normalizer.normalize(
                        null,
                        "Graduate Trainee",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.TRAINEE
        );
    }

    @Test
    void shouldSupportExecutive() {
        assertThat(
                normalizer.normalize(
                        null,
                        "CEO",
                        null
                )
        ).isEqualTo(
                SeniorityLevel.EXECUTIVE
        );
    }
}