package com.autojob.modules.jobnormalizer.normalization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillNormalizerV2Test {

    private SkillNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new SkillNormalizer(
                new TextNormalizer()
        );
    }

    @Test
    void shouldCanonicalizeMultiDomainRawSkillsWithoutUsingWhitelist() {
        List<String> result = normalizer.normalize(List.of(
                "java",
                "springboot",
                "misa",
                "Kế toán thuế",
                "Telesales",
                "B2B Sales",
                "CNC",
                "auto cad",
                "Điều dưỡng",
                "Incoterms",
                "Xuất nhập khẩu"
        ));

        assertThat(result).containsExactly(
                "Java",
                "Spring Boot",
                "MISA",
                "Kế toán thuế",
                "Telesales",
                "B2B Sales",
                "CNC",
                "AutoCAD",
                "Điều dưỡng",
                "Incoterms",
                "Import/Export"
        );
    }

    @Test
    void shouldAlwaysKeepUnknownRawSkill() {
        List<String> result = normalizer.normalize(List.of(
                "Kỹ thuật vận hành máy ABC"
        ));

        assertThat(result).containsExactly(
                "Kỹ thuật vận hành máy ABC"
        );
    }

    @Test
    void shouldSupplementKnownSkillsFromProseWhenRawSkillsAreEmpty() {
        List<String> result = normalizer.normalize(
                List.of(),
                "Kế toán tổng hợp",
                "Sử dụng MISA, IFRS và lập báo cáo tài chính.",
                "Phối hợp với bộ phận kế toán thuế."
        );

        assertThat(result)
                .contains("MISA")
                .contains("Kế toán tổng hợp")
                .contains("Kế toán thuế")
                .contains("Lập báo cáo tài chính")
                .contains("IFRS");
    }

    @Test
    void shouldSupplementButNotReplaceSingleUnknownRawSkill() {
        List<String> result = normalizer.normalize(
                List.of("Kỹ thuật vận hành máy ABC"),
                "Kỹ sư sản xuất",
                "Yêu cầu AutoCAD, CNC, 5S và Kaizen",
                null
        );

        assertThat(result.getFirst()).isEqualTo(
                "Kỹ thuật vận hành máy ABC"
        );

        assertThat(result)
                .contains(
                        "AutoCAD",
                        "CNC",
                        "5S",
                        "Kaizen"
                );
    }

    @Test
    void shouldExtractKnownSkillsAcrossNonItDomains() {
        List<String> result = normalizer.normalize(
                null,
                "Nhân viên Logistics",
                "Có kinh nghiệm Incoterms, khai báo hải quan và quản lý kho",
                "Ưu tiên hiểu Supply Chain và xuất nhập khẩu"
        );

        assertThat(result)
                .contains("Incoterms")
                .contains("Customs Declaration")
                .contains("Warehouse Management")
                .contains("Supply Chain")
                .contains("Import/Export");
    }

    @Test
    void shouldNotTreatShortAmbiguousAliasesAsSkillsInFreeProse() {
        List<String> result = normalizer.normalize(
                List.of(),
                "Go to Market Specialist",
                "Work with HR and AI policy documents. Candidate can go to clients.",
                "Knowledge of JavaScript is useful."
        );

        assertThat(result)
                .contains("JavaScript")
                .doesNotContain("Go")
                .doesNotContain("Artificial Intelligence");
    }

    @Test
    void shouldNotExtractJavaFromJavaScriptSubstring() {
        List<String> result = normalizer.normalize(
                List.of(),
                null,
                "Strong JavaScript skills",
                null
        );

        assertThat(result)
                .contains("JavaScript")
                .doesNotContain("Java");
    }
}