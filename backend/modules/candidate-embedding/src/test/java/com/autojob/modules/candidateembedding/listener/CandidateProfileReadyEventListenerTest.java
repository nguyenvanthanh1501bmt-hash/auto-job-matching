package com.autojob.modules.candidateembedding.listener;

import com.autojob.common.events.CandidateProfileReadyEvent;
import com.autojob.modules.candidateembedding.domain.CandidateEmbedding;
import com.autojob.modules.candidateembedding.service.CandidateEmbeddingService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateProfileReadyEventListenerTest {

    @Test
    void shouldEmbedCandidateWhenEventArrives() {
        CandidateEmbeddingService service =
                mock(
                        CandidateEmbeddingService.class
                );

        CandidateEmbeddingReadyFixture fixture =
                new CandidateEmbeddingReadyFixture(
                        service
                );

        CandidateEmbedding result =
                CandidateEmbedding
                        .builder()
                        .build();

        when(
                service.embed(
                        "profile-001",
                        false
                )
        ).thenReturn(result);

        fixture.listener
                .onCandidateProfileReady(
                        fixture.event
                );

        verify(service)
                .embed(
                        "profile-001",
                        false
                );
    }

    @Test
    void shouldNotPropagateEmbeddingFailure() {
        CandidateEmbeddingService service =
                mock(
                        CandidateEmbeddingService.class
                );

        CandidateEmbeddingReadyFixture fixture =
                new CandidateEmbeddingReadyFixture(
                        service
                );

        when(
                service.embed(
                        "profile-001",
                        false
                )
        ).thenThrow(
                new RuntimeException(
                        "embedding down"
                )
        );

        assertThatCode(
                () ->
                        fixture.listener
                                .onCandidateProfileReady(
                                        fixture.event
                                )
        ).doesNotThrowAnyException();

        verify(service)
                .embed(
                        "profile-001",
                        false
                );
    }

    private static class CandidateEmbeddingReadyFixture {

        private final CandidateProfileReadyEvent event =
                new CandidateProfileReadyEvent(
                        "profile-001",
                        "raw-cv-001",
                        "rule-v1",
                        Instant.parse(
                                "2026-08-08T00:00:00Z"
                        )
                );

        private final CandidateProfileReadyEventListener listener;

        private CandidateEmbeddingReadyFixture(
                CandidateEmbeddingService service
        ) {
            this.listener =
                    new CandidateProfileReadyEventListener(
                            service
                    );
        }
    }
}