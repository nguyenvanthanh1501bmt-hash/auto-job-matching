package com.autojob.modules.jobnormalizer.service;

import com.autojob.common.dtos.ApplyType;
import com.autojob.common.events.JobNormalizedReadyEvent;
import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.domain.RawPayloadPurgeResult;
import com.autojob.modules.jobcrawler.repository.RawJobRepository;
import com.autojob.modules.jobcrawler.service.RawPayloadPurgeService;
import com.autojob.modules.jobnormalizer.config.NormalizationProperties;
import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizationAction;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.exception.RawJobNotFoundException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobNormalizationServiceTest {

    private static final Instant FIXED_NOW =
            Instant.parse("2026-07-12T03:00:00Z");

    private static final Clock FIXED_CLOCK = Clock.fixed(
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

    private TextNormalizer textNormalizer;
    private RawJobContentHasher rawJobContentHasher;
    private NormalizationProperties normalizationProperties;
    private NormalizationTaxonomyProperties normalizationTaxonomyProperties;
    private JobNormalizationService jobNormalizationService;

    @BeforeEach
    void setUp() {
        textNormalizer = new TextNormalizer();

        rawJobContentHasher = new RawJobContentHasher(
                textNormalizer
        );

        normalizationProperties =
                new NormalizationProperties();

        normalizationProperties.setVersion("rule-v1");

        normalizationProperties.setTimezone(
                "Asia/Ho_Chi_Minh"
        );

        normalizationTaxonomyProperties =
                TaxonomyTestLoader.load();

        jobNormalizationService = createService(
                rawJobContentHasher,
                rawPayloadPurgeService
        );

        lenient().when(
                rawPayloadPurgeService.purgeRawPayload(
                        anyString()
                )
        ).thenAnswer(
                invocation -> new RawPayloadPurgeResult(
                        invocation.getArgument(0),
                        1,
                        1,
                        FIXED_NOW
                )
        );
    }

    @Test
    void shouldCreatePublishAndPurge() {
        RawJob rawJob = createRawJob();

        when(
                rawJobRepository.findById("raw-001")
        ).thenReturn(
                Optional.of(rawJob)
        );

        when(
                normalizedJobRepository
                        .findByRawJobIdAndNormalizationVersion(
                                "raw-001",
                                "rule-v1"
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                normalizedJobRepository.insert(
                        any(NormalizedJob.class)
                )
        ).thenAnswer(invocation -> {
            NormalizedJob inserted =
                    invocation.getArgument(0);

            inserted.setId("normalized-001");

            return inserted;
        });

        NormalizationRunResult result =
                jobNormalizationService
                        .normalizeByRawJobId(
                                "raw-001"
                        );

        assertThat(
                result.execution().action()
        ).isEqualTo(
                NormalizationAction.CREATED
        );

        assertThat(
                result.execution()
                        .normalizedJob()
                        .getId()
        ).isEqualTo(
                "normalized-001"
        );

        assertThat(
                result.execution()
                        .normalizedJob()
                        .getRawContentHash()
        ).hasSize(64);

        assertThat(
                result.execution()
                        .normalizedJob()
                        .getEmbeddingText()
        )
                .isNotBlank()
                .startsWith(
                        "query: Title: Senior Java Backend Engineer"
                )
                .contains(
                        "Skills: Java, MongoDB, Spring Boot"
                )
                .contains(
                        "Requirements: Java 21 Spring Boot 3"
                );

        assertThat(
                result.rawPayloadPurged()
        ).isTrue();

        assertThat(
                result.purgeFailed()
        ).isFalse();

        ArgumentCaptor<Object> eventCaptor =
                ArgumentCaptor.forClass(
                        Object.class
                );

        verify(
                eventPublisher
        ).publishEvent(
                eventCaptor.capture()
        );

        assertThat(
                eventCaptor.getValue()
        ).isInstanceOf(
                JobNormalizedReadyEvent.class
        );

        verify(
                rawPayloadPurgeService
        ).purgeRawPayload(
                "raw-001"
        );
    }

    @Test
    void shouldUpdateWhenBusinessContentChangesAndKeepId() {
        RawJob rawJob = createRawJob();

        String oldHash =
                rawJobContentHasher.hash(
                        rawJob
                );

        rawJob.setSalaryText(
                "50 - 60 triệu"
        );

        NormalizedJob existing =
                existingNormalizedJob(
                        oldHash
                );

        when(
                rawJobRepository.findById(
                        "raw-001"
                )
        ).thenReturn(
                Optional.of(rawJob)
        );

        when(
                normalizedJobRepository
                        .findByRawJobIdAndNormalizationVersion(
                                "raw-001",
                                "rule-v1"
                        )
        ).thenReturn(
                Optional.of(existing)
        );

        when(
                normalizedJobRepository.save(
                        existing
                )
        ).thenReturn(
                existing
        );

        NormalizationRunResult result =
                jobNormalizationService
                        .normalizeByRawJobId(
                                "raw-001"
                        );

        assertThat(
                result.execution().action()
        ).isEqualTo(
                NormalizationAction.UPDATED
        );

        assertThat(
                result.execution()
                        .normalizedJob()
                        .getId()
        ).isEqualTo(
                "normalized-existing"
        );

        assertThat(
                result.execution()
                        .normalizedJob()
                        .getRawContentHash()
        ).isNotEqualTo(
                oldHash
        );

        assertThat(
                result.execution()
                        .normalizedJob()
                        .getEmbeddingText()
        ).isNotBlank();

        verify(
                normalizedJobRepository
        ).save(
                existing
        );

        verify(
                eventPublisher
        ).publishEvent(
                any(Object.class)
        );

        verify(
                rawPayloadPurgeService
        ).purgeRawPayload(
                "raw-001"
        );
    }

    @Test
    void shouldReturnUnchangedWithoutSaveOrReadyEventButStillPurge() {
        RawJob rawJob =
                createRawJob();

        NormalizedJob existing =
                existingNormalizedJob(
                        rawJobContentHasher.hash(
                                rawJob
                        )
                );

        when(
                rawJobRepository.findById(
                        "raw-001"
                )
        ).thenReturn(
                Optional.of(rawJob)
        );

        when(
                normalizedJobRepository
                        .findByRawJobIdAndNormalizationVersion(
                                "raw-001",
                                "rule-v1"
                        )
        ).thenReturn(
                Optional.of(existing)
        );

        NormalizationRunResult result =
                jobNormalizationService
                        .normalizeByRawJobId(
                                "raw-001"
                        );

        assertThat(
                result.execution().action()
        ).isEqualTo(
                NormalizationAction.UNCHANGED
        );

        verify(
                normalizedJobRepository,
                never()
        ).save(
                any(NormalizedJob.class)
        );

        verify(
                normalizedJobRepository,
                never()
        ).insert(
                any(NormalizedJob.class)
        );

        verifyNoInteractions(
                eventPublisher
        );

        verify(
                rawPayloadPurgeService
        ).purgeRawPayload(
                "raw-001"
        );
    }

    @Test
    void shouldIgnoreMetadataAndRawPayloadChangesInHash() {
        RawJob rawJob =
                createRawJob();

        String businessHash =
                rawJobContentHasher.hash(
                        rawJob
                );

        rawJob.setFirstSeenAt(
                Instant.parse(
                        "2026-06-01T00:00:00Z"
                )
        );

        rawJob.setLastSeenAt(
                Instant.parse(
                        "2026-07-12T03:00:00Z"
                )
        );

        rawJob.setCollectedAt(
                Instant.parse(
                        "2026-07-12T03:01:00Z"
                )
        );

        rawJob.setExpiresAt(
                Instant.parse(
                        "2026-07-01T00:00:00Z"
                )
        );

        rawJob.setRawHtml(
                "<html>new payload</html>"
        );

        rawJob.setRawText(
                "new raw text"
        );

        rawJob.setRawPayloadPurgedAt(
                FIXED_NOW
        );

        NormalizedJob existing =
                existingNormalizedJob(
                        businessHash
                );

        when(
                rawJobRepository.findById(
                        "raw-001"
                )
        ).thenReturn(
                Optional.of(rawJob)
        );

        when(
                normalizedJobRepository
                        .findByRawJobIdAndNormalizationVersion(
                                "raw-001",
                                "rule-v1"
                        )
        ).thenReturn(
                Optional.of(existing)
        );

        NormalizationRunResult result =
                jobNormalizationService
                        .normalizeByRawJobId(
                                "raw-001"
                        );

        assertThat(
                result.execution().action()
        ).isEqualTo(
                NormalizationAction.UNCHANGED
        );

        verify(
                normalizedJobRepository,
                never()
        ).save(
                any(NormalizedJob.class)
        );
    }

    @Test
    void shouldCreateNewDocumentForNewVersion() {
        normalizationProperties.setVersion(
                "rule-v2"
        );

        RawJob rawJob =
                createRawJob();

        when(
                rawJobRepository.findById(
                        "raw-001"
                )
        ).thenReturn(
                Optional.of(rawJob)
        );

        when(
                normalizedJobRepository
                        .findByRawJobIdAndNormalizationVersion(
                                "raw-001",
                                "rule-v2"
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                normalizedJobRepository.insert(
                        any(NormalizedJob.class)
                )
        ).thenAnswer(invocation -> {
            NormalizedJob inserted =
                    invocation.getArgument(0);

            inserted.setId(
                    "normalized-v2"
            );

            return inserted;
        });

        NormalizationRunResult result =
                jobNormalizationService
                        .normalizeByRawJobId(
                                "raw-001"
                        );

        assertThat(
                result.execution().action()
        ).isEqualTo(
                NormalizationAction.CREATED
        );

        assertThat(
                result.execution()
                        .normalizedJob()
                        .getNormalizationVersion()
        ).isEqualTo(
                "rule-v2"
        );

        verify(
                normalizedJobRepository,
                never()
        ).delete(
                any(NormalizedJob.class)
        );
    }

    @Test
    void shouldUpdateWhenForceIsTrueEvenIfHashIsEqual() {
        RawJob rawJob =
                createRawJob();

        NormalizedJob existing =
                existingNormalizedJob(
                        rawJobContentHasher.hash(
                                rawJob
                        )
                );

        when(
                rawJobRepository.findById(
                        "raw-001"
                )
        ).thenReturn(
                Optional.of(rawJob)
        );

        when(
                normalizedJobRepository
                        .findByRawJobIdAndNormalizationVersion(
                                "raw-001",
                                "rule-v1"
                        )
        ).thenReturn(
                Optional.of(existing)
        );

        when(
                normalizedJobRepository.save(
                        existing
                )
        ).thenReturn(
                existing
        );

        NormalizationRunResult result =
                jobNormalizationService
                        .normalizeByRawJobId(
                                "raw-001",
                                true
                        );

        assertThat(
                result.execution().action()
        ).isEqualTo(
                NormalizationAction.UPDATED
        );

        verify(
                normalizedJobRepository
        ).save(
                existing
        );

        verify(
                eventPublisher
        ).publishEvent(
                any(Object.class)
        );
    }

    @Test
    void shouldRecoverFromDuplicateKeyRaceWithoutDuplicate() {
        RawJob rawJob =
                createRawJob();

        NormalizedJob concurrent =
                existingNormalizedJob(
                        rawJobContentHasher.hash(
                                rawJob
                        )
                );

        when(
                rawJobRepository.findById(
                        "raw-001"
                )
        ).thenReturn(
                Optional.of(rawJob)
        );

        when(
                normalizedJobRepository
                        .findByRawJobIdAndNormalizationVersion(
                                "raw-001",
                                "rule-v1"
                        )
        ).thenReturn(
                Optional.empty(),
                Optional.of(concurrent)
        );

        when(
                normalizedJobRepository.insert(
                        any(NormalizedJob.class)
                )
        ).thenThrow(
                new DuplicateKeyException(
                        "duplicate"
                )
        );

        NormalizationRunResult result =
                jobNormalizationService
                        .normalizeByRawJobId(
                                "raw-001"
                        );

        assertThat(
                result.execution().action()
        ).isEqualTo(
                NormalizationAction.UNCHANGED
        );

        assertThat(
                result.execution()
                        .normalizedJob()
                        .getId()
        ).isEqualTo(
                "normalized-existing"
        );

        verify(
                normalizedJobRepository,
                never()
        ).save(
                any(NormalizedJob.class)
        );

        verifyNoInteractions(
                eventPublisher
        );
    }

    @Test
    void shouldNotPurgeWhenNormalizationFails() {
        RawJob rawJob =
                createRawJob();

        RawJobContentHasher throwingHasher =
                mock(
                        RawJobContentHasher.class
                );

        JobNormalizationService service =
                createService(
                        throwingHasher,
                        rawPayloadPurgeService
                );

        when(
                rawJobRepository.findById(
                        "raw-001"
                )
        ).thenReturn(
                Optional.of(rawJob)
        );

        when(
                throwingHasher.hash(
                        rawJob
                )
        ).thenThrow(
                new IllegalStateException(
                        "hash failed"
                )
        );

        assertThatThrownBy(
                () -> service.normalizeByRawJobId(
                        "raw-001"
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "hash failed"
                );

        verify(
                rawPayloadPurgeService,
                never()
        ).purgeRawPayload(
                anyString()
        );

        verifyNoInteractions(
                eventPublisher
        );
    }

    @Test
    void shouldKeepNormalizationSuccessfulWhenPurgeFails() {
        RawJob rawJob =
                createRawJob();

        when(
                rawJobRepository.findById(
                        "raw-001"
                )
        ).thenReturn(
                Optional.of(rawJob)
        );

        when(
                normalizedJobRepository
                        .findByRawJobIdAndNormalizationVersion(
                                "raw-001",
                                "rule-v1"
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                normalizedJobRepository.insert(
                        any(NormalizedJob.class)
                )
        ).thenAnswer(invocation -> {
            NormalizedJob inserted =
                    invocation.getArgument(0);

            inserted.setId(
                    "normalized-001"
            );

            return inserted;
        });

        doThrow(
                new IllegalStateException(
                        "purge failed"
                )
        )
                .when(
                        rawPayloadPurgeService
                )
                .purgeRawPayload(
                        "raw-001"
                );

        NormalizationRunResult result =
                jobNormalizationService
                        .normalizeByRawJobId(
                                "raw-001"
                        );

        assertThat(
                result.execution().action()
        ).isEqualTo(
                NormalizationAction.CREATED
        );

        assertThat(
                result.purgeFailed()
        ).isTrue();

        assertThat(
                result.purgeError()
        ).isEqualTo(
                "purge failed"
        );

        verify(
                eventPublisher
        ).publishEvent(
                any(Object.class)
        );
    }

    @Test
    void shouldThrowWhenRawJobDoesNotExist() {
        when(
                rawJobRepository.findById(
                        "missing"
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () -> jobNormalizationService
                        .normalizeByRawJobId(
                                "missing"
                        )
        ).isInstanceOf(
                RawJobNotFoundException.class
        );

        verify(
                rawPayloadPurgeService,
                never()
        ).purgeRawPayload(
                anyString()
        );
    }

    private JobNormalizationService createService(
            RawJobContentHasher hasher,
            RawPayloadPurgeService purgeService
    ) {
        SalaryNormalizer salaryNormalizer =
                new SalaryNormalizer(
                        textNormalizer,
                        normalizationTaxonomyProperties
                );

        JobEmbeddingTextBuilder embeddingTextBuilder =
                new JobEmbeddingTextBuilder(
                        textNormalizer,
                        normalizationProperties
                );

        return new JobNormalizationService(
                rawJobRepository,
                normalizedJobRepository,
                textNormalizer,
                salaryNormalizer,
                new SkillNormalizer(
                        textNormalizer,
                        normalizationTaxonomyProperties
                ),
                new LocationNormalizer(
                        textNormalizer,
                        normalizationTaxonomyProperties
                ),
                new ExperienceNormalizer(
                        textNormalizer,
                        normalizationTaxonomyProperties
                ),
                new SeniorityNormalizer(
                        normalizationTaxonomyProperties
                ),
                new JobTypeNormalizer(
                        normalizationTaxonomyProperties
                ),
                new DateNormalizer(
                        textNormalizer,
                        normalizationTaxonomyProperties,
                        FIXED_CLOCK
                ),
                new ApplyInformationNormalizer(
                        textNormalizer
                ),
                hasher,
                embeddingTextBuilder,
                normalizationProperties,
                eventPublisher,
                purgeService,
                FIXED_CLOCK
        );
    }

    private NormalizedJob existingNormalizedJob(
            String hash
    ) {
        return NormalizedJob.builder()
                .id("normalized-existing")
                .rawJobId("raw-001")
                .sourceCode("MOCK")
                .normalizationVersion("rule-v1")
                .rawContentHash(hash)
                .build();
    }

    private RawJob createRawJob() {
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
                .applyType(
                        ApplyType.DETAIL_PAGE
                )
                .title(
                        "Senior Java Backend Engineer"
                )
                .companyName(
                        "AutoJob Labs"
                )
                .salaryText(
                        "30 - 45 triệu"
                )
                .locationText(
                        "TP.HCM / Remote"
                )
                .experienceText(
                        "5+ years"
                )
                .seniorityText(
                        "Senior"
                )
                .jobTypeText(
                        "FULL_TIME"
                )
                .deadlineText(
                        "2026-07-30"
                )
                .postedText(
                        "Hôm nay"
                )
                .skills(
                        List.of(
                                "Java",
                                "springboot",
                                "mongo db"
                        )
                )
                .descriptionText(
                        "Build scalable backend services."
                )
                .requirementsText(
                        "Java 21\nSpring Boot 3"
                )
                .benefitsText(
                        "Remote friendly"
                )
                .fingerprint(
                        "MOCK:java-backend"
                )
                .rawHtml(
                        "<html>payload</html>"
                )
                .rawText(
                        "payload"
                )
                .collectedAt(
                        FIXED_NOW
                )
                .build();
    }
}