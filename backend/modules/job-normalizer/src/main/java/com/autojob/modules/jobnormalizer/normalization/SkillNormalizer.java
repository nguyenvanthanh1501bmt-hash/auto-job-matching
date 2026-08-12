package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SkillNormalizer {

    private static final Pattern SKILL_SEPARATOR =
            Pattern.compile("[,;|\\n]+");

    private final TextNormalizer textNormalizer;

    private final int richRawSkillCount;

    private final Set<String> ambiguousProseAliases;

    private final Set<String> safeShortProseAliases;

    private final Map<String, String> canonicalAliases;

    private final List<SkillMatcher> proseMatchers;

    public SkillNormalizer(
            TextNormalizer textNormalizer,
            NormalizationTaxonomyProperties taxonomyProperties
    ) {
        this.textNormalizer = textNormalizer;

        NormalizationTaxonomyProperties.Skill config =
                taxonomyProperties.getSkill();

        this.richRawSkillCount =
                config.getRichRawSkillCount();

        this.ambiguousProseAliases =
                foldSet(
                        config.getAmbiguousProseAliases()
                );

        this.safeShortProseAliases =
                foldSet(
                        config.getSafeShortProseAliases()
                );

        this.canonicalAliases =
                createCanonicalAliases(
                        config.getAliases()
                );

        this.proseMatchers =
                createProseMatchers(
                        config.getAliases()
                );
    }

    public List<String> normalize(
            List<String> rawSkills
    ) {
        return normalizeRawSkills(rawSkills);
    }

    public List<String> normalize(
            List<String> rawSkills,
            String title,
            String requirementsText,
            String descriptionText
    ) {
        List<String> normalizedRawSkills =
                normalizeRawSkills(rawSkills);

        if (normalizedRawSkills.size()
                >= richRawSkillCount) {
            return normalizedRawSkills;
        }

        List<String> extractedSkills =
                extractKnownSkills(
                        title,
                        requirementsText,
                        descriptionText
                );

        return mergeSkills(
                normalizedRawSkills,
                extractedSkills
        );
    }

    private List<String> normalizeRawSkills(
            List<String> rawSkills
    ) {
        if (rawSkills == null
                || rawSkills.isEmpty()) {
            return List.of();
        }

        List<String> normalizedSkills =
                new ArrayList<>();

        Set<String> seenSkills =
                new LinkedHashSet<>();

        for (String rawSkillGroup : rawSkills) {
            if (rawSkillGroup == null) {
                continue;
            }

            String[] skillParts =
                    SKILL_SEPARATOR.split(
                            rawSkillGroup
                    );

            for (String skillPart : skillParts) {
                String normalizedSkill =
                        normalizeSingleSkill(
                                skillPart
                        );

                if (normalizedSkill == null) {
                    continue;
                }

                String deduplicationKey =
                        NormalizationTextSupport.fold(
                                normalizedSkill
                        );

                if (seenSkills.add(
                        deduplicationKey
                )) {
                    normalizedSkills.add(
                            normalizedSkill
                    );
                }
            }
        }

        return List.copyOf(normalizedSkills);
    }

    private List<String> extractKnownSkills(
            String title,
            String requirementsText,
            String descriptionText
    ) {
        StringBuilder prose =
                new StringBuilder();

        appendProse(prose, title);
        appendProse(prose, requirementsText);
        appendProse(prose, descriptionText);

        String foldedProse =
                NormalizationTextSupport.fold(
                        prose.toString()
                );

        if (foldedProse.isBlank()) {
            return List.of();
        }

        List<String> extracted =
                new ArrayList<>();

        Set<String> seen =
                new LinkedHashSet<>();

        for (SkillMatcher matcher : proseMatchers) {
            if (!matcher
                    .pattern()
                    .matcher(foldedProse)
                    .find()) {
                continue;
            }

            String deduplicationKey =
                    NormalizationTextSupport.fold(
                            matcher.canonical()
                    );

            if (seen.add(deduplicationKey)) {
                extracted.add(
                        matcher.canonical()
                );
            }
        }

        return List.copyOf(extracted);
    }

    private void appendProse(
            StringBuilder prose,
            String value
    ) {
        String normalized =
                textNormalizer.normalizeMultiline(
                        value
                );

        if (normalized == null) {
            return;
        }

        if (!prose.isEmpty()) {
            prose.append('\n');
        }

        prose.append(normalized);
    }

    private List<String> mergeSkills(
            List<String> primary,
            List<String> supplemental
    ) {
        if (supplemental.isEmpty()) {
            return primary;
        }

        List<String> merged =
                new ArrayList<>(primary);

        Set<String> seen =
                new LinkedHashSet<>();

        for (String skill : primary) {
            seen.add(
                    NormalizationTextSupport.fold(
                            skill
                    )
            );
        }

        for (String skill : supplemental) {
            String deduplicationKey =
                    NormalizationTextSupport.fold(
                            skill
                    );

            if (seen.add(
                    deduplicationKey
            )) {
                merged.add(skill);
            }
        }

        return List.copyOf(merged);
    }

    private String normalizeSingleSkill(
            String rawSkill
    ) {
        String cleaned =
                textNormalizer.normalizeInline(
                        rawSkill
                );

        if (cleaned == null) {
            return null;
        }

        String aliasKey =
                NormalizationTextSupport.compactKey(
                        cleaned
                );

        return canonicalAliases.getOrDefault(
                aliasKey,
                cleaned
        );
    }

    private Map<String, String> createCanonicalAliases(
            List<NormalizationTaxonomyProperties.CanonicalAlias>
                    configuredAliases
    ) {
        if (configuredAliases == null
                || configuredAliases.isEmpty()) {
            return Map.of();
        }

        Map<String, String> aliases =
                new LinkedHashMap<>();

        for (NormalizationTaxonomyProperties.CanonicalAlias definition
                : configuredAliases) {

            if (definition == null) {
                continue;
            }

            String canonical =
                    definition.getCanonical();

            if (canonical == null
                    || canonical.isBlank()) {
                continue;
            }

            registerAlias(
                    aliases,
                    canonical,
                    canonical
            );

            List<String> values =
                    definition.getAliases();

            if (values == null) {
                continue;
            }

            for (String alias : values) {
                registerAlias(
                        aliases,
                        canonical,
                        alias
                );
            }
        }

        return Map.copyOf(aliases);
    }

    private void registerAlias(
            Map<String, String> aliases,
            String canonical,
            String alias
    ) {
        if (alias == null
                || alias.isBlank()) {
            return;
        }

        String aliasKey =
                NormalizationTextSupport.compactKey(
                        alias
                );

        if (aliasKey.isBlank()) {
            return;
        }

        aliases.put(
                aliasKey,
                canonical
        );
    }

    private List<SkillMatcher> createProseMatchers(
            List<NormalizationTaxonomyProperties.CanonicalAlias>
                    configuredAliases
    ) {
        if (configuredAliases == null
                || configuredAliases.isEmpty()) {
            return List.of();
        }

        List<SkillMatcher> matchers =
                new ArrayList<>();

        Set<String> registeredPatterns =
                new LinkedHashSet<>();

        for (NormalizationTaxonomyProperties.CanonicalAlias definition
                : configuredAliases) {

            if (definition == null) {
                continue;
            }

            String canonical =
                    definition.getCanonical();

            if (canonical == null
                    || canonical.isBlank()) {
                continue;
            }

            List<String> candidates =
                    new ArrayList<>();

            candidates.add(canonical);

            if (definition.getAliases() != null) {
                candidates.addAll(
                        definition.getAliases()
                );
            }

            for (String alias : candidates) {
                if (alias == null
                        || alias.isBlank()) {
                    continue;
                }

                String foldedAlias =
                        NormalizationTextSupport.fold(
                                alias
                        );

                if (!isSafeForProseExtraction(
                        foldedAlias
                )) {
                    continue;
                }

                String matcherKey =
                        canonical
                                + "\u0000"
                                + foldedAlias;

                if (!registeredPatterns.add(
                        matcherKey
                )) {
                    continue;
                }

                Pattern pattern =
                        Pattern.compile(
                                "(?<![a-z0-9])"
                                        + Pattern.quote(
                                        foldedAlias
                                )
                                        + "(?![a-z0-9])"
                        );

                matchers.add(
                        new SkillMatcher(
                                canonical,
                                pattern
                        )
                );
            }
        }

        return List.copyOf(matchers);
    }

    private boolean isSafeForProseExtraction(
            String foldedAlias
    ) {
        if (foldedAlias == null
                || foldedAlias.isBlank()) {
            return false;
        }

        if (ambiguousProseAliases.contains(
                foldedAlias
        )) {
            return false;
        }

        if (safeShortProseAliases.contains(
                foldedAlias
        )) {
            return true;
        }

        String compact =
                foldedAlias.replaceAll(
                        "[^a-z0-9]+",
                        ""
                );

        return compact.length() >= 3;
    }

    private static Set<String> foldSet(
            Set<String> values
    ) {
        if (values == null
                || values.isEmpty()) {
            return Set.of();
        }

        Set<String> folded =
                new LinkedHashSet<>();

        for (String value : values) {
            if (value == null) {
                continue;
            }

            String normalized =
                    NormalizationTextSupport.fold(
                            value
                    );

            if (!normalized.isBlank()) {
                folded.add(normalized);
            }
        }

        return Set.copyOf(folded);
    }

    private record SkillMatcher(
            String canonical,
            Pattern pattern
    ) {
    }
}