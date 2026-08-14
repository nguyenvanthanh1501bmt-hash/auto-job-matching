package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.SharedSeniorityTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SeniorityNormalizer {

    private final List<CompiledRule> rules;

    private final ExperienceThresholds experienceThresholds;

    public SeniorityNormalizer(
            SharedSeniorityTaxonomyProperties taxonomyProperties
    ) {
        validateSharedTaxonomy(
                taxonomyProperties
        );

        this.rules =
                taxonomyProperties
                        .getLevels()
                        .stream()
                        .filter(
                                definition ->
                                        definition.getLevel()
                                                != SeniorityLevel.UNKNOWN
                        )
                        .map(
                                this::compileRule
                        )
                        .toList();

        SharedSeniorityTaxonomyProperties.ExperienceThresholds
                configured =
                taxonomyProperties
                        .getExperience();

        this.experienceThresholds =
                new ExperienceThresholds(
                        configured.getEntryLevelUnder(),
                        configured.getJuniorUnder(),
                        configured.getMidUnder()
                );
    }

    /**
     * Priority:
     *
     * 1. explicit seniorityText
     * 2. explicit title
     * 3. experience fallback
     * 4. UNKNOWN
     */
    public SeniorityLevel normalize(
            String seniorityText,
            String title,
            ExperienceNormalizationResult experience
    ) {
        SeniorityLevel fromSeniorityText =
                detectExplicitLevel(
                        seniorityText
                );

        if (
                fromSeniorityText
                        != SeniorityLevel.UNKNOWN
        ) {
            return fromSeniorityText;
        }

        SeniorityLevel fromTitle =
                detectExplicitLevel(
                        title
                );

        if (
                fromTitle
                        != SeniorityLevel.UNKNOWN
        ) {
            return fromTitle;
        }

        return inferFromExperience(
                experience
        );
    }

    private SeniorityLevel detectExplicitLevel(
            String value
    ) {
        String folded =
                NormalizationTextSupport.fold(
                        value
                );

        if (folded.isBlank()) {
            return SeniorityLevel.UNKNOWN;
        }

        /*
         * Declaration order in shared/seniority.yml
         * is explicit matching priority.
         *
         * Example:
         *
         * "Senior Sales Manager"
         *
         * MANAGER appears before SENIOR,
         * therefore result = MANAGER.
         */
        for (CompiledRule rule : rules) {

            if (
                    !matchesAny(
                            rule.patterns(),
                            folded
                    )
            ) {
                continue;
            }

            boolean excluded =
                    matchesAny(
                            rule.excludePatterns(),
                            folded
                    );

            if (!excluded) {
                return rule.level();
            }

            boolean explicitlyAllowed =
                    matchesAny(
                            rule.allowPatterns(),
                            folded
                    );

            if (explicitlyAllowed) {
                return rule.level();
            }
        }

        return SeniorityLevel.UNKNOWN;
    }

    private SeniorityLevel inferFromExperience(
            ExperienceNormalizationResult experience
    ) {
        if (
                experience == null
                        || !experience.known()
        ) {
            return SeniorityLevel.UNKNOWN;
        }

        Double min =
                experience.min();

        Double max =
                experience.max();

        if (
                min != null
                        && min < 0
        ) {
            min = null;
        }

        if (
                max != null
                        && max < 0
        ) {
            max = null;
        }

        if (
                min == null
                        && max == null
        ) {
            return SeniorityLevel.UNKNOWN;
        }

        /*
         * Preserve the existing Job Normalizer
         * policy for ranges:
         *
         * 2 - 4 years -> use 2 years.
         */
        double effectiveYears =
                min != null
                        ? min
                        : max;

        if (
                effectiveYears
                        < experienceThresholds
                        .entryLevelUnder()
        ) {
            /*
             * Generic low-experience fallback.
             *
             * INTERN / TRAINEE / FRESHER require
             * an explicit textual signal.
             */
            return SeniorityLevel.ENTRY_LEVEL;
        }

        if (
                effectiveYears
                        < experienceThresholds
                        .juniorUnder()
        ) {
            return SeniorityLevel.JUNIOR;
        }

        if (
                effectiveYears
                        < experienceThresholds
                        .midUnder()
        ) {
            return SeniorityLevel.MID;
        }

        /*
         * Experience alone never infers
         * organizational leadership.
         */
        return SeniorityLevel.SENIOR;
    }

    private CompiledRule compileRule(
            SharedSeniorityTaxonomyProperties.LevelDefinition
                    definition
    ) {
        return new CompiledRule(
                definition.getLevel(),
                compilePatterns(
                        definition.getPatterns()
                ),
                compilePatterns(
                        definition.getExcludePatterns()
                ),
                compilePatterns(
                        definition.getAllowPatterns()
                )
        );
    }

    private static List<Pattern> compilePatterns(
            List<String> configuredPatterns
    ) {
        if (
                configuredPatterns == null
                        || configuredPatterns.isEmpty()
        ) {
            return List.of();
        }

        return configuredPatterns
                .stream()
                .filter(
                        value ->
                                value != null
                                        && !value.isBlank()
                )
                .map(
                        Pattern::compile
                )
                .toList();
    }

    private static boolean matchesAny(
            List<Pattern> patterns,
            String value
    ) {
        if (
                patterns == null
                        || patterns.isEmpty()
        ) {
            return false;
        }

        return patterns
                .stream()
                .anyMatch(
                        pattern ->
                                pattern
                                        .matcher(
                                                value
                                        )
                                        .find()
                );
    }

    private static void validateSharedTaxonomy(
            SharedSeniorityTaxonomyProperties taxonomy
    ) {
        if (
                taxonomy == null
                        || taxonomy.getExperience() == null
        ) {
            throw new IllegalStateException(
                    "Shared seniority taxonomy "
                            + "experience configuration is missing"
            );
        }

        List<SharedSeniorityTaxonomyProperties.LevelDefinition>
                levels =
                taxonomy.getLevels();

        if (
                levels == null
                        || levels.isEmpty()
        ) {
            throw new IllegalStateException(
                    "Shared seniority taxonomy "
                            + "levels are missing"
            );
        }

        SharedSeniorityTaxonomyProperties.ExperienceThresholds
                thresholds =
                taxonomy.getExperience();

        if (
                thresholds.getEntryLevelUnder() < 0
                        || thresholds.getJuniorUnder() < 0
                        || thresholds.getMidUnder() < 0
                        || !(
                        thresholds.getEntryLevelUnder()
                                < thresholds.getJuniorUnder()
                                && thresholds.getJuniorUnder()
                                < thresholds.getMidUnder()
                )
        ) {
            throw new IllegalStateException(
                    "Shared seniority experience thresholds "
                            + "must satisfy "
                            + "0 <= entryLevelUnder "
                            + "< juniorUnder < midUnder"
            );
        }

        Set<SeniorityLevel> seenLevels =
                new HashSet<>();

        Set<Integer> seenRanks =
                new HashSet<>();

        Integer previousRank =
                null;

        for (
                int index = 0;
                index < levels.size();
                index++
        ) {
            SharedSeniorityTaxonomyProperties.LevelDefinition
                    definition =
                    levels.get(
                            index
                    );

            if (
                    definition == null
                            || definition.getLevel() == null
                            || definition.getRank() == null
            ) {
                throw new IllegalStateException(
                        "Invalid shared seniority "
                                + "definition at index "
                                + index
                );
            }

            if (
                    !seenLevels.add(
                            definition.getLevel()
                    )
            ) {
                throw new IllegalStateException(
                        "Duplicate shared seniority level: "
                                + definition.getLevel()
                );
            }

            if (
                    !seenRanks.add(
                            definition.getRank()
                    )
            ) {
                throw new IllegalStateException(
                        "Duplicate shared seniority rank: "
                                + definition.getRank()
                );
            }

            if (
                    previousRank != null
                            && definition.getRank()
                            >= previousRank
            ) {
                throw new IllegalStateException(
                        "Shared seniority ranks must "
                                + "strictly descend in "
                                + "declaration order"
                );
            }

            previousRank =
                    definition.getRank();

            if (
                    definition.getLevel()
                            != SeniorityLevel.UNKNOWN
                            && (
                            definition.getPatterns() == null
                                    || definition.getPatterns().isEmpty()
                    )
            ) {
                throw new IllegalStateException(
                        "Shared seniority level requires "
                                + "patterns: "
                                + definition.getLevel()
                );
            }
        }

        SharedSeniorityTaxonomyProperties.LevelDefinition
                last =
                levels.get(
                        levels.size() - 1
                );

        if (
                last.getLevel()
                        != SeniorityLevel.UNKNOWN
        ) {
            throw new IllegalStateException(
                    "UNKNOWN must be the final "
                            + "shared seniority rule"
            );
        }

        if (
                last.getPatterns() != null
                        && !last.getPatterns().isEmpty()
        ) {
            throw new IllegalStateException(
                    "UNKNOWN must not contain patterns"
            );
        }

        Set<SeniorityLevel> expected =
                Set.of(
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

        if (
                !seenLevels.equals(
                        expected
                )
        ) {
            throw new IllegalStateException(
                    "Shared seniority vocabulary mismatch. "
                            + "Expected="
                            + expected
                            + ", actual="
                            + seenLevels
            );
        }
    }

    private record ExperienceThresholds(
            double entryLevelUnder,
            double juniorUnder,
            double midUnder
    ) {
    }

    private record CompiledRule(
            SeniorityLevel level,
            List<Pattern> patterns,
            List<Pattern> excludePatterns,
            List<Pattern> allowPatterns
    ) {
    }
}