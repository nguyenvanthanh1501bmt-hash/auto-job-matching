package com.autojob.modules.jobnormalizer.config;

import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SharedSeniorityTaxonomyPropertiesTest {

    @Test
    void shouldLoadAllSharedSeniorityLevelsInPriorityOrder() {
        SharedSeniorityTaxonomyProperties taxonomy =
                TaxonomyTestLoader
                        .loadSharedSeniority();

        assertThat(
                taxonomy.getLevels()
                        .stream()
                        .map(
                                SharedSeniorityTaxonomyProperties
                                        .LevelDefinition
                                        ::getLevel
                        )
                        .toList()
        ).containsExactly(
                SeniorityLevel.EXECUTIVE,
                SeniorityLevel.DIRECTOR,
                SeniorityLevel.HEAD,
                SeniorityLevel.MANAGER,
                SeniorityLevel.SUPERVISOR,
                SeniorityLevel.LEAD,
                SeniorityLevel.SENIOR,
                SeniorityLevel.MID,
                SeniorityLevel.JUNIOR,
                SeniorityLevel.ENTRY_LEVEL,
                SeniorityLevel.FRESHER,
                SeniorityLevel.TRAINEE,
                SeniorityLevel.INTERN,
                SeniorityLevel.UNKNOWN
        );
    }

    @Test
    void shouldLoadFourteenSharedLevels() {
        SharedSeniorityTaxonomyProperties taxonomy =
                TaxonomyTestLoader
                        .loadSharedSeniority();

        assertThat(
                taxonomy.getLevels()
        ).hasSize(
                14
        );
    }

    @Test
    void shouldLoadSharedExperienceFallbackThresholds() {
        SharedSeniorityTaxonomyProperties.ExperienceThresholds
                experience =
                TaxonomyTestLoader
                        .loadSharedSeniority()
                        .getExperience();

        assertThat(
                experience.getEntryLevelUnder()
        ).isEqualTo(
                0.5
        );

        assertThat(
                experience.getJuniorUnder()
        ).isEqualTo(
                2.0
        );

        assertThat(
                experience.getMidUnder()
        ).isEqualTo(
                5.0
        );

        assertThat(
                experience.getEntryLevelUnder()
        ).isLessThan(
                experience.getJuniorUnder()
        );

        assertThat(
                experience.getJuniorUnder()
        ).isLessThan(
                experience.getMidUnder()
        );
    }

    @Test
    void shouldUseExplicitUniqueRanksInsteadOfEnumOrdinal() {
        SharedSeniorityTaxonomyProperties taxonomy =
                TaxonomyTestLoader
                        .loadSharedSeniority();

        Set<Integer> ranks =
                new HashSet<>();

        for (
                SharedSeniorityTaxonomyProperties.LevelDefinition
                        definition
                : taxonomy.getLevels()
        ) {
            assertThat(
                    definition.getRank()
            ).isNotNull();

            assertThat(
                    ranks.add(
                            definition.getRank()
                    )
            )
                    .as(
                            "duplicate rank for %s",
                            definition.getLevel()
                    )
                    .isTrue();
        }

        assertThat(
                find(
                        taxonomy,
                        SeniorityLevel.EXECUTIVE
                ).getRank()
        ).isEqualTo(
                12
        );

        assertThat(
                find(
                        taxonomy,
                        SeniorityLevel.UNKNOWN
                ).getRank()
        ).isEqualTo(
                -1
        );
    }

    @Test
    void shouldKeepUnknownAsFinalRuleWithoutPatterns() {
        SharedSeniorityTaxonomyProperties taxonomy =
                TaxonomyTestLoader
                        .loadSharedSeniority();

        SharedSeniorityTaxonomyProperties.LevelDefinition
                unknown =
                taxonomy.getLevels()
                        .get(
                                taxonomy.getLevels()
                                        .size()
                                        - 1
                        );

        assertThat(
                unknown.getLevel()
        ).isEqualTo(
                SeniorityLevel.UNKNOWN
        );

        assertThat(
                unknown.getPatterns()
        ).isEmpty();
    }

    @Test
    void shouldRequirePatternsForEveryExplicitLevel() {
        SharedSeniorityTaxonomyProperties taxonomy =
                TaxonomyTestLoader
                        .loadSharedSeniority();

        assertThat(
                taxonomy.getLevels()
                        .stream()
                        .filter(
                                definition ->
                                        definition.getLevel()
                                                != SeniorityLevel.UNKNOWN
                        )
                        .allMatch(
                                definition ->
                                        definition.getPatterns()
                                                != null
                                                && !definition
                                                .getPatterns()
                                                .isEmpty()
                        )
        ).isTrue();
    }

    @Test
    void shouldKeepLeadGenerationGuard() {
        SharedSeniorityTaxonomyProperties taxonomy =
                TaxonomyTestLoader
                        .loadSharedSeniority();

        SharedSeniorityTaxonomyProperties.LevelDefinition
                lead =
                find(
                        taxonomy,
                        SeniorityLevel.LEAD
                );

        assertThat(
                lead.getExcludePatterns()
        ).contains(
                "^lead generation"
        );

        assertThat(
                lead.getAllowPatterns()
        ).anyMatch(
                pattern ->
                        pattern.contains(
                                "leader"
                        )
        );
    }

    @Test
    void shouldContainDistinctHeadSupervisorAndTraineeLevels() {
        SharedSeniorityTaxonomyProperties taxonomy =
                TaxonomyTestLoader
                        .loadSharedSeniority();

        assertThat(
                taxonomy.getLevels()
                        .stream()
                        .map(
                                SharedSeniorityTaxonomyProperties
                                        .LevelDefinition
                                        ::getLevel
                        )
        ).contains(
                SeniorityLevel.HEAD,
                SeniorityLevel.SUPERVISOR,
                SeniorityLevel.TRAINEE,
                SeniorityLevel.ENTRY_LEVEL,
                SeniorityLevel.EXECUTIVE
        );
    }

    @Test
    void shouldCompileEveryConfiguredRegexWithJavaPattern() {
        SharedSeniorityTaxonomyProperties taxonomy =
                TaxonomyTestLoader
                        .loadSharedSeniority();

        for (
                SharedSeniorityTaxonomyProperties.LevelDefinition
                        definition
                : taxonomy.getLevels()
        ) {
            for (
                    String regex
                    : allPatterns(
                    definition
            )
            ) {
                assertThatCode(
                        () ->
                                Pattern.compile(
                                        regex
                                )
                )
                        .as(
                                "regex for %s: %s",
                                definition.getLevel(),
                                regex
                        )
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    void sharedFallbackShouldMapLowExperienceToEntryLevelNotFresher() {
        SharedSeniorityTaxonomyProperties.ExperienceThresholds
                experience =
                TaxonomyTestLoader
                        .loadSharedSeniority()
                        .getExperience();

        double years =
                0.2;

        SeniorityLevel result;

        if (
                years
                        < experience.getEntryLevelUnder()
        ) {
            result =
                    SeniorityLevel.ENTRY_LEVEL;
        } else if (
                years
                        < experience.getJuniorUnder()
        ) {
            result =
                    SeniorityLevel.JUNIOR;
        } else if (
                years
                        < experience.getMidUnder()
        ) {
            result =
                    SeniorityLevel.MID;
        } else {
            result =
                    SeniorityLevel.SENIOR;
        }

        assertThat(
                result
        ).isEqualTo(
                SeniorityLevel.ENTRY_LEVEL
        );

        assertThat(
                result
        ).isNotEqualTo(
                SeniorityLevel.FRESHER
        );
    }

    private static SharedSeniorityTaxonomyProperties.LevelDefinition find(
            SharedSeniorityTaxonomyProperties taxonomy,
            SeniorityLevel level
    ) {
        return taxonomy.getLevels()
                .stream()
                .filter(
                        definition ->
                                definition.getLevel()
                                        == level
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Missing shared seniority level: "
                                                + level
                                )
                );
    }

    private static List<String> allPatterns(
            SharedSeniorityTaxonomyProperties.LevelDefinition
                    definition
    ) {
        return java.util.stream.Stream
                .of(
                        definition.getPatterns(),
                        definition.getExcludePatterns(),
                        definition.getAllowPatterns()
                )
                .filter(
                        values ->
                                values != null
                )
                .flatMap(
                        List::stream
                )
                .toList();
    }
}