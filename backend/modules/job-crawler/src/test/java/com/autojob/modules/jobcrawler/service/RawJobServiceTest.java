package com.autojob.modules.jobcrawler.service;

import com.autojob.common.events.JobRawCollectedEvent;
import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.repository.RawJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.index.Indexed;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RawJobServiceTest {

    @Mock
    private RawJobRepository rawJobRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void shouldInitializeFirstSeenAndExpiryOnInsert() {
        RawJobService service = new RawJobService(
                rawJobRepository,
                eventPublisher
        );

        Instant observedAt = Instant.parse(
                "2026-07-01T00:00:00Z"
        );

        RawJob incoming = incomingJob(observedAt);

        when(rawJobRepository.findByFingerprint("MOCK:job-1"))
                .thenReturn(Optional.empty());

        when(rawJobRepository.save(incoming))
                .thenAnswer(invocation -> {
                    RawJob saved = invocation.getArgument(0);
                    saved.setId("raw-1");
                    return saved;
                });

        RawJob saved = service.upsertSeen(incoming, 30);

        assertThat(saved.getFirstSeenAt()).isEqualTo(observedAt);
        assertThat(saved.getLastSeenAt()).isEqualTo(observedAt);
        assertThat(saved.getCollectedAt()).isEqualTo(observedAt);
        assertThat(saved.getExpiresAt()).isEqualTo(
                observedAt.plus(30, ChronoUnit.DAYS)
        );

        ArgumentCaptor<Object> eventCaptor =
                ArgumentCaptor.forClass(Object.class);

        verify(eventPublisher).publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getValue())
                .isInstanceOf(JobRawCollectedEvent.class);
    }

    @Test
    void shouldUpdateBusinessContentWithoutRefreshingFirstSeenOrExpiry() {
        RawJobService service = new RawJobService(
                rawJobRepository,
                eventPublisher
        );

        Instant firstSeenAt = Instant.parse(
                "2026-07-01T00:00:00Z"
        );

        Instant expiresAt = firstSeenAt.plus(
                30,
                ChronoUnit.DAYS
        );

        RawJob existing = RawJob.builder()
                .id("raw-1")
                .sourceCode("MOCK")
                .sourceJobId("job-1")
                .fingerprint("MOCK:job-1")
                .title("Old title")
                .salaryText("10 triệu")
                .firstSeenAt(firstSeenAt)
                .lastSeenAt(firstSeenAt)
                .collectedAt(firstSeenAt)
                .expiresAt(expiresAt)
                .build();

        Instant observedAt = Instant.parse(
                "2026-07-20T00:00:00Z"
        );

        RawJob incoming = incomingJob(observedAt);
        incoming.setTitle("New title");
        incoming.setSalaryText("20 triệu");
        incoming.setExpiresAt(
                observedAt.plus(30, ChronoUnit.DAYS)
        );

        when(rawJobRepository.findByFingerprint("MOCK:job-1"))
                .thenReturn(Optional.of(existing));

        when(rawJobRepository.save(existing))
                .thenReturn(existing);

        RawJob saved = service.upsertSeen(incoming, 30);

        assertThat(saved.getId()).isEqualTo("raw-1");
        assertThat(saved.getTitle()).isEqualTo("New title");
        assertThat(saved.getSalaryText()).isEqualTo("20 triệu");
        assertThat(saved.getFirstSeenAt()).isEqualTo(firstSeenAt);
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(saved.getLastSeenAt()).isEqualTo(observedAt);
        assertThat(saved.getCollectedAt()).isEqualTo(observedAt);

        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void shouldKeepExpiryAcrossRepeatedCrawls() {
        RawJobService service = new RawJobService(
                rawJobRepository,
                eventPublisher
        );

        Instant firstSeenAt = Instant.parse(
                "2026-07-01T00:00:00Z"
        );

        Instant originalExpiresAt = firstSeenAt.plus(
                30,
                ChronoUnit.DAYS
        );

        RawJob existing = RawJob.builder()
                .id("raw-1")
                .sourceCode("MOCK")
                .sourceJobId("job-1")
                .fingerprint("MOCK:job-1")
                .firstSeenAt(firstSeenAt)
                .expiresAt(originalExpiresAt)
                .build();

        RawJob incomingDayFive = incomingJob(
                Instant.parse("2026-07-05T00:00:00Z")
        );

        RawJob incomingDayTwenty = incomingJob(
                Instant.parse("2026-07-20T00:00:00Z")
        );

        when(rawJobRepository.findByFingerprint("MOCK:job-1"))
                .thenReturn(
                        Optional.of(existing),
                        Optional.of(existing)
                );

        when(rawJobRepository.save(existing))
                .thenReturn(existing);

        service.upsertSeen(incomingDayFive, 30);
        service.upsertSeen(incomingDayTwenty, 30);

        assertThat(existing.getExpiresAt())
                .isEqualTo(originalExpiresAt);

        assertThat(existing.getFirstSeenAt())
                .isEqualTo(firstSeenAt);

        assertThat(existing.getLastSeenAt()).isEqualTo(
                Instant.parse("2026-07-20T00:00:00Z")
        );
    }

    @Test
    void shouldDeclareAbsoluteDateTtlIndex() throws Exception {
        Field expiresAtField = RawJob.class.getDeclaredField(
                "expiresAt"
        );

        Indexed indexed = expiresAtField.getAnnotation(
                Indexed.class
        );

        assertThat(indexed).isNotNull();
        assertThat(indexed.name())
                .isEqualTo("idx_raw_jobs_expires_at_ttl");
        assertThat(indexed.expireAfter()).isEqualTo("0s");
    }

    private RawJob incomingJob(Instant observedAt) {
        return RawJob.builder()
                .sourceCode("MOCK")
                .sourceJobId("job-1")
                .fingerprint("MOCK:job-1")
                .title("Title")
                .salaryText("10 triệu")
                .descriptionText("Description")
                .rawHtml("<html>payload</html>")
                .rawText("payload")
                .collectedAt(observedAt)
                .build();
    }
}