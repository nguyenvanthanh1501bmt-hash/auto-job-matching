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

        properties
                .getWeights()
                .setSemantic(
                        0.90d
                );

        assertThat(
                properties.isWeightSumValid()
        ).isFalse();
    }

    @Test
    void retrievalPoolShouldBeGreaterThanOrEqualToResultLimit() {

        MatchingProperties properties =
                new MatchingProperties();

        properties
                .getRetrieval()
                .setCandidatePoolSize(
                        100
                );

        properties
                .getRetrieval()
                .setResultLimit(
                        20
                );

        assertThat(
                properties.isRetrievalWindowValid()
        ).isTrue();
    }

    @Test
    void retrievalPoolSmallerThanResultLimitShouldBeRejected() {

        MatchingProperties properties =
                new MatchingProperties();

        properties
                .getRetrieval()
                .setCandidatePoolSize(
                        10
                );

        properties
                .getRetrieval()
                .setResultLimit(
                        20
                );

        assertThat(
                properties.isRetrievalWindowValid()
        ).isFalse();
    }

    @Test
    void secondarySkillIdsShouldNotBeHardCodedInJavaDefaults() {

        MatchingProperties properties =
                new MatchingProperties();

        assertThat(
                properties
                        .getSkillScoring()
                        .getGenericSkillIds()
        ).isEmpty();
    }

    @Test
    void genericOnlyCapShouldNotExceedGenericWeight() {

        MatchingProperties properties =
                new MatchingProperties();

        properties
                .getSkillScoring()
                .setGenericWeight(
                        0.10d
                );

        properties
                .getSkillScoring()
                .setGenericOnlyCap(
                        0.05d
                );

        assertThat(
                properties
                        .getSkillScoring()
                        .isGenericCapValid()
        ).isTrue();

        properties
                .getSkillScoring()
                .setGenericOnlyCap(
                        0.20d
                );

        assertThat(
                properties
                        .getSkillScoring()
                        .isGenericCapValid()
        ).isFalse();
    }

    @Test
    void strongSkillThresholdShouldRemainAboveModerateSkillThreshold() {

        MatchingProperties properties =
                new MatchingProperties();

        assertThat(
                properties
                        .getAcceptance()
                        .isSkillRangeValid()
        ).isTrue();

        properties
                .getAcceptance()
                .setStrongSkillScore(
                        0.05d
                );

        assertThat(
                properties
                        .getAcceptance()
                        .isSkillRangeValid()
        ).isFalse();
    }

    @Test
    void strongSemanticThresholdShouldRemainAboveMinimumSemanticThreshold() {

        MatchingProperties properties =
                new MatchingProperties();

        assertThat(
                properties
                        .getAcceptance()
                        .isSemanticRangeValid()
        ).isTrue();

        properties
                .getAcceptance()
                .setStrongSemanticScore(
                        0.40d
                );

        assertThat(
                properties
                        .getAcceptance()
                        .isSemanticRangeValid()
        ).isFalse();
    }

    @Test
    void defaultsShouldDescribeCurrentHybridContract() {

        MatchingProperties properties =
                new MatchingProperties();

        assertThat(
                properties.getVersion()
        ).isEqualTo(
                "hybrid-v6-balanced-r7"
        );

        assertThat(
                properties
                        .getCompatibility()
                        .getNormalizationVersion()
        ).isEqualTo(
                "rule-v4"
        );

        assertThat(
                properties
                        .getCompatibility()
                        .getCandidateTextVersion()
        ).isEqualTo(
                "candidate-text-v1"
        );

        assertThat(
                properties
                        .getCompatibility()
                        .getJobTextVersion()
        ).isEqualTo(
                "job-text-v2"
        );

        assertThat(
                properties
                        .getWeights()
                        .getSemantic()
        ).isEqualTo(
                0.40d
        );

        assertThat(
                properties
                        .getWeights()
                        .getSkill()
        ).isEqualTo(
                0.40d
        );

        assertThat(
                properties
                        .getWeights()
                        .getSeniority()
        ).isEqualTo(
                0.10d
        );

        assertThat(
                properties
                        .getWeights()
                        .getLocation()
        ).isEqualTo(
                0.05d
        );

        assertThat(
                properties
                        .getWeights()
                        .getFreshness()
        ).isEqualTo(
                0.05d
        );

        assertThat(
                properties
                        .getSkillScoring()
                        .getGenericWeight()
        ).isEqualTo(
                0.10d
        );

        assertThat(
                properties
                        .getSkillScoring()
                        .getGenericOnlyCap()
        ).isEqualTo(
                0.05d
        );

        assertThat(
                properties
                        .getAcceptance()
                        .getMinimumFinalScore()
        ).isEqualTo(
                0.45d
        );

        assertThat(
                properties
                        .getAcceptance()
                        .getMinimumSemanticScore()
        ).isEqualTo(
                0.50d
        );

        assertThat(
                properties
                        .getAcceptance()
                        .getMinimumSkillScore()
        ).isEqualTo(
                0.10d
        );

        assertThat(
                properties
                        .getAcceptance()
                        .getStrongSkillScore()
        ).isEqualTo(
                0.70d
        );

        assertThat(
                properties
                        .getAcceptance()
                        .getStrongSemanticScore()
        ).isEqualTo(
                0.70d
        );

        assertThat(
                properties
                        .getRetrieval()
                        .getCandidatePoolSize()
        ).isEqualTo(
                100
        );

        assertThat(
                properties
                        .getRetrieval()
                        .getResultLimit()
        ).isEqualTo(
                20
        );
    }
}