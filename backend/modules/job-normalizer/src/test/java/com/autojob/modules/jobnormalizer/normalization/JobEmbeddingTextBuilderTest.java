package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobEmbeddingTextBuilderTest {

    private NormalizationProperties properties;
    private JobEmbeddingTextBuilder builder;

    @BeforeEach
    void setUp() {
        properties = new NormalizationProperties();
        properties.setEmbeddingTextMaxChars(2_400);
        properties.setEmbeddingDescriptionMaxChars(600);
        properties.setEmbeddingRequirementsMaxChars(600);
        properties.setEmbeddingBenefitsMaxChars(300);

        builder = new JobEmbeddingTextBuilder(
                new TextNormalizer(),
                properties
        );
    }

    @Test
    void shouldBuildAllSupportedFieldsInStableOrder() {
        NormalizedJob job = completeJob();

        String result = builder.build(job);

        assertThat(result).isEqualTo(
                """
                query: Title: Senior Java Backend Engineer
                Skills: Java, MongoDB, Spring Boot
                Seniority: Senior
                Experience: 4 - 6 years
                Locations: Đà Nẵng, Hà Nội, Hồ Chí Minh
                Job type: Full time
                Requirements: Thành thạo Java và Spring Boot
                Description: Xây dựng hệ thống tuyển dụng
                Benefits: Làm việc từ xa
                """.strip()
        );
    }

    @Test
    void shouldNotContainNullAndShouldSkipBlankFields() {
        NormalizedJob job = NormalizedJob.builder()
                .title("Java Engineer")
                .skills(List.of("", "Java", "   "))
                .locations(List.of())
                .seniority(SeniorityLevel.UNKNOWN)
                .jobType(NormalizedJobType.UNKNOWN)
                .descriptionText("   ")
                .requirementsText(null)
                .benefitsText(null)
                .build();

        String result = builder.build(job);

        assertThat(result)
                .isEqualTo(
                        """
                        query: Title: Java Engineer
                        Skills: Java
                        """.strip()
                )
                .doesNotContain("null")
                .doesNotContain("Seniority:")
                .doesNotContain("Locations:")
                .doesNotContain("Description:");
    }

    @Test
    void shouldSortAndDeduplicateSkillsDeterministically() {
        NormalizedJob firstJob = NormalizedJob.builder()
                .title("Backend Engineer")
                .skills(List.of(
                        "Spring Boot",
                        "Java",
                        "MongoDB",
                        "Java"
                ))
                .build();

        NormalizedJob secondJob = NormalizedJob.builder()
                .title("Backend Engineer")
                .skills(List.of(
                        "MongoDB",
                        "Java",
                        "Spring Boot"
                ))
                .build();

        assertThat(builder.build(firstJob))
                .isEqualTo(builder.build(secondJob))
                .contains("Skills: Java, MongoDB, Spring Boot");
    }

    @Test
    void shouldSortAndDeduplicateLocationsDeterministically() {
        NormalizedJob firstJob = NormalizedJob.builder()
                .title("Backend Engineer")
                .locations(List.of(
                        "Hồ Chí Minh",
                        "Đà Nẵng",
                        "Hà Nội",
                        "Hồ Chí Minh"
                ))
                .build();

        NormalizedJob secondJob = NormalizedJob.builder()
                .title("Backend Engineer")
                .locations(List.of(
                        "Hà Nội",
                        "Hồ Chí Minh",
                        "Đà Nẵng"
                ))
                .build();

        assertThat(builder.build(firstJob))
                .isEqualTo(builder.build(secondJob))
                .contains(
                        "Locations: Đà Nẵng, Hà Nội, Hồ Chí Minh"
                );
    }

    @Test
    void shouldProduceSameTextForSameNormalizedContent() {
        NormalizedJob firstJob = completeJob();
        NormalizedJob secondJob = completeJob();

        assertThat(builder.build(firstJob))
                .isEqualTo(builder.build(secondJob));
    }

    @Test
    void shouldChangeTextWhenBusinessContentChanges() {
        NormalizedJob job = completeJob();
        String original = builder.build(job);

        job.setRequirementsText(
                "Thành thạo Java, Spring Boot và Kafka"
        );

        assertThat(builder.build(job))
                .isNotEqualTo(original)
                .contains("Kafka");
    }

    @Test
    void shouldIgnoreIdentifiersAndCrawlerMetadata() {
        NormalizedJob job = completeJob();
        String original = builder.build(job);

        job.setId("normalized-job-changed");
        job.setRawJobId("raw-job-changed");
        job.setSourceCode("OTHER_SOURCE");
        job.setSourceJobId("source-job-changed");
        job.setSourceFingerprint("fingerprint-changed");
        job.setRawContentHash("hash-changed");
        job.setNormalizationVersion("rule-v99");
        job.setNormalizedAt(
                Instant.parse("2030-01-01T00:00:00Z")
        );
        job.setPostedAt(
                Instant.parse("2030-01-02T00:00:00Z")
        );
        job.setDeadlineAt(
                Instant.parse("2030-01-03T00:00:00Z")
        );

        assertThat(builder.build(job))
                .isEqualTo(original)
                .doesNotContain("OTHER_SOURCE")
                .doesNotContain("rule-v99")
                .doesNotContain("2030");
    }

    @Test
    void shouldPreserveVietnameseUnicode() {
        NormalizedJob job = NormalizedJob.builder()
                .title("Kỹ sư phần mềm")
                .skills(List.of("Trí tuệ nhân tạo"))
                .locations(List.of("Thành phố Hồ Chí Minh"))
                .requirementsText(
                        "Có khả năng đọc hiểu tài liệu tiếng Việt"
                )
                .build();

        assertThat(builder.build(job))
                .contains("Kỹ sư phần mềm")
                .contains("Trí tuệ nhân tạo")
                .contains("Thành phố Hồ Chí Minh")
                .contains(
                        "Có khả năng đọc hiểu tài liệu tiếng Việt"
                );
    }

    @Test
    void shouldTruncateDescriptionUsingConfiguredLimit() {
        properties.setEmbeddingDescriptionMaxChars(100);

        NormalizedJob job = NormalizedJob.builder()
                .title("Java Engineer")
                .descriptionText("x".repeat(200))
                .build();

        String result = builder.build(job);

        assertThat(result)
                .contains(
                        "Description: "
                                + "x".repeat(99)
                                + "…"
                )
                .doesNotContain("x".repeat(100));
    }

    @Test
    void shouldPrioritizeRequirementsBeforeDescriptionAndBenefits() {
        properties.setEmbeddingTextMaxChars(500);

        NormalizedJob job = NormalizedJob.builder()
                .title("Senior Backend Engineer")
                .requirementsText("R".repeat(300))
                .descriptionText("D".repeat(500))
                .benefitsText("B".repeat(500))
                .build();

        String result = builder.build(job);

        assertThat(result)
                .contains("Requirements:")
                .contains("Description:")
                .doesNotContain("Benefits:");

        assertThat(result.indexOf("Requirements:"))
                .isLessThan(result.indexOf("Description:"));
    }

    @Test
    void shouldRespectTotalTextLengthLimit() {
        properties.setEmbeddingTextMaxChars(500);

        NormalizedJob job = NormalizedJob.builder()
                .title("T".repeat(1_000))
                .descriptionText("D".repeat(1_000))
                .build();

        String result = builder.build(job);

        assertThat(result)
                .hasSize(500)
                .startsWith("query: Title: ")
                .endsWith("…")
                .doesNotContain("Description:");
    }

    @Test
    void shouldKeepFieldOrderStable() {
        String result = builder.build(completeJob());

        assertThat(result.indexOf("Title:"))
                .isLessThan(result.indexOf("Skills:"));

        assertThat(result.indexOf("Skills:"))
                .isLessThan(result.indexOf("Seniority:"));

        assertThat(result.indexOf("Seniority:"))
                .isLessThan(result.indexOf("Experience:"));

        assertThat(result.indexOf("Experience:"))
                .isLessThan(result.indexOf("Locations:"));

        assertThat(result.indexOf("Locations:"))
                .isLessThan(result.indexOf("Job type:"));

        assertThat(result.indexOf("Job type:"))
                .isLessThan(result.indexOf("Requirements:"));

        assertThat(result.indexOf("Requirements:"))
                .isLessThan(result.indexOf("Description:"));

        assertThat(result.indexOf("Description:"))
                .isLessThan(result.indexOf("Benefits:"));
    }

    @Test
    void shouldReturnNullWhenNoBusinessContentExists() {
        NormalizedJob job = NormalizedJob.builder()
                .skills(List.of())
                .locations(List.of())
                .seniority(SeniorityLevel.UNKNOWN)
                .jobType(NormalizedJobType.UNKNOWN)
                .build();

        assertThat(builder.build(job)).isNull();
    }

    private NormalizedJob completeJob() {
        return NormalizedJob.builder()
                .id("normalized-job-1")
                .rawJobId("raw-job-1")
                .sourceCode("MOCK")
                .sourceJobId("mock-job-1")
                .sourceFingerprint("fingerprint")
                .rawContentHash("content-hash")
                .title("Senior Java Backend Engineer")
                .skills(List.of(
                        "Spring Boot",
                        "Java",
                        "MongoDB",
                        "Java"
                ))
                .locations(List.of(
                        "Hồ Chí Minh",
                        "Đà Nẵng",
                        "Hà Nội"
                ))
                .experienceMin(4.0)
                .experienceMax(6.0)
                .seniority(SeniorityLevel.SENIOR)
                .jobType(NormalizedJobType.FULL_TIME)
                .descriptionText(
                        "Xây dựng hệ thống tuyển dụng"
                )
                .requirementsText(
                        "Thành thạo Java và Spring Boot"
                )
                .benefitsText("Làm việc từ xa")
                .normalizationVersion("rule-v1")
                .normalizedAt(
                        Instant.parse("2026-07-21T00:00:00Z")
                )
                .build();
    }
}