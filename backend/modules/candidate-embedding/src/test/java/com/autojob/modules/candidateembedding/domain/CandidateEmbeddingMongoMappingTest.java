package com.autojob.modules.candidateembedding.domain;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateEmbeddingMongoMappingTest {

    @Test
    void shouldMapToCandidateEmbeddingsCollection() {
        Document document =
                CandidateEmbedding.class
                        .getAnnotation(
                                Document.class
                        );

        assertThat(document)
                .isNotNull();

        assertThat(
                document.collection()
        ).isEqualTo(
                "candidate_embeddings"
        );
    }

    @Test
    void shouldDefineExpectedCompoundIndexes() {
        CompoundIndexes indexes =
                CandidateEmbedding.class
                        .getAnnotation(
                                CompoundIndexes.class
                        );

        assertThat(indexes)
                .isNotNull();

        assertThat(
                indexes.value()
        ).hasSize(3);

        Map<String, CompoundIndex> indexesByName =
                Arrays.stream(
                                indexes.value()
                        )
                        .collect(
                                Collectors.toMap(
                                        CompoundIndex::name,
                                        Function.identity()
                                )
                        );

        CompoundIndex unique =
                indexesByName.get(
                        "uk_candidate_embedding_profile_version_text"
                );

        assertThat(unique)
                .isNotNull();

        assertThat(
                unique.unique()
        ).isTrue();

        assertThat(
                unique.def()
        ).isEqualTo(
                "{'candidateProfileId': 1, 'embeddingVersion': 1, 'textVersion': 1}"
        );

        CompoundIndex statusUpdated =
                indexesByName.get(
                        "idx_candidate_embedding_status_updated"
                );

        assertThat(statusUpdated)
                .isNotNull();

        assertThat(
                statusUpdated.def()
        ).isEqualTo(
                "{'status': 1, 'updatedAt': 1}"
        );

        CompoundIndex profileUpdated =
                indexesByName.get(
                        "idx_candidate_embedding_profile_updated"
                );

        assertThat(profileUpdated)
                .isNotNull();

        assertThat(
                profileUpdated.def()
        ).isEqualTo(
                "{'candidateProfileId': 1, 'updatedAt': -1}"
        );
    }

    @Test
    void shouldExposeExpectedStatuses() {
        assertThat(
                CandidateEmbeddingStatus.values()
        ).containsExactly(
                CandidateEmbeddingStatus.PROCESSING,
                CandidateEmbeddingStatus.READY,
                CandidateEmbeddingStatus.FAILED
        );
    }
}