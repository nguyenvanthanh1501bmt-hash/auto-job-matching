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
    void shouldInitializeObservationTimesWithoutExpirationOnInsert() {
        RawJobService service =
                new RawJobService(
                        rawJobRepository,
                        eventPublisher
                );

        Instant observedAt =
                Instant.parse(
                        "2026-07-01T00:00:00Z"
                );

        RawJob incoming =
                incomingJob(observedAt);

        when(
                rawJobRepository.findByFingerprint(
                        "MOCK:job-1"
                )
        ).thenReturn(
                Optional.empty()
        );

        when(
                rawJobRepository.save(incoming)
        ).thenAnswer(
                invocation -> {
                    RawJob saved =
                            invocation.getArgument(0);

                    saved.setId("raw-1");

                    return saved;
                }
        );

        RawJob saved =
                service.upsertSeen(
                        incoming
                );

        assertThat(saved.getFirstSeenAt())
                .isEqualTo(observedAt);

        assertThat(saved.getLastSeenAt())
                .isEqualTo(observedAt);

        assertThat(saved.getCollectedAt())
                .isEqualTo(observedAt);

        assertThat(saved.getExpiresAt())
                .isNull();

        ArgumentCaptor<Object> eventCaptor =
                ArgumentCaptor.forClass(
                        Object.class
                );

        verify(eventPublisher)
                .publishEvent(
                        eventCaptor.capture()
                );

        assertThat(eventCaptor.getValue())
                .isInstanceOf(
                        JobRawCollectedEvent.class
                );
    }

    @Test
    void shouldUpdateBusinessContentAndClearLegacyExpiration() {
        RawJobService service =
                new RawJobService(
                        rawJobRepository,
                        eventPublisher
                );

        Instant firstSeenAt =
                Instant.parse(
                        "2026-07-01T00:00:00Z"
                );

        RawJob existing =
                RawJob.builder()
                        .id("raw-1")
                        .sourceCode("MOCK")
                        .sourceJobId("job-1")
                        .fingerprint("MOCK:job-1")
                        .title("Old title")
                        .salaryText("10 triệu")
                        .firstSeenAt(firstSeenAt)
                        .lastSeenAt(firstSeenAt)
                        .collectedAt(firstSeenAt)
                        .expiresAt(
                                Instant.parse(
                                        "2026-07-31T00:00:00Z"
                                )
                        )
                        .build();

        Instant observedAt =
                Instant.parse(
                        "2026-07-20T00:00:00Z"
                );

        RawJob incoming =
                incomingJob(
                        observedAt
                );

        incoming.setTitle(
                "New title"
        );

        incoming.setSalaryText(
                "20 triệu"
        );

        when(
                rawJobRepository.findByFingerprint(
                        "MOCK:job-1"
                )
        ).thenReturn(
                Optional.of(existing)
        );

        when(
                rawJobRepository.save(existing)
        ).thenReturn(existing);

        RawJob saved =
                service.upsertSeen(
                        incoming
                );

        assertThat(saved.getId())
                .isEqualTo("raw-1");

        assertThat(saved.getTitle())
                .isEqualTo("New title");

        assertThat(saved.getSalaryText())
                .isEqualTo("20 triệu");

        assertThat(saved.getFirstSeenAt())
                .isEqualTo(firstSeenAt);

        assertThat(saved.getLastSeenAt())
                .isEqualTo(observedAt);

        assertThat(saved.getCollectedAt())
                .isEqualTo(observedAt);

        assertThat(saved.getExpiresAt())
                .isNull();

        verify(eventPublisher)
                .publishEvent(
                        any(Object.class)
                );
    }

    @Test
    void shouldNotDeclareTtlIndexOnLegacyExpiresAtField()
            throws Exception {

        Field expiresAtField =
                RawJob.class.getDeclaredField(
                        "expiresAt"
                );

        Indexed indexed =
                expiresAtField.getAnnotation(
                        Indexed.class
                );

        assertThat(indexed)
                .isNull();
    }

    private RawJob incomingJob(
            Instant observedAt
    ) {
        return RawJob.builder()
                .sourceCode("MOCK")
                .sourceJobId("job-1")
                .fingerprint("MOCK:job-1")
                .title("Title")
                .salaryText("10 triệu")
                .descriptionText(
                        "Description"
                )
                .rawHtml(
                        "<html>payload</html>"
                )
                .rawText("payload")
                .collectedAt(observedAt)
                .build();
    }
}