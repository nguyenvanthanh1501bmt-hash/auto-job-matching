package com.autojob.modules.candidateembedding.service;

import com.autojob.common.embedding.client.EmbeddingClient;
import com.autojob.common.embedding.client.dto.EmbeddingResponse;
import com.autojob.common.embedding.config.EmbeddingProperties;
import com.autojob.common.embedding.service.EmbeddingTextHashCalculator;
import com.autojob.modules.candidateembedding.config.CandidateEmbeddingProperties;
import com.autojob.modules.candidateembedding.text.CandidateEmbeddingTextBuilder;
import com.autojob.modules.cv.domain.CandidateProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateEmbeddingGeneratorTest {

    @Test
    void generatesEmbeddingWithoutRepositoryPersistence() {

        CandidateEmbeddingTextBuilder textBuilder =
                mock(
                        CandidateEmbeddingTextBuilder.class
                );

        EmbeddingClient embeddingClient =
                mock(
                        EmbeddingClient.class
                );

        EmbeddingTextHashCalculator hashCalculator =
                new EmbeddingTextHashCalculator();

        EmbeddingProperties embeddingProperties =
                new EmbeddingProperties();

        embeddingProperties
                .setExpectedDimension(
                        3
                );

        embeddingProperties
                .setExpectedVersion(
                        "model@rev|prep-v1|l2"
                );

        CandidateEmbeddingProperties candidateProperties =
                new CandidateEmbeddingProperties();

        candidateProperties
                .setTextVersion(
                        "candidate-text-v1"
                );

        CandidateEmbeddingGenerator generator =
                new CandidateEmbeddingGenerator(
                        textBuilder,
                        embeddingClient,
                        hashCalculator,
                        embeddingProperties,
                        candidateProperties
                );

        CandidateProfile profile =
                CandidateProfile
                        .builder()
                        .id(
                                "candidate-1"
                        )
                        .build();

        String text =
                "query: Skills: Java, Spring Boot";

        String textHash =
                hashCalculator
                        .calculate(
                                text
                        );

        when(
                textBuilder.build(
                        profile
                )
        ).thenReturn(
                text
        );

        when(
                embeddingClient.embed(
                        text
                )
        ).thenReturn(
                new EmbeddingResponse(
                        List.of(
                                1.0d,
                                0.0d,
                                0.0d
                        ),
                        3,
                        "model",
                        "rev",
                        "model@rev|prep-v1|l2",
                        textHash,
                        true
                )
        );

        CandidateEmbeddingGenerator
                .GeneratedCandidateEmbedding result =
                generator.generate(
                        profile
                );

        assertThat(
                result.vector()
        ).containsExactly(
                1.0d,
                0.0d,
                0.0d
        );

        assertThat(
                result.textVersion()
        ).isEqualTo(
                "candidate-text-v1"
        );

        assertThat(
                result.embeddingVersion()
        ).isEqualTo(
                "model@rev|prep-v1|l2"
        );

        verify(
                textBuilder
        ).build(
                profile
        );

        verify(
                embeddingClient
        ).embed(
                text
        );
    }
}