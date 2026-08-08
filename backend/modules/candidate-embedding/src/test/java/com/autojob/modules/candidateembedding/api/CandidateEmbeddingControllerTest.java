package com.autojob.modules.candidateembedding.api;

import com.autojob.modules.candidateembedding.domain.CandidateEmbedding;
import com.autojob.modules.candidateembedding.domain.CandidateEmbeddingStatus;
import com.autojob.modules.candidateembedding.service.CandidateEmbeddingService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateEmbeddingControllerTest {

    @Test
    void responseMustNotExposeVector() {
        List<String> componentNames =
                Arrays.stream(
                                CandidateEmbeddingController
                                        .CandidateEmbeddingResponse
                                        .class
                                        .getRecordComponents()
                        )
                        .map(
                                RecordComponent::getName
                        )
                        .toList();

        assertThat(componentNames)
                .doesNotContain("vector");
    }

    @Test
    void shouldReturnLatestMetadata() {
        CandidateEmbeddingService service =
                mock(
                        CandidateEmbeddingService.class
                );

        CandidateEmbeddingController controller =
                new CandidateEmbeddingController(
                        service
                );

        CandidateEmbedding embedding =
                readyEmbedding();

        when(
                service.getLatest(
                        "profile-001"
                )
        ).thenReturn(embedding);

        CandidateEmbeddingController
                .CandidateEmbeddingResponse response =
                controller.getLatest(
                        "profile-001"
                );

        assertThat(
                response.candidateProfileId()
        ).isEqualTo(
                "profile-001"
        );

        assertThat(
                response.dimension()
        ).isEqualTo(384);

        assertThat(
                response.status()
        ).isEqualTo(
                CandidateEmbeddingStatus.READY
        );

        verify(service)
                .getLatest(
                        "profile-001"
                );
    }

    @Test
    void shouldForwardForceOnRebuild() {
        CandidateEmbeddingService service =
                mock(
                        CandidateEmbeddingService.class
                );

        CandidateEmbeddingController controller =
                new CandidateEmbeddingController(
                        service
                );

        when(
                service.embed(
                        "profile-001",
                        true
                )
        ).thenReturn(
                readyEmbedding()
        );

        controller.rebuild(
                "profile-001",
                true
        );

        verify(service)
                .embed(
                        "profile-001",
                        true
                );
    }

    private CandidateEmbedding readyEmbedding() {
        return CandidateEmbedding
                .builder()
                .candidateProfileId(
                        "profile-001"
                )
                .rawCvId(
                        "raw-cv-001"
                )
                .parserVersion(
                        "rule-v1"
                )
                .textVersion(
                        "candidate-text-v1"
                )
                .modelName(
                        "test-model"
                )
                .modelRevision(
                        "rev-1"
                )
                .embeddingVersion(
                        "test-model@rev-1|prep-v1|l2"
                )
                .textHash(
                        "a".repeat(64)
                )
                .dimension(384)
                .normalized(true)
                .vector(
                        List.of(1.0)
                )
                .status(
                        CandidateEmbeddingStatus.READY
                )
                .embeddedAt(
                        Instant.parse(
                                "2026-08-08T00:00:00Z"
                        )
                )
                .build();
    }
}