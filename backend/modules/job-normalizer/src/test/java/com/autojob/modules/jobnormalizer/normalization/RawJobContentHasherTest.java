package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.common.dtos.ApplyType;
import com.autojob.modules.jobcrawler.domain.RawJob;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RawJobContentHasherTest {

    private final RawJobContentHasher hasher =
            new RawJobContentHasher(new TextNormalizer());

    @Test
    void shouldIgnoreMetadataAndRawPayloadFields() {
        RawJob rawJob = businessRawJob();
        String originalHash = hasher.hash(rawJob);

        rawJob.setFirstSeenAt(
                Instant.parse("2026-01-01T00:00:00Z")
        );
        rawJob.setLastSeenAt(
                Instant.parse("2026-02-01T00:00:00Z")
        );
        rawJob.setCollectedAt(
                Instant.parse("2026-03-01T00:00:00Z")
        );
        rawJob.setExpiresAt(
                Instant.parse("2026-04-01T00:00:00Z")
        );
        rawJob.setRawHtml("<html>changed</html>");
        rawJob.setRawText("changed");
        rawJob.setRawPayloadPurgedAt(
                Instant.parse("2026-03-01T00:00:01Z")
        );
        rawJob.setSourceCode("OTHER");
        rawJob.setSourceJobId("other-id");

        assertThat(hasher.hash(rawJob))
                .isEqualTo(originalHash);
    }

    @Test
    void shouldChangeWhenBusinessContentChanges() {
        RawJob rawJob = businessRawJob();
        String originalHash = hasher.hash(rawJob);

        rawJob.setSalaryText("40 - 50 triệu");

        assertThat(hasher.hash(rawJob))
                .isNotEqualTo(originalHash);

        rawJob = businessRawJob();
        originalHash = hasher.hash(rawJob);
        rawJob.setDescriptionText("Different description");

        assertThat(hasher.hash(rawJob))
                .isNotEqualTo(originalHash);

        rawJob = businessRawJob();
        originalHash = hasher.hash(rawJob);
        rawJob.setSkills(List.of("Java", "Kafka"));

        assertThat(hasher.hash(rawJob))
                .isNotEqualTo(originalHash);
    }

    private RawJob businessRawJob() {
        return RawJob.builder()
                .sourceCode("MOCK")
                .sourceJobId("job-1")
                .title("Java Engineer")
                .companyName("AutoJob")
                .salaryText("30 - 40 triệu")
                .locationText("Ho Chi Minh")
                .experienceText("3 years")
                .seniorityText("Senior")
                .jobTypeText("FULL_TIME")
                .deadlineText("2026-08-01")
                .postedText("2026-07-01")
                .skills(List.of("Java", "Spring Boot"))
                .descriptionText("Build services")
                .requirementsText("Java 21")
                .benefitsText("Remote")
                .detailUrl("https://example.test/jobs/1")
                .applyUrl("https://example.test/apply/1")
                .applyType(
                        ApplyType.EXTERNAL_COMPANY_SITE
                )
                .build();
    }
}