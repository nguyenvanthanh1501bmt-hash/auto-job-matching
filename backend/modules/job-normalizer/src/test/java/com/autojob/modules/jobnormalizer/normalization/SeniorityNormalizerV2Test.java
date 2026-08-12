package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeniorityNormalizerV2Test {

    private SeniorityNormalizer normalizer;

    @BeforeEach
    void setUp() {
        NormalizationTaxonomyProperties taxonomy =
                TaxonomyTestLoader.load();

        normalizer =
                new SeniorityNormalizer(taxonomy);
    }

    @Test
    void shouldNormalizeMultiDomainTitles() {
        assertThat(
                normalizer.normalize(
                        null,
                        "Senior Accountant",
                        null
                )
        ).isEqualTo(SeniorityLevel.SENIOR);

        assertThat(
                normalizer.normalize(
                        null,
                        "Junior Auditor",
                        null
                )
        ).isEqualTo(SeniorityLevel.JUNIOR);

        assertThat(
                normalizer.normalize(
                        null,
                        "Sales Manager",
                        null
                )
        ).isEqualTo(SeniorityLevel.MANAGER);

        assertThat(
                normalizer.normalize(
                        null,
                        "Trưởng phòng Kinh doanh",
                        null
                )
        ).isEqualTo(SeniorityLevel.MANAGER);

        assertThat(
                normalizer.normalize(
                        null,
                        "Trưởng nhóm tuyển dụng",
                        null
                )
        ).isEqualTo(SeniorityLevel.LEAD);

        assertThat(
                normalizer.normalize(
                        null,
                        "Marketing Director",
                        null
                )
        ).isEqualTo(SeniorityLevel.DIRECTOR);
    }

    @Test
    void shouldTreatEntryLevelAsFresher() {
        assertThat(
                normalizer.normalize(
                        "Entry Level",
                        null,
                        null
                )
        ).isEqualTo(SeniorityLevel.FRESHER);

        assertThat(
                normalizer.normalize(
                        null,
                        "Mới tốt nghiệp - Kế toán",
                        null
                )
        ).isEqualTo(SeniorityLevel.FRESHER);
    }

    @Test
    void shouldTreatNoExperiencePhraseAsFresherWhenExplicit() {
        assertThat(
                normalizer.normalize(
                        "Không yêu cầu kinh nghiệm",
                        "Nhân viên bán hàng",
                        null
                )
        ).isEqualTo(SeniorityLevel.FRESHER);
    }

    @Test
    void shouldNeverInferManagerFromExperienceOnly() {
        SeniorityLevel result = normalizer.normalize(
                null,
                "Chuyên viên vận hành",
                new ExperienceNormalizationResult(
                        10.0,
                        null
                )
        );

        assertThat(result)
                .isEqualTo(SeniorityLevel.SENIOR);
    }

    @Test
    void shouldNotMisclassifyLeadGenerationAsLeadership() {
        SeniorityLevel result = normalizer.normalize(
                null,
                "Lead Generation Specialist",
                null
        );

        assertThat(result)
                .isEqualTo(SeniorityLevel.UNKNOWN);
    }

    @Test
    void shouldMapChiefAccountantToManagerNotDirector() {
        assertThat(
                normalizer.normalize(
                        null,
                        "Chief Accountant",
                        null
                )
        ).isEqualTo(SeniorityLevel.MANAGER);
    }
}