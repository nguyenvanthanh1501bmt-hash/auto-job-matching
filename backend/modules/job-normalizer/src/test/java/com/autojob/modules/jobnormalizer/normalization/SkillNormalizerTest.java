package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.SharedSkillTaxonomyProperties;
import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillNormalizerTest {

    private SkillNormalizer skillNormalizer;

    @BeforeEach
    void setUp() {
        SharedSkillTaxonomyProperties taxonomy =
                TaxonomyTestLoader
                        .loadSharedSkills();

        skillNormalizer =
                new SkillNormalizer(
                        new TextNormalizer(),
                        taxonomy
                );
    }

    @Test
    void shouldNormalizeKnownAliases() {
        List<String> result =
                skillNormalizer.normalize(
                        List.of(
                                "springboot",
                                "java script",
                                "node.js",
                                "mongo db",
                                "postgres",
                                "k8s",
                                "amazon web services"
                        )
                );

        assertThat(result)
                .containsExactly(
                        "Spring Boot",
                        "JavaScript",
                        "Node.js",
                        "MongoDB",
                        "PostgreSQL",
                        "Kubernetes",
                        "AWS"
                );
    }

    @Test
    void shouldNormalizeCommonNonItAliases() {
        List<String> result =
                skillNormalizer.normalize(
                        List.of(
                                "excel",
                                "ms word",
                                "power point",
                                "photoshop",
                                "adwords",
                                "meta ads",
                                "seo",
                                "crm"
                        )
                );

        assertThat(result)
                .containsExactly(
                        "Microsoft Excel",
                        "Microsoft Word",
                        "Microsoft PowerPoint",
                        "Adobe Photoshop",
                        "Google Ads",
                        "Facebook Ads",
                        "Search Engine Optimization",
                        "Customer Relationship Management"
                );
    }

    @Test
    void shouldKeepUnknownSkillsFromAnyIndustry() {
        List<String> result =
                skillNormalizer.normalize(
                        List.of(
                                "Vận hành máy CNC",
                                "Kế toán tổng hợp",
                                "Điều dưỡng nội khoa",
                                "Kỹ thuật hàn TIG",
                                "Phần mềm MISA",
                                "Nghiệp vụ xuất nhập khẩu"
                        )
                );

        assertThat(result)
                .containsExactly(
                        "Vận hành máy CNC",
                        "Kế toán tổng hợp",
                        "Điều dưỡng nội khoa",
                        "Kỹ thuật hàn TIG",
                        "Phần mềm MISA",
                        "Nghiệp vụ xuất nhập khẩu"
                );
    }

    @Test
    void shouldSplitSkillGroupsBySafeSeparators() {
        List<String> result =
                skillNormalizer.normalize(
                        List.of(
                                "Spring Boot, Java; MongoDB|Docker\nGit"
                        )
                );

        assertThat(result)
                .containsExactly(
                        "Spring Boot",
                        "Java",
                        "MongoDB",
                        "Docker",
                        "Git"
                );
    }

    @Test
    void shouldPreserveSlashInsideSkillNames() {
        List<String> result =
                skillNormalizer.normalize(
                        List.of(
                                "UI/UX, Import/Export, B2B/B2C, CI/CD"
                        )
                );

        assertThat(result)
                .containsExactly(
                        "UI/UX",
                        "Import/Export",
                        "B2B/B2C",
                        "CI/CD"
                );
    }

    @Test
    void shouldResolveKnownAliasInsideCompositeLabel() {
        List<String> result =
                skillNormalizer.normalize(
                        List.of(
                                "Tư vấn/ Chăm sóc khách hàng",
                                "CI/CD",
                                "Import/Export"
                        )
                );

        assertThat(result)
                .containsExactly(
                        "Customer Service",
                        "CI/CD",
                        "Import/Export"
                );
    }

    @Test
    void shouldKeepUnknownRawSkillEvenWhenItContainsKnownProseAlias() {
        List<String> result =
                skillNormalizer.normalize(
                        List.of(
                                "Vận hành máy CNC",
                                "Kỹ thuật hàn TIG",
                                "Phần mềm MISA"
                        )
                );

        assertThat(result)
                .containsExactly(
                        "Vận hành máy CNC",
                        "Kỹ thuật hàn TIG",
                        "Phần mềm MISA"
                );
    }

    @Test
    void shouldRemoveDuplicatesIgnoringCaseAndAliases() {
        List<String> result =
                skillNormalizer.normalize(
                        List.of(
                                "springboot",
                                "SPRING-BOOT",
                                "Spring Boot",
                                "mongo db",
                                "MongoDB",
                                "MONGODB"
                        )
                );

        assertThat(result)
                .containsExactly(
                        "Spring Boot",
                        "MongoDB"
                );
    }

    @Test
    void shouldRemoveDuplicatesIgnoringVietnameseDiacritics() {
        List<String> result =
                skillNormalizer.normalize(
                        List.of(
                                "Vận hành máy CNC",
                                "van hanh may cnc",
                                "Kỹ năng giao tiếp",
                                "ky nang giao tiep"
                        )
                );

        assertThat(result)
                .containsExactly(
                        "Vận hành máy CNC",
                        "Communication"
                );
    }

    @Test
    void shouldTrimAndCollapseWhitespace() {
        List<String> result =
                skillNormalizer.normalize(
                        List.of(
                                "  Vận hành   máy CNC  ",
                                "  Kế toán\t tổng hợp "
                        )
                );

        assertThat(result)
                .containsExactly(
                        "Vận hành máy CNC",
                        "Kế toán tổng hợp"
                );
    }

    @Test
    void shouldIgnoreBlankSkillValues() {
        List<String> result =
                skillNormalizer.normalize(
                        List.of(
                                "Java",
                                " ",
                                ", ; |",
                                "MongoDB"
                        )
                );

        assertThat(result)
                .containsExactly(
                        "Java",
                        "MongoDB"
                );
    }

    @Test
    void shouldReturnEmptyListForNullOrEmptyInput() {
        assertThat(
                skillNormalizer.normalize(
                        null
                )
        ).isEmpty();

        assertThat(
                skillNormalizer.normalize(
                        List.of()
                )
        ).isEmpty();
    }
}