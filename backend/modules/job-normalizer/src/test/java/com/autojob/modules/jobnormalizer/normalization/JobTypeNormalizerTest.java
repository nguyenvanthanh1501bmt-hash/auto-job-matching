package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobTypeNormalizerTest {

    private JobTypeNormalizer jobTypeNormalizer;

    @BeforeEach
    void setUp() {
        NormalizationTaxonomyProperties taxonomy =
                TaxonomyTestLoader.load();

        jobTypeNormalizer =
                new JobTypeNormalizer(taxonomy);
    }

    @Test
    void shouldPrioritizeJobTypeTextOverTitle() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "PART_TIME",
                "Full-time Sales Executive"
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.PART_TIME);
    }

    @Test
    void shouldNormalizeSchemaOrgFullTimeValue() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "FULL_TIME",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.FULL_TIME);
    }

    @Test
    void shouldNormalizeEnglishFullTimeText() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "Full-time",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.FULL_TIME);
    }

    @Test
    void shouldNormalizeVietnameseFullTimeText() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "Toàn thời gian",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.FULL_TIME);
    }

    @Test
    void shouldNormalizeSchemaOrgPartTimeValue() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "PART_TIME",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.PART_TIME);
    }

    @Test
    void shouldNormalizeVietnamesePartTimeText() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "Bán thời gian",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.PART_TIME);
    }

    @Test
    void shouldNormalizeSchemaOrgContractorValue() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "CONTRACTOR",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.CONTRACT);
    }

    @Test
    void shouldNormalizeVietnameseContractText() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "Hợp đồng",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.CONTRACT);
    }

    @Test
    void shouldNormalizeInternshipText() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "Internship",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.INTERNSHIP);
    }

    @Test
    void shouldNormalizeVietnameseInternshipText() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "Thực tập sinh",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.INTERNSHIP);
    }

    @Test
    void shouldNormalizeFreelanceText() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "Freelance",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.FREELANCE);
    }

    @Test
    void shouldNormalizeVietnameseFreelanceText() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "Cộng tác viên",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.FREELANCE);
    }

    @Test
    void shouldNormalizeTemporaryText() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "TEMPORARY",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.TEMPORARY);
    }

    @Test
    void shouldNormalizeVietnameseTemporaryText() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "Thời vụ",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.TEMPORARY);
    }

    @Test
    void shouldUseTitleAsFallbackWhenJobTypeTextIsMissing() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                null,
                "Thực Tập Sinh Marketing"
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.INTERNSHIP);
    }

    @Test
    void shouldUseTitleAsFallbackWhenJobTypeTextIsUnknown() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "OTHER",
                "Part-time Content Writer"
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.PART_TIME);
    }

    @Test
    void shouldReturnUnknownForSchemaOrgOtherValue() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "OTHER",
                null
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownForUnrecognizedText() {
        NormalizedJobType result = jobTypeNormalizer.normalize(
                "Trao đổi khi phỏng vấn",
                "Nhân viên kinh doanh"
        );

        assertThat(result)
                .isEqualTo(NormalizedJobType.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownForNullOrBlankInput() {
        NormalizedJobType nullResult =
                jobTypeNormalizer.normalize(null, null);

        NormalizedJobType blankResult =
                jobTypeNormalizer.normalize("   ", "   ");

        assertThat(nullResult)
                .isEqualTo(NormalizedJobType.UNKNOWN);

        assertThat(blankResult)
                .isEqualTo(NormalizedJobType.UNKNOWN);
    }
}