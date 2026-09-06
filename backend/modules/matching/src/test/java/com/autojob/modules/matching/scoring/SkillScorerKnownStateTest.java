package com.autojob.modules.matching.scoring;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.jobnormalizer.config.SharedSkillTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.config.MatchingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SkillScorerKnownStateTest {

    @Test
    void noJobSkillsMeansUnknown() {

        SkillScorer scorer =
                scorer();

        SkillScorer.Result result =
                scorer.score(
                        candidate(
                                "Java"
                        ),
                        job()
                );

        assertThat(
                result.known()
        ).isFalse();

        assertThat(
                result.score()
        ).isEqualTo(
                0.50d
        );
    }

    @Test
    void secondaryOnlyJobMeansUnknown() {

        SkillScorer scorer =
                scorer();

        SkillScorer.Result result =
                scorer.score(
                        candidate(
                                "Communication"
                        ),
                        job(
                                "Communication",
                                "Teamwork"
                        )
                );

        assertThat(
                result.known()
        ).isFalse();

        assertThat(
                result.score()
        ).isEqualTo(
                0.50d
        );
    }

    @Test
    void atLeastOnePrimaryJobSkillMeansKnown() {

        SkillScorer scorer =
                scorer();

        SkillScorer.Result result =
                scorer.score(
                        candidate(
                                "Communication"
                        ),
                        job(
                                "Java",
                                "Communication"
                        )
                );

        assertThat(
                result.known()
        ).isTrue();

        /*
         * Candidate chỉ match secondary,
         * trong khi JD có Java là primary.
         */
        assertThat(
                result.score()
        ).isEqualTo(
                0.05d
        );
    }

    @Test
    void unknownTaxonomySkillCountsAsPrimaryEvidence() {

        SkillScorer scorer =
                scorer();

        SkillScorer.Result result =
                scorer.score(
                        candidate(),
                        job(
                                "Nghiệp vụ định giá bất động sản"
                        )
                );

        /*
         * Không nằm secondary taxonomy
         * => mặc định PRIMARY.
         */
        assertThat(
                result.known()
        ).isTrue();

        assertThat(
                result.score()
        ).isEqualTo(
                0.0d
        );

        assertThat(
                result.missingSkills()
        )
                .containsExactly(
                        "Nghiệp vụ định giá bất động sản"
                );
    }

    private static SkillScorer scorer() {

        SharedSkillTaxonomyProperties taxonomy =
                new SharedSkillTaxonomyProperties();

        taxonomy.setItems(
                List.of(
                        definition(
                                "java",
                                "Java"
                        ),
                        definition(
                                "communication",
                                "Communication"
                        ),
                        definition(
                                "teamwork",
                                "Teamwork"
                        )
                )
        );

        MatchingProperties properties =
                new MatchingProperties();

        properties
                .getSkillScoring()
                .setGenericSkillIds(
                        Set.of(
                                "communication",
                                "teamwork"
                        )
                );

        properties
                .getSkillScoring()
                .setCoreWeight(
                        1.0d
                );

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

        return new SkillScorer(
                taxonomy,
                properties
        );
    }

    private static SharedSkillTaxonomyProperties.SkillDefinition
    definition(
            String id,
            String canonical
    ) {

        SharedSkillTaxonomyProperties.SkillDefinition definition =
                new SharedSkillTaxonomyProperties.SkillDefinition();

        definition.setId(
                id
        );

        definition.setCanonical(
                canonical
        );

        definition.setCategory(
                "OTHER"
        );

        definition.setAliases(
                List.of(
                        canonical
                )
        );

        return definition;
    }

    private static CandidateProfile candidate(
            String... skills
    ) {

        List<CandidateProfile.Skill> candidateSkills =
                List.of(
                                skills
                        )
                        .stream()
                        .map(
                                skill ->
                                        new CandidateProfile.Skill(
                                                skill,
                                                skill,
                                                CandidateProfile
                                                        .SkillCategory
                                                        .OTHER,
                                                null,
                                                CandidateProfile
                                                        .ProficiencyLevel
                                                        .UNKNOWN,
                                                null,
                                                null,
                                                List.of(
                                                        "SKILLS_SECTION"
                                                )
                                        )
                        )
                        .toList();

        return CandidateProfile
                .builder()
                .skills(
                        candidateSkills
                )
                .build();
    }

    private static NormalizedJob job(
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
}