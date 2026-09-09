package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.EditableNode;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteRequest;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteResponse;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteSuggestion;
import com.autojob.modules.cvtailoring.ai.CvRewriteProviderRouter;
import com.autojob.modules.cvtailoring.config.CvTailoringAiProperties;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionType;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.domain.MatchResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CvSuggestionService {

    private final CvTailoringAiProperties properties;
    private final CvRewriteCandidateSelector candidateSelector;
    private final CvRewriteProviderRouter providerRouter;
    private final CvRewriteSafetyGuard safetyGuard;
    private final CvSuggestionValidator suggestionValidator;
    private final CvEvidenceService evidenceService;
    private final CvSourceIdResolver sourceIdResolver;
    private final Clock clock;

    private final Map<String, CacheEntry> cache =
            new ConcurrentHashMap<>();

    public CvSuggestionService(
            CvTailoringAiProperties properties,
            CvRewriteCandidateSelector candidateSelector,
            CvRewriteProviderRouter providerRouter,
            CvRewriteSafetyGuard safetyGuard,
            CvSuggestionValidator suggestionValidator,
            CvEvidenceService evidenceService,
            CvSourceIdResolver sourceIdResolver,
            Clock clock
    ) {
        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );

        this.candidateSelector =
                Objects.requireNonNull(
                        candidateSelector,
                        "candidateSelector must not be null"
                );

        this.providerRouter =
                Objects.requireNonNull(
                        providerRouter,
                        "providerRouter must not be null"
                );

        this.safetyGuard =
                Objects.requireNonNull(
                        safetyGuard,
                        "safetyGuard must not be null"
                );

        this.suggestionValidator =
                Objects.requireNonNull(
                        suggestionValidator,
                        "suggestionValidator must not be null"
                );

        this.evidenceService =
                Objects.requireNonNull(
                        evidenceService,
                        "evidenceService must not be null"
                );

        this.sourceIdResolver =
                Objects.requireNonNull(
                        sourceIdResolver,
                        "sourceIdResolver must not be null"
                );

        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock must not be null"
                );
    }

    public List<SuggestionItem> generateRewrites(
            CandidateProfile profile,
            NormalizedJob job,
            MatchResult targetMatch,
            CvEvidenceService.EvidenceMap evidenceMap
    ) {
        if (!properties.isEnabled()) {
            return List.of();
        }

        RewriteRequest request =
                candidateSelector.select(
                        profile,
                        job,
                        targetMatch,
                        evidenceMap
                );

        if (request.editableNodes() == null
                || request.editableNodes().isEmpty()) {
            return List.of();
        }

        removeExpiredCacheEntries();

        String cacheKey =
                cacheKey(
                        profile,
                        job,
                        targetMatch
                );

        CacheEntry cached =
                cache.get(
                        cacheKey
                );

        if (cached != null
                && Instant.now(clock)
                .isBefore(
                        cached.expiresAt()
                )) {
            return cached.suggestions();
        }

        Optional<RewriteResponse> routed =
                providerRouter.generate(
                        request,
                        response ->
                                responseIsUsable(
                                        response,
                                        request,
                                        profile,
                                        job,
                                        targetMatch,
                                        evidenceMap
                                )
                );

        if (routed.isEmpty()) {
            return List.of();
        }

        List<SuggestionItem> validated =
                normalizeAndValidate(
                        routed.get(),
                        request,
                        profile,
                        job,
                        targetMatch,
                        evidenceMap
                );

        cache.put(
                cacheKey,
                new CacheEntry(
                        Instant.now(clock)
                                .plus(
                                        properties.getCacheTtl()
                                ),
                        validated
                )
        );

        return validated;
    }

    private boolean responseIsUsable(
            RewriteResponse response,
            RewriteRequest request,
            CandidateProfile profile,
            NormalizedJob job,
            MatchResult targetMatch,
            CvEvidenceService.EvidenceMap evidenceMap
    ) {
        if (response == null
                || response.suggestions() == null) {
            return false;
        }

        /*
         * Empty is a legitimate answer:
         * model may decide no truthful rewrite is needed.
         */
        if (response.suggestions().isEmpty()) {
            return true;
        }

        return !normalizeAndValidate(
                response,
                request,
                profile,
                job,
                targetMatch,
                evidenceMap
        ).isEmpty();
    }

    private List<SuggestionItem> normalizeAndValidate(
            RewriteResponse response,
            RewriteRequest request,
            CandidateProfile profile,
            NormalizedJob job,
            MatchResult targetMatch,
            CvEvidenceService.EvidenceMap evidenceMap
    ) {
        Map<String, EditableNode> nodesById =
                new HashMap<>();

        for (EditableNode node : safeList(
                request.editableNodes()
        )) {
            if (node != null
                    && hasText(
                    node.sourceId()
            )) {
                nodesById.put(
                        node.sourceId(),
                        node
                );
            }
        }

        Set<String> allowedTargetSkillKeys =
                allowedTargetSkillKeys(
                        targetMatch,
                        request
                );

        Set<String> usedSourceIds =
                new LinkedHashSet<>();

        List<SuggestionItem> result =
                new ArrayList<>();

        for (RewriteSuggestion raw : safeList(
                response.suggestions()
        )) {
            if (raw == null
                    || result.size()
                    >= properties.getMaxRewriteCandidates()) {
                continue;
            }

            EditableNode node =
                    nodesById.get(
                            raw.sourceId()
                    );

            if (node == null
                    || usedSourceIds.contains(
                    node.sourceId()
            )
                    || !hasText(
                    raw.suggested()
            )) {
                continue;
            }

            List<String> targetSkills =
                    normalizeTargetSkills(
                            raw.targetSkills(),
                            allowedTargetSkillKeys
                    );

            if (targetSkills == null) {
                continue;
            }

            List<String> evidenceIds =
                    normalizeEvidenceIds(
                            raw.evidenceIds(),
                            node
                    );

            if (evidenceIds == null) {
                continue;
            }

            /*
             * Important:
             *
             * text inside EditableNode may be truncated before it is
             * sent to the LLM to save tokens.
             *
             * Never use that truncated value as backend truth.
             * Resolve the complete original again from CandidateProfile.
             */
            CvSourceIdResolver.ResolvedSource resolvedSource;

            try {
                resolvedSource =
                        sourceIdResolver.resolve(
                                profile,
                                node.sourceId()
                        );
            } catch (IllegalArgumentException exception) {
                continue;
            }

            SuggestionItem suggestion =
                    new SuggestionItem(
                            "rewrite-ai-"
                                    + result.size(),
                            SuggestionType.REWRITE,
                            resolvedSource.section(),
                            resolvedSource.sourceId(),
                            resolvedSource.text(),
                            raw.suggested().trim(),
                            buildReason(
                                    targetSkills
                            ),
                            targetSkills,
                            evidenceIds
                    );

            if (!safetyGuard.isSafe(
                    suggestion,
                    evidenceMap,
                    job
            )) {
                continue;
            }

            try {
                List<SuggestionItem> validated =
                        suggestionValidator.validateAll(
                                profile,
                                evidenceMap,
                                List.of(
                                        suggestion
                                )
                        );

                if (!validated.isEmpty()) {
                    result.add(
                            validated.getFirst()
                    );

                    usedSourceIds.add(
                            node.sourceId()
                    );
                }
            } catch (IllegalArgumentException exception) {
                /*
                 * Model output is untrusted.
                 *
                 * Reject only this rewrite.
                 * Never let one hallucinated suggestion break Analyze.
                 */
            }
        }

        return List.copyOf(
                result
        );
    }

    private List<String> normalizeTargetSkills(
            List<String> rawTargetSkills,
            Set<String> allowedTargetSkillKeys
    ) {
        if (rawTargetSkills == null) {
            return null;
        }

        Map<String, String> byKey =
                new java.util.LinkedHashMap<>();

        for (String rawSkill : rawTargetSkills) {
            if (!hasText(rawSkill)) {
                return null;
            }

            String key =
                    evidenceService
                            .canonicalSkillKey(
                                    rawSkill
                            );

            if (key.isBlank()
                    || !allowedTargetSkillKeys.contains(
                    key
            )) {
                return null;
            }

            byKey.putIfAbsent(
                    key,
                    rawSkill.trim()
            );
        }

        return List.copyOf(
                byKey.values()
        );
    }

    private List<String> normalizeEvidenceIds(
            List<String> rawEvidenceIds,
            EditableNode node
    ) {
        if (rawEvidenceIds == null) {
            return null;
        }

        Set<String> allowed =
                new LinkedHashSet<>(
                        safeList(
                                node.allowedEvidenceIds()
                        )
                );

        Set<String> selected =
                new LinkedHashSet<>();

        for (String evidenceId : rawEvidenceIds) {
            if (!hasText(evidenceId)
                    || !allowed.contains(
                    evidenceId
            )) {
                return null;
            }

            selected.add(
                    evidenceId
            );
        }

        /*
         * Always retain provenance of the original editable text.
         */
        if (allowed.contains(
                node.sourceId()
        )) {
            selected.add(
                    node.sourceId()
            );
        }

        return List.copyOf(
                selected
        );
    }

    private Set<String> allowedTargetSkillKeys(
            MatchResult targetMatch,
            RewriteRequest request
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        Set<String> missing =
                new LinkedHashSet<>();

        for (String skill : safeList(
                targetMatch.getMissingSkills()
        )) {
            String key =
                    evidenceService
                            .canonicalSkillKey(
                                    skill
                            );

            if (!key.isBlank()) {
                missing.add(
                        key
                );
            }
        }

        for (String skill : safeList(
                targetMatch.getMatchedSkills()
        )) {
            String key =
                    evidenceService
                            .canonicalSkillKey(
                                    skill
                            );

            if (!key.isBlank()
                    && !missing.contains(key)) {
                result.add(
                        key
                );
            }
        }

        String jobCorpus =
                compactForSearch(
                        request.job() == null
                                ? ""
                                : safeText(
                                request.job().title()
                        )
                                + " "
                                + String.join(
                                " ",
                                safeList(
                                        request.job().skills()
                                )
                        )
                                + " "
                                + safeText(
                                request.job().requirements()
                        )
                                + " "
                                + safeText(
                                request.job().description()
                        )
                );

        /*
         * Matching taxonomy may not know every useful CV skill.
         *
         * Example:
         * evidence contains REST API,
         * real JD explicitly asks for REST API,
         * but matchedSkills only contains Java/Spring Boot.
         *
         * It is still safe to expose REST API to the rewrite model
         * when CandidateProfile proves it AND matching did not mark
         * it as missing.
         */
        for (var evidence : safeList(
                request.evidenceCatalog()
        )) {
            if (evidence == null
                    || !hasText(
                    evidence.canonicalSkillKey()
            )
                    || missing.contains(
                    evidence.canonicalSkillKey()
            )) {
                continue;
            }

            String phrase =
                    compactForSearch(
                            evidence.value()
                    );

            if (containsSearchPhrase(
                    jobCorpus,
                    phrase
            )) {
                result.add(
                        evidence.canonicalSkillKey()
                );
            }
        }

        return result;
    }

    private String compactForSearch(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return "";
        }

        String normalized =
                java.text.Normalizer
                        .normalize(
                                value,
                                java.text.Normalizer.Form.NFD
                        )
                        .toLowerCase(
                                java.util.Locale.ROOT
                        )
                        .replace(
                                'đ',
                                'd'
                        );

        normalized =
                normalized.replaceAll(
                        "\\p{M}+",
                        ""
                );

        normalized =
                normalized.replaceAll(
                        "[^a-z0-9]+",
                        " "
                ).trim();

        return normalized.replaceAll(
                "\\s+",
                " "
        );
    }

    private boolean containsSearchPhrase(
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
                        " " + compactPhrase + " "
                );
    }

    private String buildReason(
            List<String> targetSkills
    ) {
        if (targetSkills == null
                || targetSkills.isEmpty()) {
            return "Improves the wording of this existing content "
                    + "for the selected job without adding "
                    + "unsupported facts.";
        }

        return "Makes existing evidence for "
                + String.join(
                ", ",
                targetSkills
        )
                + " clearer and more relevant to the selected job.";
    }

    private String cacheKey(
            CandidateProfile profile,
            NormalizedJob job,
            MatchResult targetMatch
    ) {
        String material =
                safeText(
                        profile.getId()
                )
                        + "|"
                        + safeText(
                        profile.getSourceSha256()
                )
                        + "|"
                        + String.valueOf(
                        profile.getUpdatedAt()
                )
                        + "|"
                        + safeText(
                        job.getId()
                )
                        + "|"
                        + safeText(
                        job.getRawContentHash()
                )
                        + "|"
                        + String.valueOf(
                        job.getNormalizedAt()
                )
                        + "|"
                        + safeText(
                        targetMatch.getCandidateEmbeddingId()
                )
                        + "|"
                        + safeText(
                        targetMatch.getRankingVersion()
                )
                        + "|"
                        + String.join(
                        ",",
                        safeList(
                                targetMatch.getMatchedSkills()
                        )
                )
                        + "|"
                        + String.join(
                        ",",
                        safeList(
                                targetMatch.getMissingSkills()
                        )
                )
                        + "|"
                        + properties.getPromptVersion()
                        + "|"
                        + properties
                        .getGemini()
                        .getModel()
                        + "|"
                        + properties
                        .getGroq()
                        .getModel();

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return HexFormat.of()
                    .formatHex(
                            digest.digest(
                                    material.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
                    );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }

    private void removeExpiredCacheEntries() {
        Instant now =
                Instant.now(
                        clock
                );

        cache.entrySet()
                .removeIf(
                        entry ->
                                !now.isBefore(
                                        entry.getValue()
                                                .expiresAt()
                                )
                );
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    private String safeText(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }

    private <T> List<T> safeList(
            List<T> values
    ) {
        return values == null
                ? List.of()
                : values;
    }

    private record CacheEntry(
            Instant expiresAt,
            List<SuggestionItem> suggestions
    ) {
        private CacheEntry {
            suggestions =
                    suggestions == null
                            ? List.of()
                            : List.copyOf(
                            suggestions
                    );
        }
    }
}