package com.autojob.modules.jobnormalizer.service;

import com.autojob.common.dtos.ApplyType;
import com.autojob.common.events.JobNormalizedReadyEvent;
import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.repository.RawJobRepository;
import com.autojob.modules.jobnormalizer.config.NormalizationProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import com.autojob.modules.jobnormalizer.exception.RawJobNotFoundException;
import com.autojob.modules.jobnormalizer.normalization.ApplyInformationNormalizer;
import com.autojob.modules.jobnormalizer.normalization.DateNormalizer;
import com.autojob.modules.jobnormalizer.normalization.ExperienceNormalizer;
import com.autojob.modules.jobnormalizer.normalization.JobTypeNormalizer;
import com.autojob.modules.jobnormalizer.normalization.LocationNormalizer;
import com.autojob.modules.jobnormalizer.normalization.RawJobContentHasher;
import com.autojob.modules.jobnormalizer.normalization.SalaryNormalizer;
import com.autojob.modules.jobnormalizer.normalization.SeniorityNormalizer;
import com.autojob.modules.jobnormalizer.normalization.SkillNormalizer;
import com.autojob.modules.jobnormalizer.normalization.TextNormalizer;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobNormalizationServiceTest {

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

    private JobNormalizationService jobNormalizationService;

    @BeforeEach
    void setUp() {
        TextNormalizer textNormalizer =
                new TextNormalizer();

        SalaryNormalizer salaryNormalizer =
                new SalaryNormalizer(textNormalizer);

        SkillNormalizer skillNormalizer =
                new SkillNormalizer(textNormalizer);

        LocationNormalizer locationNormalizer =
                new LocationNormalizer(textNormalizer);

        ExperienceNormalizer experienceNormalizer =
                new ExperienceNormalizer(textNormalizer);

        SeniorityNormalizer seniorityNormalizer =
                new SeniorityNormalizer();

        JobTypeNormalizer jobTypeNormalizer =
                new JobTypeNormalizer();

        DateNormalizer dateNormalizer =
                new DateNormalizer(
                        textNormalizer,
                        FIXED_CLOCK
                );

        ApplyInformationNormalizer applyInformationNormalizer =
                new ApplyInformationNormalizer(
                        textNormalizer
                );

        RawJobContentHasher rawJobContentHasher =
                new RawJobContentHasher(textNormalizer);

        NormalizationProperties properties =
                new NormalizationProperties();

        properties.setVersion("rule-v1");
        properties.setTimezone("Asia/Ho_Chi_Minh");

        jobNormalizationService =
                new JobNormalizationService(
                        rawJobRepository,
                        normalizedJobRepository,
                        textNormalizer,
                        salaryNormalizer,
                        skillNormalizer,
                        locationNormalizer,
                        experienceNormalizer,
                        seniorityNormalizer,
                        jobTypeNormalizer,
                        dateNormalizer,
                        applyInformationNormalizer,
                        rawJobContentHasher,
                        properties,
                        eventPublisher,
                        FIXED_CLOCK
                );
    }

    @Test
    void shouldNormalizeRawJobSaveResultAndPublishReadyEvent() {
        RawJob rawJob = createCompleteRawJob();

        when(rawJobRepository.findById("raw-001"))
                .thenReturn(Optional.of(rawJob));

        when(
                normalizedJobRepository
                        .findByRawJobIdAndNormalizationVersion(
                                "raw-001",
                                "rule-v1"
                        )
        ).thenReturn(Optional.empty());

        when(normalizedJobRepository.save(any(NormalizedJob.class)))
                .thenAnswer(invocation -> {
                    NormalizedJob job = invocation.getArgument(0);
                    job.setId("normalized-001");
                    return job;
                });

        NormalizedJob result =
                jobNormalizationService
                        .normalizeByRawJobId("raw-001");

        assertThat(result.getId())
                .isEqualTo("normalized-001");

        assertThat(result.getRawJobId())
                .isEqualTo("raw-001");

        assertThat(result.getSourceCode())
                .isEqualTo("MOCK");

        assertThat(result.getSourceJobId())
                .isEqualTo("java-backend");

        assertThat(result.getTitle())
                .isEqualTo("Senior Java Backend Engineer");

        assertThat(result.getCompanyName())
                .isEqualTo("AutoJob Labs");

        assertThat(result.getSkills()).containsExactly(
                "Java",
                "Spring Boot",
                "MongoDB",
                "Vận hành hệ thống"
        );

        assertThat(result.getLocations()).containsExactly(
                "Ho Chi Minh",
                "Remote"
        );

        assertThat(result.getSalaryText())
                .isEqualTo("30 - 45 triệu");

        assertThat(result.getSalaryMin())
                .isEqualTo(30_000_000L);

        assertThat(result.getSalaryMax())
                .isEqualTo(45_000_000L);

        assertThat(result.getCurrency())
                .isEqualTo("VND");

        assertThat(result.getExperienceMin())
                .isEqualTo(5.0);

        assertThat(result.getExperienceMax())
                .isNull();

        assertThat(result.getSeniority())
                .isEqualTo(SeniorityLevel.SENIOR);

        assertThat(result.getJobType())
                .isEqualTo(NormalizedJobType.FULL_TIME);

        assertThat(result.getApplyUrl())
                .isEqualTo(
                        "https://company.example.com/apply/java"
                );

        assertThat(result.getApplyType())
                .isEqualTo(
                        ApplyType.EXTERNAL_COMPANY_SITE
                );

        assertThat(result.getPostedAt())
                .isEqualTo(
                        Instant.parse("2026-07-11T17:00:00Z")
                );

        assertThat(result.getDeadlineAt())
                .isEqualTo(
                        Instant.parse(
                                "2026-07-30T16:59:59.999999999Z"
                        )
                );

        assertThat(result.getNormalizationVersion())
                .isEqualTo("rule-v1");

        assertThat(result.getNormalizedAt())
                .isEqualTo(FIXED_NOW);

        /*
         * Hôm nay chưa làm embedding.
         */
        assertThat(result.getEmbeddingText())
                .isNull();

        assertThat(result.getRawContentHash())
                .isNotBlank()
                .hasSize(64);

        /*
         * Normalizer chỉ đọc raw job, không ghi đè raw_jobs.
         */
        verify(rawJobRepository, never())
                .save(any(RawJob.class));

        ArgumentCaptor<Object> eventCaptor =
                ArgumentCaptor.forClass(Object.class);

        verify(eventPublisher)
                .publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getValue())
                .isInstanceOf(
                        JobNormalizedReadyEvent.class
                );

        JobNormalizedReadyEvent event =
                (JobNormalizedReadyEvent)
                        eventCaptor.getValue();

        assertThat(event.getNormalizedJobId())
                .isEqualTo("normalized-001");

        assertThat(event.getRawJobId())
                .isEqualTo("raw-001");

        assertThat(event.getSourceCode())
                .isEqualTo("MOCK");

        assertThat(event.getNormalizationVersion())
                .isEqualTo("rule-v1");

        assertThat(event.getOccurredAt())
                .isEqualTo(FIXED_NOW);
    }

    @Test
    void shouldThrowWhenRawJobDoesNotExist() {
        when(rawJobRepository.findById("missing-raw-job"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> jobNormalizationService
                        .normalizeByRawJobId(
                                "missing-raw-job"
                        )
        )
                .isInstanceOf(
                        RawJobNotFoundException.class
                )
                .hasMessage(
                        "Raw job not found: missing-raw-job"
                );

        verify(normalizedJobRepository, never())
                .save(any(NormalizedJob.class));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldUpdateExistingNormalizedJobInsteadOfCreatingDuplicate() {
        RawJob rawJob = createCompleteRawJob();

        NormalizedJob existing = NormalizedJob.builder()
                .id("normalized-existing")
                .rawJobId("raw-001")
                .normalizationVersion("rule-v1")
                .title("Old title")
                .skills(List.of("Old skill"))
                .build();

        when(rawJobRepository.findById("raw-001"))
                .thenReturn(Optional.of(rawJob));

        when(
                normalizedJobRepository
                        .findByRawJobIdAndNormalizationVersion(
                                "raw-001",
                                "rule-v1"
                        )
        ).thenReturn(Optional.of(existing));

        when(normalizedJobRepository.save(existing))
                .thenReturn(existing);

        NormalizedJob result =
                jobNormalizationService
                        .normalizeByRawJobId("raw-001");

        assertThat(result.getId())
                .isEqualTo("normalized-existing");

        assertThat(result.getTitle())
                .isEqualTo(
                        "Senior Java Backend Engineer"
                );

        assertThat(result.getSkills()).containsExactly(
                "Java",
                "Spring Boot",
                "MongoDB",
                "Vận hành hệ thống"
        );

        verify(normalizedJobRepository)
                .save(existing);

        verify(eventPublisher)
                .publishEvent(any(Object.class));
    }

    @Test
    void shouldRemainIdempotentWhenNormalizingSameRawJobTwice() {
        RawJob rawJob = createCompleteRawJob();

        NormalizedJob existing = NormalizedJob.builder()
                .id("normalized-001")
                .rawJobId("raw-001")
                .normalizationVersion("rule-v1")
                .build();

        when(rawJobRepository.findById("raw-001"))
                .thenReturn(Optional.of(rawJob));

        when(
                normalizedJobRepository
                        .findByRawJobIdAndNormalizationVersion(
                                "raw-001",
                                "rule-v1"
                        )
        ).thenReturn(
                Optional.empty(),
                Optional.of(existing)
        );

        when(normalizedJobRepository.save(any(NormalizedJob.class)))
                .thenAnswer(invocation -> {
                    NormalizedJob job = invocation.getArgument(0);

                    if (job.getId() == null) {
                        job.setId("normalized-001");
                    }

                    return job;
                });

        NormalizedJob first =
                jobNormalizationService
                        .normalizeByRawJobId("raw-001");

        NormalizedJob second =
                jobNormalizationService
                        .normalizeByRawJobId("raw-001");

        assertThat(first.getId())
                .isEqualTo("normalized-001");

        assertThat(second.getId())
                .isEqualTo("normalized-001");

        verify(normalizedJobRepository, times(2))
                .save(any(NormalizedJob.class));

        verify(eventPublisher, times(2))
                .publishEvent(any(Object.class));
    }

    @Test
    void shouldHandleNullOptionalFieldsSafely() {
        RawJob rawJob = RawJob.builder()
                .id("raw-null-fields")
                .sourceCode("MOCK")
                .sourceJobId("null-fields")
                .fingerprint("MOCK:null-fields")
                .build();

        when(rawJobRepository.findById("raw-null-fields"))
                .thenReturn(Optional.of(rawJob));

        when(
                normalizedJobRepository
                        .findByRawJobIdAndNormalizationVersion(
                                "raw-null-fields",
                                "rule-v1"
                        )
        ).thenReturn(Optional.empty());

        when(normalizedJobRepository.save(any(NormalizedJob.class)))
                .thenAnswer(invocation -> {
                    NormalizedJob job = invocation.getArgument(0);
                    job.setId("normalized-null-fields");
                    return job;
                });

        NormalizedJob result =
                jobNormalizationService
                        .normalizeByRawJobId(
                                "raw-null-fields"
                        );

        assertThat(result.getId())
                .isEqualTo("normalized-null-fields");

        assertThat(result.getTitle()).isNull();
        assertThat(result.getCompanyName()).isNull();

        assertThat(result.getSkills()).isEmpty();
        assertThat(result.getLocations()).isEmpty();

        assertThat(result.getSalaryMin()).isNull();
        assertThat(result.getSalaryMax()).isNull();
        assertThat(result.getCurrency()).isNull();

        assertThat(result.getExperienceMin()).isNull();
        assertThat(result.getExperienceMax()).isNull();

        assertThat(result.getSeniority())
                .isEqualTo(SeniorityLevel.UNKNOWN);

        assertThat(result.getJobType())
                .isEqualTo(NormalizedJobType.UNKNOWN);

        assertThat(result.getApplyUrl()).isNull();

        assertThat(result.getApplyType())
                .isEqualTo(ApplyType.UNKNOWN);

        assertThat(result.getPostedAt()).isNull();
        assertThat(result.getDeadlineAt()).isNull();

        assertThat(result.getEmbeddingText()).isNull();
    }

    @Test
    void shouldRejectBlankRawJobIdBeforeQueryingRepository() {
        assertThatThrownBy(
                () -> jobNormalizationService
                        .normalizeByRawJobId("   ")
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "rawJobId must not be blank"
                );

        verifyNoInteractions(rawJobRepository);
        verifyNoInteractions(normalizedJobRepository);
        verifyNoInteractions(eventPublisher);
    }

    private RawJob createCompleteRawJob() {
        return RawJob.builder()
                .id("raw-001")
                .sourceCode("MOCK")
                .sourceJobId("java-backend")
                .sourceUrl(
                        "http://localhost:18080/jobs.html"
                )
                .listUrl(
                        "http://localhost:18080/jobs.html"
                )
                .detailUrl(
                        "http://localhost:18080/jobs/java-backend.html"
                )
                .applyUrl(
                        "https://company.example.com/apply/java"
                )
                .applyType(ApplyType.DETAIL_PAGE)
                .title(
                        "  Senior   Java Backend Engineer "
                )
                .companyName("  AutoJob Labs  ")
                .salaryText("30 - 45 triệu")
                .locationText("TP.HCM / Remote")
                .experienceText("5+ years")
                .seniorityText("Senior")
                .jobTypeText("FULL_TIME")
                .deadlineText("2026-07-30")
                .postedText("Hôm nay")
                .skills(
                        List.of(
                                "Java",
                                "springboot",
                                "mongo db",
                                "Vận hành hệ thống"
                        )
                )
                .descriptionText(
                        "  Build scalable backend services.  "
                )
                .requirementsText(
                        " Java 21\nSpring Boot 3 "
                )
                .benefitsText(
                        " Remote friendly "
                )
                .fingerprint("MOCK:java-backend")
                .collectedAt(FIXED_NOW)
                .build();
    }
}