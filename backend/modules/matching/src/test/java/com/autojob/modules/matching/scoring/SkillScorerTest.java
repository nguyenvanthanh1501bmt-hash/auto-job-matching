package com.autojob.modules.matching.scoring;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.jobnormalizer.config.SharedSkillTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.config.MatchingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SkillScorerTest {

    private static final double EPSILON =
            0.000001d;

    @Test
    void languageAndCommunicationOnlyMustNotMatchInteriorDesignerProfessionally() {

        SkillScorer scorer =
                scorer();

        CandidateProfile candidate =
                candidateWithSkills(
                        "English",
                        "Communication"
                );

        NormalizedJob job =
                jobWithSkills(
                        "AutoCAD",
                        "Adobe Photoshop",
                        "SketchUp",
                        "English",
                        "Communication"
                );

        SkillScorer.Result result =
                scorer.score(
                        candidate,
                        job
                );

        /*
         * JD có professional primary skills:
         *
         * AutoCAD
         * Photoshop
         * SketchUp
         *
         * => skill signal KNOWN.
         */
        assertThat(
                result.known()
        ).isTrue();

        /*
         * Candidate chỉ match:
         *
         * English       -> LANGUAGE -> SECONDARY
         * Communication -> SECONDARY
         *
         * Không match primary nào.
         *
         * => hard cap.
         */
        assertThat(
                result.score()
        ).isCloseTo(
                0.05d,
                org.assertj.core.data.Offset.offset(
                        EPSILON
                )
        );

        assertThat(
                result.matchedSkills()
        ).containsExactly(
                "English",
                "Communication"
        );

        /*
         * Secondary skill không được coi là professional
         * missing skill.
         */
        assertThat(
                result.missingSkills()
        ).containsExactly(
                "AutoCAD",
                "Adobe Photoshop",
                "SketchUp"
        );
    }

    @Test
    void languageOnlyJobMustBeUnknownProfessionalSkillSignal() {

        SkillScorer scorer =
                scorer();

        CandidateProfile candidate =
                candidateWithSkills(
                        "English"
                );

        NormalizedJob job =
                jobWithSkills(
                        "English"
                );

        SkillScorer.Result result =
                scorer.score(
                        candidate,
                        job
                );

        /*
         * Chỉ LANGUAGE:
         *
         * không có professional PRIMARY skill đủ để
         * tính skill fit.
         */
        assertThat(
                result.known()
        ).isFalse();

        assertThat(
                result.score()
        ).isCloseTo(
                0.50d,
                org.assertj.core.data.Offset.offset(
                        EPSILON
                )
        );

        assertThat(
                result.matchedSkills()
        ).containsExactly(
                "English"
        );

        assertThat(
                result.missingSkills()
        ).isEmpty();
    }

    @Test
    void professionalSkillPlusLanguageShouldUseProfessionalSkillAsPrimary() {

        SkillScorer scorer =
                scorer();

        CandidateProfile candidate =
                candidateWithSkills(
                        "AutoCAD",
                        "English"
                );

        NormalizedJob job =
                jobWithSkills(
                        "AutoCAD",
                        "English"
                );

        SkillScorer.Result result =
                scorer.score(
                        candidate,
                        job
                );

        assertThat(
                result.known()
        ).isTrue();

        /*
         * AutoCAD = full primary match.
         *
         * English chỉ bonus.
         *
         * Clamp về 1.0.
         */
        assertThat(
                result.score()
        ).isCloseTo(
                1.0d,
                org.assertj.core.data.Offset.offset(
                        EPSILON
                )
        );

        assertThat(
                result.missingSkills()
        ).isEmpty();
    }

    @Test
    void secondaryOnlyCandidateMatchAgainstPrimaryJobShouldBeCappedVeryLow() {

        SkillScorer scorer =
                scorer();

        CandidateProfile candidate =
                candidateWithSkills(
                        "Communication",
                        "Teamwork"
                );

        NormalizedJob job =
                jobWithSkills(
                        "Java",
                        "Spring Boot",
                        "SQL",
                        "Communication",
                        "Teamwork"
                );

        SkillScorer.Result result =
                scorer.score(
                        candidate,
                        job
                );

        assertThat(
                result.known()
        ).isTrue();

        assertThat(
                result.score()
        ).isCloseTo(
                0.05d,
                org.assertj.core.data.Offset.offset(
                        EPSILON
                )
        );

        assertThat(
                result.matchedSkills()
        ).containsExactly(
                "Communication",
                "Teamwork"
        );

        assertThat(
                result.missingSkills()
        ).containsExactly(
                "Java",
                "Spring Boot",
                "SQL"
        );
    }

    @Test
    void missingSecondarySkillsShouldNotReducePrimaryCoverage() {

        SkillScorer scorer =
                scorer();

        CandidateProfile candidate =
                candidateWithSkills(
                        "Java",
                        "SQL"
                );

        NormalizedJob job =
                jobWithSkills(
                        "Java",
                        "Spring Boot",
                        "SQL",
                        "Communication",
                        "Teamwork",
                        "English"
                );

        SkillScorer.Result result =
                scorer.score(
                        candidate,
                        job
                );

        assertThat(
                result.known()
        ).isTrue();

        /*
         * Chỉ tính:
         *
         * Java
         * Spring Boot
         * SQL
         *
         * English/Communication/Teamwork thiếu
         * không làm primary coverage giảm.
         */
        assertThat(
                result.score()
        ).isCloseTo(
                2.0d / 3.0d,
                org.assertj.core.data.Offset.offset(
                        EPSILON
                )
        );

        assertThat(
                result.missingSkills()
        ).containsExactly(
                "Spring Boot"
        );

        assertThat(
                result.missingSkills()
        ).doesNotContain(
                "Communication",
                "Teamwork",
                "English"
        );
    }

    @Test
    void matchingSecondarySkillsShouldAddOnlySmallBonus() {

        SkillScorer scorer =
                scorer();

        CandidateProfile candidate =
                candidateWithSkills(
                        "Java",
                        "SQL",
                        "Communication",
                        "Teamwork",
                        "English"
                );

        NormalizedJob job =
                jobWithSkills(
                        "Java",
                        "Spring Boot",
                        "SQL",
                        "Communication",
                        "Teamwork",
                        "English"
                );

        SkillScorer.Result result =
                scorer.score(
                        candidate,
                        job
                );

        assertThat(
                result.known()
        ).isTrue();

        /*
         * Primary:
         *
         * 2 / 3 = 0.6667
         *
         * Secondary:
         *
         * 3 / 3 = 1.0
         *
         * Bonus:
         *
         * +0.10
         */
        assertThat(
                result.score()
        ).isCloseTo(
                (2.0d / 3.0d)
                        + 0.10d,
                org.assertj.core.data.Offset.offset(
                        EPSILON
                )
        );

        assertThat(
                result.missingSkills()
        ).containsExactly(
                "Spring Boot"
        );
    }

    @Test
    void perfectPrimaryMatchShouldRemainPerfectWhenSecondaryMissing() {

        SkillScorer scorer =
                scorer();

        CandidateProfile candidate =
                candidateWithSkills(
                        "Java",
                        "Spring Boot",
                        "SQL"
                );

        NormalizedJob job =
                jobWithSkills(
                        "Java",
                        "Spring Boot",
                        "SQL",
                        "Communication",
                        "English"
                );

        SkillScorer.Result result =
                scorer.score(
                        candidate,
                        job
                );

        assertThat(
                result.known()
        ).isTrue();

        assertThat(
                result.score()
        ).isCloseTo(
                1.0d,
                org.assertj.core.data.Offset.offset(
                        EPSILON
                )
        );

        assertThat(
                result.missingSkills()
        ).isEmpty();
    }

    @Test
    void secondaryOnlyJobMustBeUnknown() {

        SkillScorer scorer =
                scorer();

        CandidateProfile candidate =
                candidateWithSkills(
                        "Communication",
                        "Teamwork",
                        "English"
                );

        NormalizedJob job =
                jobWithSkills(
                        "Communication",
                        "Teamwork",
                        "English"
                );

        SkillScorer.Result result =
                scorer.score(
                        candidate,
                        job
                );

        assertThat(
                result.known()
        ).isFalse();

        assertThat(
                result.score()
        ).isCloseTo(
                0.50d,
                org.assertj.core.data.Offset.offset(
                        EPSILON
                )
        );

        assertThat(
                result.matchedSkills()
        ).containsExactly(
                "Communication",
                "Teamwork",
                "English"
        );

        assertThat(
                result.missingSkills()
        ).isEmpty();
    }

    @Test
    void missingSecondaryOnlyJobMustStillBeUnknown() {

        SkillScorer scorer =
                scorer();

        CandidateProfile candidate =
                candidateWithSkills();

        NormalizedJob job =
                jobWithSkills(
                        "Communication",
                        "Teamwork",
                        "English"
                );

        SkillScorer.Result result =
                scorer.score(
                        candidate,
                        job
                );

        assertThat(
                result.known()
        ).isFalse();

        assertThat(
                result.score()
        ).isCloseTo(
                0.50d,
                org.assertj.core.data.Offset.offset(
                        EPSILON
                )
        );

        assertThat(
                result.missingSkills()
        ).isEmpty();

        assertThat(
                result.matchedSkills()
        ).isEmpty();
    }

    @Test
    void jobWithoutSkillsShouldReturnUnknownScore() {

        SkillScorer scorer =
                scorer();

        CandidateProfile candidate =
                candidateWithSkills(
                        "Java"
                );

        NormalizedJob job =
                jobWithSkills();

        SkillScorer.Result result =
                scorer.score(
                        candidate,
                        job
                );

        assertThat(
                result.known()
        ).isFalse();

        assertThat(
                result.score()
        ).isCloseTo(
                0.50d,
                org.assertj.core.data.Offset.offset(
                        EPSILON
                )
        );

        assertThat(
                result.matchedSkills()
        ).isEmpty();

        assertThat(
                result.missingSkills()
        ).isEmpty();
    }

    @Test
    void unknownTaxonomySkillShouldDefaultToPrimary() {

        SkillScorer scorer =
                scorer();

        CandidateProfile candidate =
                candidateWithSkills(
                        "Special Domain Skill"
                );

        NormalizedJob job =
                jobWithSkills(
                        "Special Domain Skill",
                        "English",
                        "Communication"
                );

        SkillScorer.Result result =
                scorer.score(
                        candidate,
                        job
                );

        /*
         * Không có taxonomy metadata:
         *
         * => mặc định PRIMARY.
         */
        assertThat(
                result.known()
        ).isTrue();

        assertThat(
                result.score()
        ).isCloseTo(
                1.0d,
                org.assertj.core.data.Offset.offset(
                        EPSILON
                )
        );
    }

    @Test
    void unknownMissingTaxonomySkillShouldRemainProfessionalMissingSkill() {

        SkillScorer scorer =
                scorer();

        CandidateProfile candidate =
                candidateWithSkills(
                        "English",
                        "Communication"
                );

        NormalizedJob job =
                jobWithSkills(
                        "New Domain Technology",
                        "English",
                        "Communication"
                );

        SkillScorer.Result result =
                scorer.score(
                        candidate,
                        job
                );

        assertThat(
                result.known()
        ).isTrue();

        assertThat(
                result.missingSkills()
        ).containsExactly(
                "New Domain Technology"
        );

        /*
         * Chỉ match LANGUAGE + Communication.
         *
         * Không match professional primary.
         */
        assertThat(
                result.score()
        ).isCloseTo(
                0.05d,
                org.assertj.core.data.Offset.offset(
                        EPSILON
                )
        );
    }

    private static SkillScorer scorer() {

        SharedSkillTaxonomyProperties taxonomy =
                new SharedSkillTaxonomyProperties();

        taxonomy.setItems(
                List.of(
                        definition(
                                "java",
                                "Java",
                                "TECHNICAL",
                                "java"
                        ),

                        definition(
                                "spring-boot",
                                "Spring Boot",
                                "TECHNICAL",
                                "spring boot",
                                "springboot"
                        ),

                        definition(
                                "sql",
                                "SQL",
                                "TECHNICAL",
                                "sql"
                        ),

                        definition(
                                "autocad",
                                "AutoCAD",
                                "TOOL",
                                "autocad"
                        ),

                        definition(
                                "adobe-photoshop",
                                "Adobe Photoshop",
                                "TOOL",
                                "photoshop",
                                "adobe photoshop"
                        ),

                        definition(
                                "sketchup",
                                "SketchUp",
                                "TOOL",
                                "sketchup",
                                "sketch up"
                        ),

                        definition(
                                "communication",
                                "Communication",
                                "SOFT_SKILL",
                                "communication"
                        ),

                        definition(
                                "teamwork",
                                "Teamwork",
                                "SOFT_SKILL",
                                "teamwork"
                        ),

                        /*
                         * Không cần thêm english-language
                         * vào genericSkillIds.
                         *
                         * Category LANGUAGE tự phân loại.
                         */
                        definition(
                                "english-language",
                                "English",
                                "LANGUAGE",
                                "english",
                                "english language"
                        )
                )
        );

        MatchingProperties properties =
                new MatchingProperties();

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
                .setGenericSkillCategories(
                        Set.of(
                                "LANGUAGE"
                        )
                );

        return new SkillScorer(
                taxonomy,
                properties
        );
    }

    private static SharedSkillTaxonomyProperties.SkillDefinition
    definition(
            String id,
            String canonical,
            String category,
            String... aliases
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
                category
        );

        definition.setAliases(
                List.of(
                        aliases
                )
        );

        return definition;
    }

    private static CandidateProfile candidateWithSkills(
            String... skills
    ) {

        List<CandidateProfile.Skill> values =
                List.of(
                                skills
                        )
                        .stream()
                        .map(
                                value ->
                                        new CandidateProfile.Skill(
                                                value,
                                                value,

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
                        values
                )
                .build();
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
}