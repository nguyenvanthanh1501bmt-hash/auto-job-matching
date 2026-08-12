package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobcrawler.domain.RawJob;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RawJobContentHasherV2Test {

    private final RawJobContentHasher hasher =
            new RawJobContentHasher(
                    new TextNormalizer()
            );

    @Test
    void shouldHashAllFieldsUsedBySkillFallback() {
        RawJob rawJob = RawJob.builder()
                .title("Kế toán tổng hợp")
                .skills(List.of())
                .requirementsText("Sử dụng MISA")
                .descriptionText("Lập báo cáo tài chính")
                .build();

        String original = hasher.hash(rawJob);

        rawJob.setRequirementsText(
                "Sử dụng MISA và IFRS"
        );

        assertThat(hasher.hash(rawJob))
                .isNotEqualTo(original);

        rawJob.setRequirementsText(
                "Sử dụng MISA"
        );

        rawJob.setDescriptionText(
                "Lập báo cáo tài chính và kế toán thuế"
        );

        assertThat(hasher.hash(rawJob))
                .isNotEqualTo(original);

        rawJob.setDescriptionText(
                "Lập báo cáo tài chính"
        );

        rawJob.setTitle(
                "Senior Accountant"
        );

        assertThat(hasher.hash(rawJob))
                .isNotEqualTo(original);
    }
}