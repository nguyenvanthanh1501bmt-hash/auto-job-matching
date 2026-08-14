package com.autojob.modules.matching.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingPropertiesTest {

    @Test
    void defaultWeightsShouldSumToOne() {
        MatchingProperties properties =
                new MatchingProperties();

        assertThat(
                properties.isWeightSumValid()
        ).isTrue();
    }

    @Test
    void invalidWeightSumShouldBeRejected() {
        MatchingProperties properties =
                new MatchingProperties();

        properties.getWeights()
                .setSemantic(0.90d);

        assertThat(
                properties.isWeightSumValid()
        ).isFalse();
    }

    @Test
    void retrievalPoolShouldBeGreaterThanOrEqualToResultLimit() {
        MatchingProperties properties =
                new MatchingProperties();

        properties.getRetrieval()
                .setCandidatePoolSize(100);

        properties.getRetrieval()
                .setResultLimit(20);

        assertThat(
                properties.isRetrievalWindowValid()
        ).isTrue();
    }

    @Test
    void retrievalPoolSmallerThanResultLimitShouldBeRejected() {
        MatchingProperties properties =
                new MatchingProperties();

        properties.getRetrieval()
                .setCandidatePoolSize(10);

        properties.getRetrieval()
                .setResultLimit(20);

        assertThat(
                properties.isRetrievalWindowValid()
        ).isFalse();
    }

    @Test
    void defaultsShouldDescribePlannedHybridV1Contract() {
        MatchingProperties properties =
                new MatchingProperties();

        assertThat(properties.getVersion())
                .isEqualTo("hybrid-v1");

        assertThat(
                properties.getCompatibility()
                        .getNormalizationVersion()
        ).isEqualTo("rule-v2");

        assertThat(
                properties.getCompatibility()
                        .getCandidateTextVersion()
        ).isEqualTo("candidate-text-v1");

        assertThat(
                properties.getCompatibility()
                        .getJobTextVersion()
        ).isEqualTo("job-text-v2");

        assertThat(
                properties.getRetrieval()
                        .getCandidatePoolSize()
        ).isEqualTo(100);

        assertThat(
                properties.getRetrieval()
                        .getResultLimit()
        ).isEqualTo(20);
    }
}