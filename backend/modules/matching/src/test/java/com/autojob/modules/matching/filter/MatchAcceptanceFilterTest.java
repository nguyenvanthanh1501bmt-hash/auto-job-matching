package com.autojob.modules.matching.filter;

import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.config.MatchingProperties;
import com.autojob.modules.matching.domain.HybridScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchAcceptanceFilterTest {

    private MatchAcceptanceFilter filter;

    @BeforeEach
    void setUp() {

        MatchingProperties properties =
                new MatchingProperties();

        filter =
                new MatchAcceptanceFilter(
                        properties
                );
    }

    @Test
    void threePercentKnownSkillFitMustNotBeRescuedByVeryHighSemanticScore() {

        NormalizedJob job =
                jobWithSkills(
                        "Quality Assurance",
                        "Problem Solving",
                        "Process Improvement",
                        "Research",
                        "Communication"
                );

        /*
         * Case giống screenshot:
         *
         * final      = 52%
         * semantic   = 92%
         * skill      = 3%
         * seniority  = 70%
         * location   = 50%
         * freshness  = 87%
         *
         * Vì skillKnown=true và skill chỉ 3%,
         * semantic 92% KHÔNG được rescue.
         */
        HybridScore score =
                score(
                        0.52d,
                        0.92d,
                        0.03d,
                        0.70d,
                        0.50d,
                        0.87d,
                        true,
                        true,
                        true,
                        true
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isFalse();
    }

    @Test
    void knownSkillBelowMinimumMustFailEvenWithPerfectSemanticScore() {

        NormalizedJob job =
                jobWithSkills(
                        "Java",
                        "Spring Boot",
                        "SQL"
                );

        HybridScore score =
                score(
                        0.60d,
                        1.00d,
                        0.09d,
                        0.80d,
                        0.80d,
                        0.80d,
                        true,
                        true,
                        true,
                        true
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isFalse();
    }

    @Test
    void knownSkillAtMinimumMayPassWhenStructuredSignalsDoNotContradict() {

        NormalizedJob job =
                jobWithSkills(
                        "Java",
                        "Spring Boot",
                        "SQL"
                );

        HybridScore score =
                score(
                        0.55d,
                        0.70d,
                        0.10d,
                        0.70d,
                        0.70d,
                        0.70d,
                        true,
                        true,
                        true,
                        true
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isTrue();
    }

    @Test
    void semanticFallbackShouldWorkWhenSkillIsUnknown() {

        NormalizedJob job =
                jobWithSkills();

        HybridScore score =
                score(
                        0.60d,
                        0.92d,
                        0.50d,
                        0.70d,
                        0.50d,
                        0.87d,
                        false,
                        true,
                        true,
                        true
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isTrue();
    }

    @Test
    void unknownSkillStillRequiresStrongSemanticFallback() {

        NormalizedJob job =
                jobWithSkills();

        HybridScore score =
                score(
                        0.55d,
                        0.69d,
                        0.50d,
                        0.70d,
                        0.50d,
                        0.87d,
                        false,
                        true,
                        true,
                        true
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isFalse();
    }

    @Test
    void oneOfThreePrimarySkillsMustNotOverrideKnownContradiction() {

        NormalizedJob job =
                jobWithSkills(
                        "Java",
                        "Spring Boot",
                        "SQL"
                );

        HybridScore score =
                score(
                        0.70d,
                        0.70d,
                        1.0d / 3.0d,
                        0.0d,
                        0.80d,
                        0.50d,
                        true,
                        true,
                        true,
                        false
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isFalse();
    }

    @Test
    void halfPrimaryCoverageMustNotOverrideKnownContradiction() {

        NormalizedJob job =
                jobWithSkills(
                        "Java",
                        "Spring Boot"
                );

        HybridScore score =
                score(
                        0.70d,
                        0.70d,
                        0.50d,
                        0.0d,
                        0.80d,
                        0.50d,
                        true,
                        true,
                        true,
                        false
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isFalse();
    }

    @Test
    void twoOfThreePrimarySkillsMustStillNotOverrideKnownContradiction() {

        NormalizedJob job =
                jobWithSkills(
                        "Java",
                        "Spring Boot",
                        "SQL"
                );

        HybridScore score =
                score(
                        0.72d,
                        0.72d,
                        2.0d / 3.0d,
                        0.0d,
                        0.80d,
                        0.50d,
                        true,
                        true,
                        true,
                        false
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isFalse();
    }

    @Test
    void seventyPercentSkillFitMayOverrideKnownStructuredContradiction() {

        NormalizedJob job =
                jobWithSkills(
                        "Java",
                        "Spring Boot",
                        "SQL",
                        "Docker"
                );

        HybridScore score =
                score(
                        0.75d,
                        0.70d,
                        0.70d,
                        0.0d,
                        0.80d,
                        0.50d,
                        true,
                        true,
                        true,
                        false
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isTrue();
    }

    @Test
    void moderateKnownSkillFitShouldPassWithoutStructuredContradiction() {

        NormalizedJob job =
                jobWithSkills(
                        "Java",
                        "Spring Boot"
                );

        HybridScore score =
                score(
                        0.60d,
                        0.65d,
                        0.50d,
                        0.70d,
                        0.80d,
                        0.50d,
                        true,
                        true,
                        true,
                        false
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isTrue();
    }

    @Test
    void unknownStructuredSignalsMustNotBeTreatedAsContradictions() {

        NormalizedJob job =
                jobWithSkills(
                        "Java"
                );

        HybridScore score =
                score(
                        0.60d,
                        0.65d,
                        0.30d,
                        0.50d,
                        0.50d,
                        0.50d,
                        true,
                        false,
                        false,
                        false
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isTrue();
    }

    @Test
    void highNumericSkillPlaceholderMustNotCountAsStrongWhenSkillIsUnknown() {

        NormalizedJob job =
                jobWithSkills();

        HybridScore score =
                score(
                        0.75d,
                        0.75d,
                        0.95d,
                        0.0d,
                        0.80d,
                        0.50d,
                        false,
                        true,
                        true,
                        false
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isFalse();
    }

    @Test
    void baselineFinalScoreCannotBeBypassedByStrongSkill() {

        NormalizedJob job =
                jobWithSkills(
                        "Java"
                );

        HybridScore score =
                score(
                        0.40d,
                        0.90d,
                        1.00d,
                        1.00d,
                        1.00d,
                        1.00d,
                        true,
                        true,
                        true,
                        true
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isFalse();
    }

    @Test
    void baselineSemanticScoreCannotBeBypassedByStrongSkill() {

        NormalizedJob job =
                jobWithSkills(
                        "Java"
                );

        HybridScore score =
                score(
                        0.80d,
                        0.40d,
                        1.00d,
                        1.00d,
                        1.00d,
                        1.00d,
                        true,
                        true,
                        true,
                        true
                );

        assertThat(
                filter.accept(
                        job,
                        score
                )
        ).isFalse();
    }

    private static NormalizedJob jobWithSkills(
            String... skills
    ) {

        return NormalizedJob
                .builder()
                .skills(
                        List.of(
                                skills
                        )
                )
                .build();
    }

    private static HybridScore score(
            double finalScore,
            double semanticScore,
            double skillScore,
            double seniorityScore,
            double locationScore,
            double freshnessScore,
            boolean skillKnown,
            boolean seniorityKnown,
            boolean locationKnown,
            boolean freshnessKnown
    ) {

        return new HybridScore(
                finalScore,
                semanticScore,
                skillScore,
                seniorityScore,
                locationScore,
                freshnessScore,
                skillKnown,
                seniorityKnown,
                locationKnown,
                freshnessKnown
        );
    }
}