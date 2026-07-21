package com.autojob.modules.jobembedding.domain;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class JobEmbeddingMongoMappingTest {

    @Test
    void shouldUseJobEmbeddingsCollection() {
        Document document =
                JobEmbedding.class.getAnnotation(
                        Document.class
                );

        assertThat(document).isNotNull();
        assertThat(document.collection())
                .isEqualTo("job_embeddings");
    }

    @Test
    void shouldHaveUniqueJobAndVersionIndex() {
        CompoundIndexes indexes =
                JobEmbedding.class.getAnnotation(
                        CompoundIndexes.class
                );

        assertThat(indexes).isNotNull();

        CompoundIndex uniqueIndex = Arrays.stream(
                        indexes.value()
                )
                .filter(CompoundIndex::unique)
                .filter(index -> index.def().contains(
                        "'normalizedJobId': 1"
                ))
                .filter(index -> index.def().contains(
                        "'embeddingVersion': 1"
                ))
                .findFirst()
                .orElseThrow();

        assertThat(uniqueIndex.name())
                .isEqualTo(
                        "uk_job_embedding_job_version"
                );
    }

    @Test
    void shouldNotStoreVectorInMongoDocument() {
        assertThat(
                Arrays.stream(
                                JobEmbedding.class
                                        .getDeclaredFields()
                        )
                        .map(Field::getName)
        )
                .doesNotContain(
                        "vector",
                        "embeddingVector"
                );
    }

    @Test
    void shouldExposeRequiredStatuses() {
        assertThat(JobEmbeddingStatus.values())
                .containsExactly(
                        JobEmbeddingStatus.PROCESSING,
                        JobEmbeddingStatus.READY,
                        JobEmbeddingStatus.FAILED
                );
    }
}