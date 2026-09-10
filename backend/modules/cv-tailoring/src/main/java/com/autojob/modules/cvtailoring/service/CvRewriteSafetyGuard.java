package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cvtailoring.config.CvRewriteSafetyTaxonomyProperties;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CvRewriteSafetyGuard {

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{M}+");

    private static final Pattern NON_WORD =
            Pattern.compile("[^a-z0-9%+.#]+");

    private static final Pattern TRAILING_DOT =
            Pattern.compile("\\.(?=\\s|$)");

    private static final Pattern NUMBER =
            Pattern.compile(
                    "(?<![a-z0-9])\\d+(?:[.,]\\d+)?%?(?![a-z0-9])",
                    Pattern.CASE_INSENSITIVE
            );

    private final CvEvidenceService evidenceService;

    private final Map<String, List<String>>
            highRiskClaimGroups;

    private final Map<String, Set<EvidenceKind>>
            requiredEvidenceKindsByGroup;

    private final Map<String, Set<String>>
            genericTokensByGroup;

    private final Set<String>
            companionIdentityRequiredGroups;

    public CvRewriteSafetyGuard(
            CvEvidenceService evidenceService,
            CvRewriteSafetyTaxonomyProperties safetyTaxonomy
    ) {
        this.evidenceService =
                Objects.requireNonNull(
                        evidenceService,
                        "evidenceService must not be null"
                );

        Objects.requireNonNull(
                safetyTaxonomy,
                "safetyTaxonomy must not be null"
        );

        this.highRiskClaimGroups =
                normalizeClaimGroups(
                        safetyTaxonomy
                                .getHighRiskClaimGroups()
                );

        if (highRiskClaimGroups.isEmpty()) {
            throw new IllegalArgumentException(
                    "CV rewrite safety taxonomy "
                            + "must contain at least one "
                            + "high-risk claim group"
            );
        }

        this.requiredEvidenceKindsByGroup =
                normalizeEvidenceKindRequirements(
                        safetyTaxonomy
                );

        this.genericTokensByGroup =
                normalizeGenericTokens(
                        safetyTaxonomy
                );

        this.companionIdentityRequiredGroups =
                normalizeCompanionIdentityGroups(
                        safetyTaxonomy
                );
    }

    public boolean isSafe(
            SuggestionItem suggestion,
            CvEvidenceService.EvidenceMap evidenceMap,
            NormalizedJob job
    ) {
        if (suggestion == null
                || evidenceMap == null
                || suggestion.suggested() == null
                || suggestion.suggested().isBlank()
                || suggestion.original() == null
                || suggestion.original().isBlank()) {

            return false;
        }

        String original =
                compact(
                        suggestion.original()
                );

        String suggested =
                compact(
                        suggestion.suggested()
                );

        if (suggested.isBlank()
                || suggested.equals(
                original
        )) {

            return false;
        }

        Map<String, EvidenceItem> evidenceById =
                indexEvidence(
                        evidenceMap.items()
                );

        List<EvidenceItem> citedEvidence =
                new ArrayList<>();

        for (String evidenceId :
                safeList(
                        suggestion.evidenceIds()
                )) {

            EvidenceItem item =
                    evidenceById.get(
                            evidenceId
                    );

            if (item == null) {
                return false;
            }

            citedEvidence.add(
                    item
            );
        }

        String evidenceCorpus =
                evidenceCorpus(
                        suggestion.original(),
                        citedEvidence
                );

        if (introducesUnsupportedNumber(
                suggestion.suggested(),
                evidenceCorpus
        )) {

            return false;
        }

        if (introducesUnsupportedHighRiskClaim(
                suggested,
                compact(
                        evidenceCorpus
                ),
                citedEvidence
        )) {

            return false;
        }

        return newlyExplicitSkillsAreTracked(
                suggestion,
                evidenceMap,
                job
        );
    }

    private boolean introducesUnsupportedNumber(
            String suggested,
            String evidenceCorpus
    ) {
        Set<String> supportedNumbers =
                numbers(
                        evidenceCorpus
                );

        for (String number :
                numbers(
                        suggested
                )) {

            if (!supportedNumbers.contains(
                    number
            )) {

                return true;
            }
        }

        return false;
    }

    private boolean introducesUnsupportedHighRiskClaim(
            String suggested,
            String evidenceCorpus,
            List<EvidenceItem> citedEvidence
    ) {
        for (
                Map.Entry<String, List<String>> entry
                : highRiskClaimGroups.entrySet()
        ) {
            String group =
                    entry.getKey();

            List<String> phrases =
                    entry.getValue();

            Set<EvidenceKind> requiredKinds =
                    requiredEvidenceKindsByGroup
                            .getOrDefault(
                                    group,
                                    Set.of()
                            );

            for (String phrase : phrases) {

                if (!containsPhrase(
                        suggested,
                        phrase
                )) {

                    continue;
                }

                if (!requiredKinds.isEmpty()) {

                    if (!supportsTypedClaim(
                            group,
                            suggested,
                            citedEvidence,
                            requiredKinds
                    )) {

                        return true;
                    }

                    continue;
                }

                if (!containsPhrase(
                        evidenceCorpus,
                        phrase
                )) {

                    return true;
                }
            }
        }

        return false;
    }

    private boolean supportsTypedClaim(
            String group,
            String suggested,
            List<EvidenceItem> citedEvidence,
            Set<EvidenceKind> requiredKinds
    ) {
        List<EvidenceItem> typedEvidence =
                citedEvidence
                        .stream()
                        .filter(
                                Objects::nonNull
                        )
                        .filter(
                                item ->
                                        item.kind() != null
                                                && requiredKinds
                                                .contains(
                                                        item.kind()
                                                )
                        )
                        .toList();

        if (typedEvidence.isEmpty()) {
            return false;
        }

        Set<String> genericTokens =
                genericTokensByGroup
                        .getOrDefault(
                                group,
                                Set.of()
                        );

        if (companionIdentityRequiredGroups
                .contains(
                        group
                )) {

            return supportsCompanionIdentityClaim(
                    group,
                    suggested,
                    typedEvidence,
                    citedEvidence,
                    genericTokens
            );
        }

        for (EvidenceItem item : typedEvidence) {

            String evidenceText =
                    compact(
                            item.text()
                    );

            if (evidenceText.isBlank()) {
                continue;
            }

            /*
             * Exact qualification name is strongest evidence.
             *
             * Example:
             *
             * Basic Life Support
             * Bachelor of Nursing
             */
            if (containsPhrase(
                    suggested,
                    evidenceText
            )) {

                return true;
            }

            Set<String> identityTokens =
                    identityTokens(
                            evidenceText,
                            genericTokens
                    );

            /*
             * Supports morphological wording changes:
             *
             * evidence:
             *   Registered Nurse License
             *
             * suggested:
             *   licensed registered nurse
             */
            if (!identityTokens.isEmpty()
                    && containsAllTokens(
                    suggested,
                    identityTokens
            )) {

                return true;
            }
        }

        return false;
    }

    private boolean supportsCompanionIdentityClaim(
            String group,
            String suggested,
            List<EvidenceItem> typedEvidence,
            List<EvidenceItem> citedEvidence,
            Set<String> genericTokens
    ) {
        List<String> claimPhrases =
                highRiskClaimGroups
                        .getOrDefault(
                                group,
                                List.of()
                        );

        for (EvidenceItem claimEvidence :
                typedEvidence) {

            String evidenceText =
                    compact(
                            claimEvidence.text()
                    );

            if (evidenceText.isBlank()) {
                continue;
            }

            /*
             * A companion-rule evidence item must itself
             * describe the claimed property.
             *
             * For language proficiency:
             *
             * "Fluent"               -> yes
             * "Native proficiency"   -> yes
             * "English"              -> no
             */
            if (!containsAnyClaimPhrase(
                    evidenceText,
                    claimPhrases
            )) {

                continue;
            }

            Set<String> ownIdentity =
                    identityTokens(
                            evidenceText,
                            genericTokens
                    );

            /*
             * One evidence value may already contain both
             * property + identity, e.g. "Fluent English".
             */
            if (!ownIdentity.isEmpty()
                    && containsPhrase(
                    suggested,
                    evidenceText
            )) {

                return true;
            }

            /*
             * Otherwise the property evidence itself must be
             * visible in the proposed wording.
             *
             * "Fluent" evidence cannot silently justify
             * "native proficiency".
             */
            if (!containsPhrase(
                    suggested,
                    evidenceText
            )) {

                continue;
            }

            /*
             * Property-only evidence needs a companion from
             * the same deterministic evidence scope.
             *
             * language:0:proficiency = Fluent
             * language:0:name        = English
             */
            if (hasMatchingCompanionEvidence(
                    claimEvidence,
                    citedEvidence,
                    suggested,
                    genericTokens
            )) {

                return true;
            }
        }

        return false;
    }

    private boolean containsAnyClaimPhrase(
            String evidenceText,
            List<String> claimPhrases
    ) {
        for (String phrase :
                claimPhrases) {

            if (containsPhrase(
                    evidenceText,
                    phrase
            )) {

                return true;
            }
        }

        return false;
    }

    private boolean hasMatchingCompanionEvidence(
            EvidenceItem source,
            List<EvidenceItem> citedEvidence,
            String suggested,
            Set<String> genericTokens
    ) {
        if (source.scopeId() == null
                || source.scopeId().isBlank()) {

            return false;
        }

        for (EvidenceItem companion :
                citedEvidence) {

            if (companion == null
                    || companion == source
                    || !Objects.equals(
                    source.scopeId(),
                    companion.scopeId()
            )) {

                continue;
            }

            String companionText =
                    compact(
                            companion.text()
                    );

            if (companionText.isBlank()) {
                continue;
            }

            Set<String> companionIdentity =
                    identityTokens(
                            companionText,
                            genericTokens
                    );

            if (!companionIdentity.isEmpty()
                    && containsAllTokens(
                    suggested,
                    companionIdentity
            )) {

                return true;
            }
        }

        return false;
    }

    private Set<String> identityTokens(
            String compactEvidence,
            Set<String> genericTokens
    ) {
        if (compactEvidence == null
                || compactEvidence.isBlank()) {

            return Set.of();
        }

        Set<String> result =
                new LinkedHashSet<>();

        for (String token :
                compactEvidence.split(
                        " "
                )) {

            if (token.isBlank()
                    || genericTokens.contains(
                    token
            )) {

                continue;
            }

            result.add(
                    token
            );
        }

        return Set.copyOf(
                result
        );
    }

    private boolean containsAllTokens(
            String compactText,
            Set<String> tokens
    ) {
        if (compactText == null
                || compactText.isBlank()
                || tokens == null
                || tokens.isEmpty()) {

            return false;
        }

        Set<String> textTokens =
                new LinkedHashSet<>(
                        List.of(
                                compactText.split(
                                        " "
                                )
                        )
                );

        return textTokens.containsAll(
                tokens
        );
    }

    private boolean newlyExplicitSkillsAreTracked(
            SuggestionItem suggestion,
            CvEvidenceService.EvidenceMap evidenceMap,
            NormalizedJob job
    ) {
        Set<String> targetSkillKeys =
                new LinkedHashSet<>();

        for (String targetSkill :
                safeList(
                        suggestion.targetSkills()
                )) {

            if (targetSkill == null
                    || targetSkill.isBlank()) {

                return false;
            }

            String key =
                    evidenceService
                            .canonicalSkillKey(
                                    targetSkill
                            );

            if (key == null
                    || key.isBlank()) {

                return false;
            }

            targetSkillKeys.add(
                    key
            );
        }

        Map<String, String> watchedSkills =
                new HashMap<>();

        if (evidenceMap.items() != null) {

            for (EvidenceItem item :
                    evidenceMap.items()) {

                if (item != null
                        && isSkillEvidence(
                        item
                )
                        && item.canonicalSkillKey()
                        != null
                        && !item
                        .canonicalSkillKey()
                        .isBlank()
                        && item.text() != null
                        && !item.text().isBlank()) {

                    String phrase =
                            compact(
                                    item.text()
                            );

                    if (!phrase.isBlank()) {

                        watchedSkills.putIfAbsent(
                                phrase,
                                item.canonicalSkillKey()
                        );
                    }
                }
            }
        }

        if (job != null) {

            for (String jobSkill :
                    safeList(
                            job.getSkills()
                    )) {

                if (jobSkill == null
                        || jobSkill.isBlank()) {

                    continue;
                }

                String key =
                        evidenceService
                                .canonicalSkillKey(
                                        jobSkill
                                );

                String phrase =
                        compact(
                                jobSkill
                        );

                if (key != null
                        && !key.isBlank()
                        && !phrase.isBlank()) {

                    watchedSkills.putIfAbsent(
                            phrase,
                            key
                    );
                }
            }
        }

        String original =
                compact(
                        suggestion.original()
                );

        String suggested =
                compact(
                        suggestion.suggested()
                );

        for (Map.Entry<String, String> watched :
                watchedSkills.entrySet()) {

            String phrase =
                    watched.getKey();

            String canonicalKey =
                    watched.getValue();

            boolean existedBefore =
                    containsPhrase(
                            original,
                            phrase
                    );

            boolean existsAfter =
                    containsPhrase(
                            suggested,
                            phrase
                    );

            if (!existedBefore
                    && existsAfter
                    && !targetSkillKeys.contains(
                    canonicalKey
            )) {

                return false;
            }
        }

        return true;
    }

    private Map<String, List<String>>
    normalizeClaimGroups(
            Map<String, List<String>> configured
    ) {
        Map<String, List<String>> result =
                new LinkedHashMap<>();

        if (configured == null) {
            return Map.of();
        }

        for (
                Map.Entry<String, List<String>> entry
                : configured.entrySet()
        ) {
            if (entry.getKey() == null
                    || entry.getKey().isBlank()) {

                continue;
            }

            Set<String> normalized =
                    new LinkedHashSet<>();

            for (String phrase :
                    safeList(
                            entry.getValue()
                    )) {

                String value =
                        compact(
                                phrase
                        );

                if (!value.isBlank()) {

                    normalized.add(
                            value
                    );
                }
            }

            if (!normalized.isEmpty()) {

                result.put(
                        entry
                                .getKey()
                                .trim(),

                        List.copyOf(
                                normalized
                        )
                );
            }
        }

        return Map.copyOf(
                result
        );
    }

    private Map<String, Set<EvidenceKind>>
    normalizeEvidenceKindRequirements(
            CvRewriteSafetyTaxonomyProperties taxonomy
    ) {
        Map<String, Set<EvidenceKind>> result =
                new LinkedHashMap<>();

        for (String group :
                highRiskClaimGroups.keySet()) {

            Set<EvidenceKind> kinds =
                    taxonomy
                            .getRequiredEvidenceKinds(
                                    group
                            );

            if (!kinds.isEmpty()) {

                result.put(
                        group,
                        Set.copyOf(
                                kinds
                        )
                );
            }
        }

        return Map.copyOf(
                result
        );
    }

    private Map<String, Set<String>>
    normalizeGenericTokens(
            CvRewriteSafetyTaxonomyProperties taxonomy
    ) {
        Map<String, Set<String>> result =
                new LinkedHashMap<>();

        for (String group :
                highRiskClaimGroups.keySet()) {

            Set<String> configured =
                    taxonomy
                            .getGenericTokens(
                                    group
                            );

            if (configured.isEmpty()) {
                continue;
            }

            Set<String> normalized =
                    new LinkedHashSet<>();

            for (String token : configured) {

                String compact =
                        compact(
                                token
                        );

                if (compact.isBlank()) {
                    continue;
                }

                for (String part :
                        compact.split(
                                " "
                        )) {

                    if (!part.isBlank()) {

                        normalized.add(
                                part
                        );
                    }
                }
            }

            if (!normalized.isEmpty()) {

                result.put(
                        group,
                        Set.copyOf(
                                normalized
                        )
                );
            }
        }

        return Map.copyOf(
                result
        );
    }

    private Set<String> normalizeCompanionIdentityGroups(
            CvRewriteSafetyTaxonomyProperties taxonomy
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        for (String group :
                taxonomy
                        .getCompanionIdentityRequiredGroups()) {

            if (group == null
                    || group.isBlank()) {

                continue;
            }

            String normalized =
                    group.trim();

            if (highRiskClaimGroups.containsKey(
                    normalized
            )
                    && requiredEvidenceKindsByGroup
                    .containsKey(
                            normalized
                    )) {

                result.add(
                        normalized
                );
            }
        }

        return Set.copyOf(
                result
        );
    }

    private boolean isSkillEvidence(
            EvidenceItem item
    ) {
        return item.kind()
                == EvidenceKind.SKILL
                || item.kind()
                == EvidenceKind.TOOL
                || item.kind()
                == EvidenceKind.EQUIPMENT;
    }

    private String evidenceCorpus(
            String original,
            List<EvidenceItem> evidence
    ) {
        StringBuilder builder =
                new StringBuilder(
                        original == null
                                ? ""
                                : original
                );

        for (EvidenceItem item : evidence) {

            if (item != null
                    && item.text() != null
                    && !item.text().isBlank()) {

                builder.append(
                        ' '
                ).append(
                        item.text()
                );
            }
        }

        return builder.toString();
    }

    private Map<String, EvidenceItem> indexEvidence(
            List<EvidenceItem> items
    ) {
        Map<String, EvidenceItem> result =
                new HashMap<>();

        if (items == null) {
            return result;
        }

        for (EvidenceItem item : items) {

            if (item != null
                    && item.id() != null
                    && !item.id().isBlank()) {

                result.put(
                        item.id(),
                        item
                );
            }
        }

        return result;
    }

    private Set<String> numbers(
            String value
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        if (value == null
                || value.isBlank()) {

            return result;
        }

        Matcher matcher =
                NUMBER.matcher(
                        value.toLowerCase(
                                Locale.ROOT
                        )
                );

        while (matcher.find()) {

            result.add(
                    matcher.group()
            );
        }

        return result;
    }

    private boolean containsPhrase(
            String compactText,
            String compactPhrase
    ) {
        if (compactText == null
                || compactText.isBlank()
                || compactPhrase == null
                || compactPhrase.isBlank()) {

            return false;
        }

        return (" " + compactText + " ")
                .contains(
                        " "
                                + compactPhrase
                                + " "
                );
    }

    private String compact(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return "";
        }

        String normalized =
                Normalizer
                        .normalize(
                                value,
                                Normalizer.Form.NFD
                        )
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .replace(
                                'đ',
                                'd'
                        );

        normalized =
                DIACRITICS
                        .matcher(
                                normalized
                        )
                        .replaceAll(
                                ""
                        );

        normalized =
                NON_WORD
                        .matcher(
                                normalized
                        )
                        .replaceAll(
                                " "
                        );

        normalized =
                TRAILING_DOT
                        .matcher(
                                normalized
                        )
                        .replaceAll(
                                " "
                        );

        normalized =
                normalized.trim();

        return normalized.replaceAll(
                "\\s+",
                " "
        );
    }

    private List<String> safeList(
            List<String> values
    ) {
        return values == null
                ? List.of()
                : values;
    }
}