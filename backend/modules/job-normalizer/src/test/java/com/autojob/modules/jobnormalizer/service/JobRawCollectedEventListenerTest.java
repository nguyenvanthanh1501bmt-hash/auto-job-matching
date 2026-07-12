package com.autojob.modules.jobnormalizer.listener;

import com.autojob.common.events.JobRawCollectedEvent;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.service.JobNormalizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobRawCollectedEventListenerTest {

    @Mock
    private JobNormalizationService jobNormalizationService;

    @Mock
    private JobRawCollectedEvent event;

    private JobRawCollectedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new JobRawCollectedEventListener(
                jobNormalizationService
        );
    }

    @Test
    void shouldNormalizeRawJobWhenEventIsReceived() {
        when(event.getRawJobId())
                .thenReturn("raw-001");

        when(event.getSourceCode())
                .thenReturn("MOCK");

        when(event.getSourceJobId())
                .thenReturn("java-backend");

        when(event.getFingerprint())
                .thenReturn("MOCK:java-backend");

        NormalizedJob normalizedJob = NormalizedJob.builder()
                .id("normalized-001")
                .rawJobId("raw-001")
                .sourceCode("MOCK")
                .normalizationVersion("rule-v1")
                .build();

        when(
                jobNormalizationService
                        .normalizeByRawJobId("raw-001")
        ).thenReturn(normalizedJob);

        listener.onJobRawCollected(event);

        verify(jobNormalizationService)
                .normalizeByRawJobId("raw-001");

        verifyNoMoreInteractions(jobNormalizationService);
    }

    @Test
    void shouldPropagateNormalizationException() {
        when(event.getRawJobId())
                .thenReturn("raw-002");

        when(event.getSourceCode())
                .thenReturn("MOCK");

        when(event.getSourceJobId())
                .thenReturn("accountant");

        when(event.getFingerprint())
                .thenReturn("MOCK:accountant");

        IllegalStateException expectedException =
                new IllegalStateException(
                        "MongoDB save failed"
                );

        when(
                jobNormalizationService
                        .normalizeByRawJobId("raw-002")
        ).thenThrow(expectedException);

        assertThatThrownBy(
                () -> listener.onJobRawCollected(event)
        )
                .isSameAs(expectedException)
                .hasMessage("MongoDB save failed");

        verify(jobNormalizationService)
                .normalizeByRawJobId("raw-002");

        verifyNoMoreInteractions(jobNormalizationService);
    }
}