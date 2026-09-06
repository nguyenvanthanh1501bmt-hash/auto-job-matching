package com.autojob.modules.matching.contract;

import com.autojob.modules.matching.domain.MatchResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingResponseTest {

    @Test
    void unknownSkillPlaceholderMustNotBePresentedAsRealSkillMatch() {

        MatchResult document =
                baseResult()
                        /*
                         * Numeric 0.50 nhưng known=false.
                         *
                         * Đây là UNKNOWN, không phải 50% match.
                         */
                        .semanticScore(
                                0.60d
                        )
                        .skillScore(
                                0.50d
                        )
                        .seniorityScore(
                                0.50d
                        )
                        .locationScore(
                                0.50d
                        )
                        .freshnessScore(
                                1.00d
                        )

                        .skillKnown(
                                false
                        )
                        .seniorityKnown(
                                false
                        )
                        .locationKnown(
                                false
                        )
                        .freshnessKnown(
                                true
                        )

                        .build();

        MatchingResponse response =
                responseOf(
                        document
                );

        MatchingResponse.MatchItem item =
                response
                        .results()
                        .getFirst();

        assertThat(
                item
                        .score()
                        .skillScore()
        )
                .isEqualTo(
                        0.50d
                );

        assertThat(
                item
                        .score()
                        .skillKnown()
        )
                .isFalse();

        /*
         * Nếu code cũ còn dùng score 0.50 như score thật,
         * case này có thể bị POSSIBLE.
         *
         * Logic mới phải là EXPLORE.
         */
        assertThat(
                item.matchTier()
        )
                .isEqualTo(
                        MatchingResponse
                                .MatchTier
                                .EXPLORE
                );

        assertThat(
                item.explanations()
        )
                .contains(
                        "Job does not provide enough structured skill data"
                )
                .doesNotContain(
                        "Moderate structured skill compatibility",
                        "Some structured skill compatibility"
                );
    }

    @Test
    void realSkillScoreOfExactlyPointFiveMustRemainKnown() {

        MatchResult document =
                baseResult()
                        /*
                         * Numeric cũng là 0.50,
                         * nhưng đây là score thật.
                         */
                        .semanticScore(
                                0.70d
                        )
                        .skillScore(
                                0.50d
                        )
                        .seniorityScore(
                                0.50d
                        )
                        .locationScore(
                                0.50d
                        )
                        .freshnessScore(
                                1.00d
                        )

                        .skillKnown(
                                true
                        )
                        .seniorityKnown(
                                true
                        )
                        .locationKnown(
                                false
                        )
                        .freshnessKnown(
                                true
                        )

                        .matchedSkills(
                                List.of(
                                        "Domain Skill"
                                )
                        )

                        .build();

        MatchingResponse response =
                responseOf(
                        document
                );

        MatchingResponse.MatchItem item =
                response
                        .results()
                        .getFirst();

        assertThat(
                item
                        .score()
                        .skillKnown()
        )
                .isTrue();

        /*
         * Quan trọng:
         *
         * seniorityScore = 0.50
         * nhưng seniorityKnown = true
         *
         * nên nó vẫn là score thật.
         */
        assertThat(
                item
                        .score()
                        .seniorityKnown()
        )
                .isTrue();

        assertThat(
                item.matchTier()
        )
                .isEqualTo(
                        MatchingResponse
                                .MatchTier
                                .POSSIBLE
                );

        assertThat(
                item.explanations()
        )
                .contains(
                        "Moderate skill overlap: Domain Skill"
                );
    }

    @Test
    void legacyNullKnowledgeMetadataShouldBeExposedAsUnknown() {

        MatchResult document =
                baseResult()
                        .semanticScore(
                                0.90d
                        )
                        .skillScore(
                                0.50d
                        )
                        .seniorityScore(
                                0.50d
                        )
                        .locationScore(
                                0.50d
                        )
                        .freshnessScore(
                                0.50d
                        )

                        /*
                         * Giả lập Mongo document cũ.
                         */
                        .skillKnown(
                                null
                        )
                        .seniorityKnown(
                                null
                        )
                        .locationKnown(
                                null
                        )
                        .freshnessKnown(
                                null
                        )

                        .build();

        MatchingResponse response =
                responseOf(
                        document
                );

        MatchingResponse.ScoreBreakdown score =
                response
                        .results()
                        .getFirst()
                        .score();

        assertThat(
                score.skillKnown()
        )
                .isFalse();

        assertThat(
                score.seniorityKnown()
        )
                .isFalse();

        assertThat(
                score.locationKnown()
        )
                .isFalse();

        assertThat(
                score.freshnessKnown()
        )
                .isFalse();
    }

    private static MatchingResponse responseOf(
            MatchResult document
    ) {
        MatchingRunResult runResult =
                new MatchingRunResult(
                        "candidate-profile-1",
                        "candidate-embedding-1",
                        "hybrid-v6-balanced-r4",

                        1,
                        1,
                        1,

                        false,

                        List.of(
                                document
                        )
                );

        return MatchingResponse.from(
                runResult
        );
    }

    private static MatchResult.MatchResultBuilder
    baseResult() {

        return MatchResult.builder()

                .candidateProfileId(
                        "candidate-profile-1"
                )

                .candidateEmbeddingId(
                        "candidate-embedding-1"
                )

                .normalizedJobId(
                        "job-1"
                )

                .qdrantPointId(
                        "point-1"
                )

                .rank(
                        1
                )

                .finalScore(
                        0.60d
                )

                .rankingVersion(
                        "hybrid-v6-balanced-r4"
                )

                .matchedSkills(
                        List.of()
                )

                .missingSkills(
                        List.of()
                )

                .generatedAt(
                        Instant.parse(
                                "2026-09-06T00:00:00Z"
                        )
                );
    }
}