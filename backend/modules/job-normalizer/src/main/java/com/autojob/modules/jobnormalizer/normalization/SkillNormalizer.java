package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.SharedSkillTaxonomyProperties;
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

    /**
     * Chỉ split composite label khi separator có whitespace ở ít nhất
     * một phía.
     *
     * Ví dụ được split:
     *
     * - "Tư vấn/ Chăm sóc khách hàng"
     * - "React / Node.js"
     * - "Sales - Customer Service"
     *
     * Ví dụ KHÔNG split:
     *
     * - "CI/CD"
     * - "UI/UX"
     * - "Import/Export"
     * - "B2B/B2C"
     *
     * Sau khi split, fragment cũng chỉ được lấy nếu nó exact-match
     * một alias trong shared taxonomy.
     */
    private static final Pattern COMPOSITE_SKILL_SEPARATOR =
            Pattern.compile(
                    "\\s*/\\s+"
                            + "|\\s+/\\s*"
                            + "|\\s+[•·]\\s+"
                            + "|\\s+[-&]\\s+"
            );

    private final TextNormalizer textNormalizer;

    private final int richRawSkillCount;

    private final Set<String> ambiguousProseAliases;

    private final Set<String> safeShortProseAliases;

    private final Map<String, String> canonicalAliases;

    private final List<SkillMatcher> proseMatchers;

    public SkillNormalizer(
            TextNormalizer textNormalizer,
            SharedSkillTaxonomyProperties taxonomyProperties
    ) {
        this.textNormalizer =
                textNormalizer;

        this.richRawSkillCount =
                taxonomyProperties
                        .getRichRawSkillCount();

        this.ambiguousProseAliases =
                foldSet(
                        taxonomyProperties
                                .getAmbiguousProseAliases()
                );

        this.safeShortProseAliases =
                foldSet(
                        taxonomyProperties
                                .getSafeShortProseAliases()
                );

        List<SkillDefinition> definitions =
                fromSharedDefinitions(
                        taxonomyProperties
                                .getItems()
                );

        this.canonicalAliases =
                createCanonicalAliases(
                        definitions
                );

        this.proseMatchers =
                createProseMatchers(
                        definitions
                );
    }

    public List<String> normalize(
            List<String> rawSkills
    ) {
        return normalizeRawSkills(
                rawSkills
        );
    }

    public List<String> normalize(
            List<String> rawSkills,
            String title,
            String requirementsText,
            String descriptionText
    ) {
        List<String> normalizedRawSkills =
                normalizeRawSkills(
                        rawSkills
                );

        /*
         * Nếu crawler đã cung cấp một danh sách skill đủ giàu,
         * ưu tiên dữ liệu structured đó.
         *
         * Không cần tiếp tục quét prose để tránh sinh thêm noise.
         */
        if (normalizedRawSkills.size()
                >= richRawSkillCount) {

            return normalizedRawSkills;
        }

        /*
         * Với job có raw skill ít hoặc không có skill,
         * bổ sung các known skill xuất hiện trong title,
         * requirements và description.
         */
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

            /*
             * Chỉ split các separator an toàn ở level ngoài:
             *
             * comma
             * semicolon
             * pipe
             * newline
             *
             * Không split "/" tại đây vì "/" có thể là một phần
             * hợp lệ của tên skill như UI/UX hoặc CI/CD.
             */
            String[] skillParts =
                    SKILL_SEPARATOR.split(
                            rawSkillGroup
                    );

            for (String skillPart : skillParts) {

                List<String> resolvedSkills =
                        normalizeSkillPart(
                                skillPart
                        );

                for (String normalizedSkill
                        : resolvedSkills) {

                    if (normalizedSkill == null
                            || normalizedSkill.isBlank()) {

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
        }

        return List.copyOf(
                normalizedSkills
        );
    }

    private List<String> normalizeSkillPart(
            String rawSkill
    ) {
        String cleaned =
                textNormalizer
                        .normalizeInline(
                                rawSkill
                        );

        if (cleaned == null) {
            return List.of();
        }

        /*
         * -----------------------------------------------------
         * 1. Exact alias.
         * -----------------------------------------------------
         *
         * Đây luôn là cách normalize đáng tin cậy nhất.
         */
        String exactCanonical =
                resolveExactAlias(
                        cleaned
                );

        if (exactCanonical != null) {

            return List.of(
                    exactCanonical
            );
        }

        /*
         * -----------------------------------------------------
         * 2. Composite label.
         * -----------------------------------------------------
         *
         * Một số website không trả atomic skill mà trả category
         * ghép, ví dụ:
         *
         * "Tư vấn/ Chăm sóc khách hàng"
         *
         * Whole string không phải alias.
         *
         * Ta split an toàn rồi chỉ giữ fragment nào exact-match
         * shared taxonomy.
         *
         * Không fuzzy match ở đây.
         */
        List<String> compositeMatches =
                resolveCompositeAliases(
                        cleaned
                );

        if (!compositeMatches.isEmpty()) {

            return compositeMatches;
        }

        /*
         * -----------------------------------------------------
         * 3. Preserve unknown structured skill.
         * -----------------------------------------------------
         *
         * Đây là contract quan trọng.
         *
         * Raw skill đã đến từ structured skill field của source.
         * Nếu không exact-match taxonomy thì KHÔNG được lấy một
         * alias nằm bên trong chuỗi rồi thay cả raw skill bằng alias.
         *
         * Ví dụ sai:
         *
         * "Vận hành máy CNC"
         * -> "CNC"
         *
         * "Kỹ thuật hàn TIG"
         * -> "Welding"
         *
         * "Phần mềm MISA"
         * -> "MISA"
         *
         * Các phép biến đổi trên làm mất ý nghĩa của raw skill.
         *
         * Phrase/prose extraction chỉ được dùng với title,
         * requirements, description.
         */
        return List.of(
                cleaned
        );
    }

    private String resolveExactAlias(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return null;
        }

        String aliasKey =
                NormalizationTextSupport
                        .compactKey(
                                value
                        );

        if (aliasKey.isBlank()) {
            return null;
        }

        return canonicalAliases.get(
                aliasKey
        );
    }

    private List<String> resolveCompositeAliases(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return List.of();
        }

        String[] fragments =
                COMPOSITE_SKILL_SEPARATOR.split(
                        value
                );

        if (fragments.length < 2) {

            return List.of();
        }

        List<String> matches =
                new ArrayList<>();

        Set<String> seen =
                new LinkedHashSet<>();

        for (String fragment : fragments) {

            String cleanedFragment =
                    textNormalizer
                            .normalizeInline(
                                    fragment
                            );

            String canonical =
                    resolveExactAlias(
                            cleanedFragment
                    );

            if (canonical == null) {
                continue;
            }

            String key =
                    NormalizationTextSupport.fold(
                            canonical
                    );

            if (seen.add(
                    key
            )) {

                matches.add(
                        canonical
                );
            }
        }

        return List.copyOf(
                matches
        );
    }

    private List<String> extractKnownSkills(
            String title,
            String requirementsText,
            String descriptionText
    ) {
        StringBuilder prose =
                new StringBuilder();

        appendProse(
                prose,
                title
        );

        appendProse(
                prose,
                requirementsText
        );

        appendProse(
                prose,
                descriptionText
        );

        String foldedProse =
                NormalizationTextSupport.fold(
                        prose.toString()
                );

        return extractKnownSkillsFromFoldedProse(
                foldedProse
        );
    }

    private List<String> extractKnownSkillsFromFoldedProse(
            String foldedProse
    ) {
        if (foldedProse == null
                || foldedProse.isBlank()) {

            return List.of();
        }

        List<String> extracted =
                new ArrayList<>();

        Set<String> seen =
                new LinkedHashSet<>();

        for (SkillMatcher matcher
                : proseMatchers) {

            if (!matcher
                    .pattern()
                    .matcher(
                            foldedProse
                    )
                    .find()) {

                continue;
            }

            String deduplicationKey =
                    NormalizationTextSupport.fold(
                            matcher.canonical()
                    );

            if (seen.add(
                    deduplicationKey
            )) {

                extracted.add(
                        matcher.canonical()
                );
            }
        }

        return List.copyOf(
                extracted
        );
    }

    private void appendProse(
            StringBuilder prose,
            String value
    ) {
        String normalized =
                textNormalizer
                        .normalizeMultiline(
                                value
                        );

        if (normalized == null) {
            return;
        }

        if (!prose.isEmpty()) {
            prose.append('\n');
        }

        prose.append(
                normalized
        );
    }

    private List<String> mergeSkills(
            List<String> primary,
            List<String> supplemental
    ) {
        if (supplemental == null
                || supplemental.isEmpty()) {

            return primary;
        }

        List<String> merged =
                new ArrayList<>(
                        primary
                );

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

                merged.add(
                        skill
                );
            }
        }

        return List.copyOf(
                merged
        );
    }

    private Map<String, String> createCanonicalAliases(
            List<SkillDefinition> configuredAliases
    ) {
        if (configuredAliases == null
                || configuredAliases.isEmpty()) {

            return Map.of();
        }

        Map<String, String> aliases =
                new LinkedHashMap<>();

        for (SkillDefinition definition
                : configuredAliases) {

            if (definition == null) {
                continue;
            }

            String canonical =
                    definition.canonical();

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
                    definition.aliases();

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

        return Map.copyOf(
                aliases
        );
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
                NormalizationTextSupport
                        .compactKey(
                                alias
                        );

        if (aliasKey.isBlank()) {
            return;
        }

        String existing =
                aliases.putIfAbsent(
                        aliasKey,
                        canonical
                );

        if (existing != null
                && !existing.equals(
                canonical
        )) {

            throw new IllegalStateException(
                    "Shared skill alias collision: alias="
                            + alias
                            + ", firstCanonical="
                            + existing
                            + ", secondCanonical="
                            + canonical
            );
        }
    }

    private List<SkillMatcher> createProseMatchers(
            List<SkillDefinition> configuredAliases
    ) {
        if (configuredAliases == null
                || configuredAliases.isEmpty()) {

            return List.of();
        }

        List<SkillMatcher> matchers =
                new ArrayList<>();

        Set<String> registeredPatterns =
                new LinkedHashSet<>();

        for (SkillDefinition definition
                : configuredAliases) {

            if (definition == null) {
                continue;
            }

            String canonical =
                    definition.canonical();

            if (canonical == null
                    || canonical.isBlank()) {

                continue;
            }

            List<String> candidates =
                    new ArrayList<>();

            candidates.add(
                    canonical
            );

            if (definition.aliases() != null) {

                candidates.addAll(
                        definition.aliases()
                );
            }

            for (String alias : candidates) {

                if (alias == null
                        || alias.isBlank()) {

                    continue;
                }

                String foldedAlias =
                        NormalizationTextSupport
                                .fold(
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

        return List.copyOf(
                matchers
        );
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

        return compact.length()
                >= 3;
    }

    private static List<SkillDefinition> fromSharedDefinitions(
            List<SharedSkillTaxonomyProperties.SkillDefinition> definitions
    ) {
        if (definitions == null
                || definitions.isEmpty()) {

            return List.of();
        }

        List<SkillDefinition> result =
                new ArrayList<>();

        for (SharedSkillTaxonomyProperties.SkillDefinition definition
                : definitions) {

            if (definition == null) {
                continue;
            }

            result.add(
                    new SkillDefinition(
                            definition.getCanonical(),
                            definition.getAliases() == null
                                    ? List.of()
                                    : List.copyOf(
                                    definition.getAliases()
                            )
                    )
            );
        }

        return List.copyOf(
                result
        );
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

                folded.add(
                        normalized
                );
            }
        }

        return Set.copyOf(
                folded
        );
    }

    private record SkillDefinition(
            String canonical,
            List<String> aliases
    ) {
    }

    private record SkillMatcher(
            String canonical,
            Pattern pattern
    ) {
    }
}