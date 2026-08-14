package com.autojob.modules.jobnormalizer.service;

import com.autojob.common.dtos.ApplyType;
import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.domain.RawPayloadPurgeResult;
import com.autojob.modules.jobcrawler.repository.RawJobRepository;
import com.autojob.modules.jobcrawler.service.RawPayloadPurgeService;
import com.autojob.modules.jobnormalizer.config.NormalizationProperties;
import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import com.autojob.modules.jobnormalizer.normalization.ApplyInformationNormalizer;
import com.autojob.modules.jobnormalizer.normalization.DateNormalizer;
import com.autojob.modules.jobnormalizer.normalization.ExperienceNormalizer;
import com.autojob.modules.jobnormalizer.normalization.JobEmbeddingTextBuilder;
import com.autojob.modules.jobnormalizer.normalization.JobTypeNormalizer;
import com.autojob.modules.jobnormalizer.normalization.LocationNormalizer;
import com.autojob.modules.jobnormalizer.normalization.RawJobContentHasher;
import com.autojob.modules.jobnormalizer.normalization.SalaryNormalizer;
import com.autojob.modules.jobnormalizer.normalization.SeniorityNormalizer;
import com.autojob.modules.jobnormalizer.normalization.SkillNormalizer;
import com.autojob.modules.jobnormalizer.normalization.TextNormalizer;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobNormalizationServiceMultiDomainTest {

    private static final Instant FIXED_NOW =
            Instant.parse("2026-07-12T03:00:00Z");

    private static final Clock FIXED_CLOCK =
            Clock.fixed(
                    FIXED_NOW,
                    ZoneId.of("Asia/Ho_Chi_Minh")
            );

    @Mock
    private RawJobRepository rawJobRepository;

    @Mock
    private NormalizedJobRepository normalizedJobRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RawPayloadPurgeService rawPayloadPurgeService;

    private JobNormalizationService service;

    @BeforeEach
    void setUp() {
        TextNormalizer textNormalizer =
                new TextNormalizer();

        NormalizationProperties properties =
                new NormalizationProperties();

        properties.setVersion(
                "rule-v2"
        );

        properties.setTimezone(
                "Asia/Ho_Chi_Minh"
        );

        NormalizationTaxonomyProperties taxonomy =
                TaxonomyTestLoader.load();

        service =
                new JobNormalizationService(
                        rawJobRepository,
                        normalizedJobRepository,
                        textNormalizer,

                        new SalaryNormalizer(
                                textNormalizer,
                                taxonomy
                        ),

                        /*
                         * Skill dùng shared taxonomy.
                         */
                        new SkillNormalizer(
                                textNormalizer,
                                TaxonomyTestLoader
                                        .loadSharedSkills()
                        ),

                        new LocationNormalizer(
                                textNormalizer,
                                TaxonomyTestLoader.loadSharedLocations()
                        ),

                        new ExperienceNormalizer(
                                textNormalizer,
                                taxonomy
                        ),

                        new SeniorityNormalizer(
                        TaxonomyTestLoader.loadSharedSeniority()
                ),

                        new JobTypeNormalizer(
                                taxonomy
                        ),

                        new DateNormalizer(
                                textNormalizer,
                                taxonomy,
                                FIXED_CLOCK
                        ),

                        new ApplyInformationNormalizer(
                                textNormalizer
                        ),

                        new RawJobContentHasher(
                                textNormalizer
                        ),

                        /*
                         * Legacy normalized-job embedding text
                         * vẫn còn tạm trong normalizer.
                         *
                         * JobEmbeddingService mới không dùng
                         * field này nữa.
                         */
                        new JobEmbeddingTextBuilder(
                                textNormalizer,
                                properties
                        ),

                        properties,
                        eventPublisher,
                        rawPayloadPurgeService,
                        FIXED_CLOCK
                );

        when(
                normalizedJobRepository.insert(
                        any(
                                NormalizedJob.class
                        )
                )
        ).thenAnswer(
                invocation -> {
                    NormalizedJob job =
                            invocation.getArgument(
                                    0
                            );

                    job.setId(
                            "normalized-"
                                    + job.getRawJobId()
                    );

                    return job;
                }
        );

        when(
                rawPayloadPurgeService
                        .purgeRawPayload(
                                anyString()
                        )
        ).thenAnswer(
                invocation ->
                        new RawPayloadPurgeResult(
                                invocation.getArgument(
                                        0
                                ),
                                1,
                                1,
                                FIXED_NOW
                        )
        );
    }

    @Test
    void shouldNormalizeJobsAcrossMultipleIndustries() {
        NormalizedJob it =
                normalize(
                        rawJob(
                                "it",
                                "Senior Java Backend Engineer",
                                List.of(
                                        "Java",
                                        "Spring Boot",
                                        "PostgreSQL"
                                ),
                                "5+ years",
                                "TP.HCM / Remote"
                        )
                );

        assertThat(
                it.getSkills()
        ).containsExactly(
                "Java",
                "Spring Boot",
                "PostgreSQL"
        );

        assertThat(
                it.getSeniority()
        ).isEqualTo(
                SeniorityLevel.SENIOR
        );

        assertThat(
                it.getLocations()
        ).containsExactly(
                "Hồ Chí Minh"
        );

        NormalizedJob accounting =
                normalize(
                        rawJob(
                                "accounting",
                                "Kế toán tổng hợp",
                                List.of(
                                        "misa",
                                        "Kế toán thuế",
                                        "Báo cáo tài chính"
                                ),
                                "2 - 4 năm",
                                "Bình Dương"
                        )
                );

        /*
         * Shared skill taxonomy:
         *
         * "Báo cáo tài chính"
         *     ↓
         * "Lập báo cáo tài chính"
         *
         * Job và CV từ đây dùng cùng canonical.
         */
        assertThat(
                accounting.getSkills()
        ).containsExactly(
                "MISA",
                "Kế toán thuế",
                "Lập báo cáo tài chính"
        );

        assertThat(
                accounting.getSeniority()
        ).isEqualTo(
                SeniorityLevel.MID
        );

        assertThat(
                accounting.getEmbeddingText()
        )
                .contains(
                        "Title: Kế toán tổng hợp"
                )
                .contains(
                        "Company: Công ty Test"
                )
                .contains(
                        "MISA"
                )
                .contains(
                        "Kế toán thuế"
                )
                .contains(
                        "Lập báo cáo tài chính"
                );

        NormalizedJob sales =
                normalize(
                        rawJob(
                                "sales",
                                "Trưởng phòng Kinh doanh",
                                List.of(
                                        "B2B Sales",
                                        "CRM",
                                        "Negotiation"
                                ),
                                "5 năm",
                                "Hà Nội"
                        )
                );

        assertThat(
                sales.getSeniority()
        ).isEqualTo(
                SeniorityLevel.MANAGER
        );

        assertThat(
                sales.getSkills()
        ).contains(
                "B2B Sales",
                "Customer Relationship Management",
                "Negotiation"
        );

        NormalizedJob manufacturing =
                normalize(
                        rawJob(
                                "manufacturing",
                                "Kỹ sư CNC",
                                List.of(
                                        "CNC",
                                        "AutoCAD",
                                        "đọc bản vẽ kỹ thuật"
                                ),
                                "3 năm",
                                "Bắc Ninh"
                        )
                );

        assertThat(
                manufacturing.getSkills()
        ).containsExactly(
                "CNC",
                "AutoCAD",
                "Đọc bản vẽ kỹ thuật"
        );

        assertThat(
                manufacturing.getLocations()
        ).containsExactly(
                "Bắc Ninh"
        );

        NormalizedJob healthcare =
                normalize(
                        rawJob(
                                "healthcare",
                                "Điều dưỡng viên",
                                List.of(
                                        "Chăm sóc bệnh nhân",
                                        "Điều dưỡng"
                                ),
                                "18 tháng",
                                "Đồng Nai"
                        )
                );

        assertThat(
                healthcare.getSkills()
        ).containsExactly(
                "Chăm sóc bệnh nhân",
                "Điều dưỡng"
        );

        assertThat(
                healthcare.getExperienceMin()
        ).isEqualTo(
                1.5
        );

        assertThat(
                healthcare.getJobType()
        ).isEqualTo(
                NormalizedJobType.FULL_TIME
        );
    }

    @Test
    void shouldUseSkillFallbackWithoutBreakingHashDrivenIdempotencyInputs() {
        RawJob rawJob =
                rawJob(
                        "fallback-accounting",
                        "Senior Accountant",
                        List.of(),
                        "5 years",
                        "HCM"
                );

        rawJob.setRequirementsText(
                "Yêu cầu MISA, IFRS và lập báo cáo tài chính"
        );

        NormalizedJob normalized =
                normalize(
                        rawJob
                );

        assertThat(
                normalized.getSkills()
        ).contains(
                "MISA",
                "IFRS",
                "Lập báo cáo tài chính"
        );

        assertThat(
                normalized.getRawContentHash()
        ).hasSize(
                64
        );
    }

    private NormalizedJob normalize(
            RawJob rawJob
    ) {
        when(
                rawJobRepository.findById(
                        rawJob.getId()
                )
        ).thenReturn(
                Optional.of(
                        rawJob
                )
        );

        when(
                normalizedJobRepository
                        .findByRawJobIdAndNormalizationVersion(
                                rawJob.getId(),
                                "rule-v2"
                        )
        ).thenReturn(
                Optional.empty()
        );

        return service
                .normalizeByRawJobId(
                        rawJob.getId()
                )
                .execution()
                .normalizedJob();
    }

    private RawJob rawJob(
            String id,
            String title,
            List<String> skills,
            String experience,
            String location
    ) {
        return RawJob.builder()
                .id(id)
                .sourceCode("TEST")
                .sourceJobId(id)
                .fingerprint(
                        "TEST:" + id
                )
                .title(title)
                .companyName(
                        "Công ty Test"
                )
                .skills(skills)
                .experienceText(
                        experience
                )
                .locationText(
                        location
                )
                .jobTypeText(
                        "nhân viên chính thức"
                )
                .salaryText(
                        "20tr - 30tr"
                )
                .postedText(
                        "2 hours ago"
                )
                .deadlineText(
                        "in 3 days"
                )
                .descriptionText(
                        "Mô tả công việc"
                )
                .requirementsText(
                        "Yêu cầu công việc"
                )
                .benefitsText(
                        "Phúc lợi"
                )
                .detailUrl(
                        "https://jobs.example.com/"
                                + id
                )
                .applyUrl(
                        "https://company.example.com/apply/"
                                + id
                )
                .applyType(
                        ApplyType.UNKNOWN
                )
                .build();
    }
}