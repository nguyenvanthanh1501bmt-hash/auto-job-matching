package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobEmbeddingTextBuilderV2Test {

    @Test
    void shouldBuildMeaningfulEmbeddingTextForNonItJobIncludingCompany() {
        NormalizationProperties properties =
                new NormalizationProperties();

        properties.setEmbeddingTextMaxChars(2_400);
        properties.setEmbeddingDescriptionMaxChars(6_000);
        properties.setEmbeddingRequirementsMaxChars(4_000);
        properties.setEmbeddingBenefitsMaxChars(2_000);

        JobEmbeddingTextBuilder builder =
                new JobEmbeddingTextBuilder(
                        new TextNormalizer(),
                        properties
                );

        NormalizedJob job = NormalizedJob.builder()
                .title("Senior Accountant")
                .companyName("Công ty ABC")
                .skills(List.of(
                        "MISA",
                        "Kế toán thuế",
                        "IFRS"
                ))
                .seniority(SeniorityLevel.SENIOR)
                .experienceMin(5.0)
                .locations(List.of("Hồ Chí Minh"))
                .jobType(NormalizedJobType.FULL_TIME)
                .requirementsText(
                        "Lập báo cáo tài chính và quyết toán thuế"
                )
                .descriptionText(
                        "Phụ trách kế toán tổng hợp"
                )
                .build();

        String result = builder.build(job);

        assertThat(result)
                .startsWith(
                        "query: Title: Senior Accountant"
                )
                .contains(
                        "Company: Công ty ABC"
                )
                .contains(
                        "Skills: IFRS, Kế toán thuế, MISA"
                )
                .contains(
                        "Seniority: Senior"
                )
                .contains(
                        "Experience: From 5 years"
                )
                .contains(
                        "Locations: Hồ Chí Minh"
                )
                .contains(
                        "Job type: Full time"
                )
                .contains(
                        "Lập báo cáo tài chính"
                )
                .contains(
                        "Phụ trách kế toán tổng hợp"
                );
    }
}