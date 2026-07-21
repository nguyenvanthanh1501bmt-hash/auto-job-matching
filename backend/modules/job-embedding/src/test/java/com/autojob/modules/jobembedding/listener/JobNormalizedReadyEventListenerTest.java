package com.autojob.modules.jobembedding.listener;

import com.autojob.common.events.JobNormalizedReadyEvent;
import com.autojob.modules.jobembedding.domain.JobEmbedding;
import com.autojob.modules.jobembedding.domain.JobEmbeddingStatus;
import com.autojob.modules.jobembedding.service.JobEmbeddingService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobNormalizedReadyEventListenerTest {

    private final JobEmbeddingService service =
            mock(JobEmbeddingService.class);

    private final JobNormalizedReadyEventListener listener =
            new JobNormalizedReadyEventListener(service);

    @Test
    void shouldEmbedNormalizedJobFromEvent() {
        JobNormalizedReadyEvent event = event();

        when(service.embed("normalized-001", false))
                .thenReturn(
                        JobEmbedding.builder()
                                .normalizedJobId(
                                        "normalized-001"
                                )
                                .embeddingVersion(
                                        "model@revision|prep-v1|l2"
                                )
                                .textHash("a".repeat(64))
                                .qdrantPointId(
                                        "13f4a274-a0f2-5d77-bf84-4c65bf870dac"
                                )
                                .status(
                                        JobEmbeddingStatus.READY
                                )
                                .build()
                );

        listener.onJobNormalizedReady(event);

        verify(service).embed(
                "normalized-001",
                false
        );
    }

    @Test
    void shouldNotPropagateEmbeddingFailure() {
        JobNormalizedReadyEvent event = event();

        when(service.embed("normalized-001", false))
                .thenThrow(
                        new IllegalStateException(
                                "embedding service unavailable"
                        )
                );

        assertThatCode(
                () -> listener.onJobNormalizedReady(event)
        ).doesNotThrowAnyException();

        verify(service).embed(
                "normalized-001",
                false
        );
    }

    private JobNormalizedReadyEvent event() {
        return new JobNormalizedReadyEvent(
                "normalized-001",
                "raw-001",
                "MOCK",
                "rule-v1",
                Instant.parse("2026-07-21T00:00:00Z")
        );
    }
}