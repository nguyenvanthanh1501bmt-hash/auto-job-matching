package com.autojob.modules.jobembedding.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobVectorSearchCriteriaTest {

    @Test
    void shouldCreateValidCriteria() {
        JobVectorSearchCriteria criteria =
                new JobVectorSearchCriteria(
                        100,
                        "rule-v2",
                        "model@revision|prep-v1|l2",
                        "job-text-v2"
                );

        assertThat(
                criteria.limit()
        ).isEqualTo(
                100
        );

        assertThat(
                criteria.normalizationVersion()
        ).isEqualTo(
                "rule-v2"
        );

        assertThat(
                criteria.embeddingVersion()
        ).isEqualTo(
                "model@revision|prep-v1|l2"
        );

        assertThat(
                criteria.textVersion()
        ).isEqualTo(
                "job-text-v2"
        );
    }

    @Test
    void shouldRejectZeroLimit() {
        assertThatThrownBy(
                () ->
                        new JobVectorSearchCriteria(
                                0,
                                "rule-v2",
                                "model-version",
                                "job-text-v2"
                        )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "limit"
                );
    }

    @Test
    void shouldRejectBlankNormalizationVersion() {
        assertThatThrownBy(
                () ->
                        new JobVectorSearchCriteria(
                                100,
                                " ",
                                "model-version",
                                "job-text-v2"
                        )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "normalizationVersion"
                );
    }

    @Test
    void shouldRejectBlankEmbeddingVersion() {
        assertThatThrownBy(
                () ->
                        new JobVectorSearchCriteria(
                                100,
                                "rule-v2",
                                "",
                                "job-text-v2"
                        )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "embeddingVersion"
                );
    }

    @Test
    void shouldRejectBlankTextVersion() {
        assertThatThrownBy(
                () ->
                        new JobVectorSearchCriteria(
                                100,
                                "rule-v2",
                                "model-version",
                                null
                        )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "textVersion"
                );
    }
}