package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobTypeNormalizerV2Test {

    private final JobTypeNormalizer normalizer =
            new JobTypeNormalizer();

    @Test
    void shouldNormalizeRequiredVietnameseAndEnglishValues() {
        assertThat(
                normalizer.normalize(
                        "full time",
                        null
                )
        ).isEqualTo(NormalizedJobType.FULL_TIME);

        assertThat(
                normalizer.normalize(
                        "nhân viên chính thức",
                        null
                )
        ).isEqualTo(NormalizedJobType.FULL_TIME);

        assertThat(
                normalizer.normalize(
                        "part-time",
                        null
                )
        ).isEqualTo(NormalizedJobType.PART_TIME);

        assertThat(
                normalizer.normalize(
                        "bán thời gian",
                        null
                )
        ).isEqualTo(NormalizedJobType.PART_TIME);

        assertThat(
                normalizer.normalize(
                        "contractor",
                        null
                )
        ).isEqualTo(NormalizedJobType.CONTRACT);

        assertThat(
                normalizer.normalize(
                        "hợp đồng",
                        null
                )
        ).isEqualTo(NormalizedJobType.CONTRACT);

        assertThat(
                normalizer.normalize(
                        "thực tập sinh",
                        null
                )
        ).isEqualTo(NormalizedJobType.INTERNSHIP);

        assertThat(
                normalizer.normalize(
                        "freelancer",
                        null
                )
        ).isEqualTo(NormalizedJobType.FREELANCE);

        assertThat(
                normalizer.normalize(
                        "seasonal",
                        null
                )
        ).isEqualTo(NormalizedJobType.TEMPORARY);

        assertThat(
                normalizer.normalize(
                        "thời vụ",
                        null
                )
        ).isEqualTo(NormalizedJobType.TEMPORARY);
    }

    @Test
    void shouldPreferExplicitJobTypeTextOverTitle() {
        assertThat(
                normalizer.normalize(
                        "hợp đồng",
                        "Nhân viên bán hàng full-time"
                )
        ).isEqualTo(NormalizedJobType.CONTRACT);
    }

    @Test
    void shouldNotInferFromGenericDescriptionLikeWordsInTitle() {
        assertThat(
                normalizer.normalize(
                        null,
                        "Nhân viên kinh doanh"
                )
        ).isEqualTo(NormalizedJobType.UNKNOWN);
    }
}