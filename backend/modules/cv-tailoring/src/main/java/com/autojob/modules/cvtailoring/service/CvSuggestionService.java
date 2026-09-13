package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.CoachingSuggestion;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.EditableNode;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteRequest;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteResponse;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteSuggestion;
import com.autojob.modules.cvtailoring.ai.CvRewriteProviderRouter;
import com.autojob.modules.cvtailoring.config.CvTailoringAiProperties;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.CoachingItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.CoachingType;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionCategory;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionPriority;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionType;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.domain.MatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CvSuggestionService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    CvSuggestionService.class
            );

    private static final int MAX_COACHING_ITEMS =
            3;

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

    private final Map<String, CacheEntry> latestByContext =
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
        return generateContent(
                profile,
                job,
                targetMatch,
                evidenceMap
        ).suggestions();
    }

    /**
     * CvTailoringAnalysisService invokes CvCoachingService
     * after generateRewrites().
     *
     * This only reads coaching generated by the SAME AI call.
     * It never makes another provider request.
     */
    public List<CoachingItem> getCachedCoaching(
            CandidateProfile profile,
            MatchResult targetMatch
    ) {
        removeExpiredCacheEntries();

        String contextKey =
                contextKey(
                        profile,
                        targetMatch,
                        resolveUiLocale()
                );

        CacheEntry entry =
                latestByContext.get(
                        contextKey
                );

        if (entry == null
                || !Instant
                .now(
                        clock
                )
                .isBefore(
                        entry.expiresAt()
                )) {

            latestByContext.remove(
                    contextKey
            );

            return List.of();
        }

        return entry
                .content()
                .coaching();
    }

    private GeneratedContent generateContent(
            CandidateProfile profile,
            NormalizedJob job,
            MatchResult targetMatch,
            CvEvidenceService.EvidenceMap evidenceMap
    ) {
        if (!properties.isEnabled()) {
            return GeneratedContent.empty();
        }

        RewriteRequest selected =
                candidateSelector.select(
                        profile,
                        job,
                        targetMatch,
                        evidenceMap
                );

        if (selected.editableNodes() == null
                || selected
                .editableNodes()
                .isEmpty()) {

            log.info(
                    "CV tailoring AI skipped "
                            + "reason=no-editable-nodes"
            );

            return GeneratedContent.empty();
        }

        RewriteRequest request =
                new RewriteRequest(
                        resolveUiLocale(),
                        selected.job(),
                        selected.editableNodes(),
                        selected.evidenceCatalog()
                );

        removeExpiredCacheEntries();

        String cacheKey =
                cacheKey(
                        profile,
                        job,
                        targetMatch,
                        request.locale()
                );

        CacheEntry cached =
                cache.get(
                        cacheKey
                );

        if (cached != null
                && Instant
                .now(
                        clock
                )
                .isBefore(
                        cached.expiresAt()
                )) {

            latestByContext.put(
                    cached.contextKey(),
                    cached
            );

            log.info(
                    "CV tailoring AI cache hit "
                            + "acceptedRewrites={} "
                            + "acceptedCoaching={}",
                    cached
                            .content()
                            .suggestions()
                            .size(),
                    cached
                            .content()
                            .coaching()
                            .size()
            );

            return cached.content();
        }

        /*
         * The predicate is invoked inside ProviderRouter.
         *
         * Keep the evaluation that actually passed so we
         * do not need to normalize the same provider output
         * twice.
         */
        AtomicReference<ResponseEvaluation>
                acceptedEvaluation =
                new AtomicReference<>();

        Optional<RewriteResponse> routed =
                providerRouter.generate(
                        request,
                        response -> {

                            ResponseEvaluation evaluation =
                                    evaluateResponse(
                                            response,
                                            request,
                                            profile,
                                            job,
                                            targetMatch,
                                            evidenceMap
                                    );

                            logEvaluation(
                                    evaluation
                            );

                            boolean usable =
                                    responseIsUsable(
                                            evaluation
                                    );

                            if (usable) {

                                acceptedEvaluation.set(
                                        evaluation
                                );
                            }

                            return usable;
                        }
                );

        GeneratedContent content;

        if (routed.isEmpty()) {

            content =
                    GeneratedContent.empty();

        } else {

            ResponseEvaluation evaluation =
                    acceptedEvaluation.get();

            /*
             * ProviderRouter may return its best-effort
             * rejected response when every provider is
             * exhausted.
             *
             * Re-evaluate that response here so valid
             * coaching is still preserved without making
             * another AI call.
             */
            if (evaluation == null
                    || evaluation.response()
                    != routed.get()) {

                evaluation =
                        evaluateResponse(
                                routed.get(),
                                request,
                                profile,
                                job,
                                targetMatch,
                                evidenceMap
                        );

                logEvaluation(
                        evaluation
                );
            }

            content =
                    evaluation.content();
        }

        CacheEntry entry =
                new CacheEntry(
                        Instant
                                .now(
                                        clock
                                )
                                .plus(
                                        properties
                                                .getCacheTtl()
                                ),

                        contextKey(
                                profile,
                                targetMatch,
                                request.locale()
                        ),

                        content
                );

        cache.put(
                cacheKey,
                entry
        );

        latestByContext.put(
                entry.contextKey(),
                entry
        );

        return content;
    }

    /**
     * Provider routing policy.
     *
     * Case A:
     *
     * raw rewrite > 0
     * accepted rewrite > 0
     *
     * => good provider response.
     *
     *
     * Case B:
     *
     * raw rewrite > 0
     * accepted rewrite == 0
     *
     * => rewrite was attempted but backend safety /
     * validation rejected every rewrite.
     *
     * Coaching must NOT hide that.
     *
     * Try the next provider.
     *
     *
     * Case C:
     *
     * raw rewrite == 0
     * accepted coaching > 0
     *
     * => model intentionally produced coaching-only.
     *
     * This is a legitimate result.
     *
     *
     * Case D:
     *
     * no rewrite
     * no coaching
     *
     * => useless response, try next provider.
     */
    private boolean responseIsUsable(
            ResponseEvaluation evaluation
    ) {
        if (evaluation == null
                || evaluation.response() == null) {

            return false;
        }

        ValidationDiagnostics diagnostics =
                evaluation.diagnostics();

        if (diagnostics.rawRewriteCount()
                > 0) {

            return diagnostics
                    .acceptedRewriteCount()
                    > 0;
        }

        if (diagnostics.rawCoachingCount()
                > 0) {

            return diagnostics
                    .acceptedCoachingCount()
                    > 0;
        }

        return false;
    }

    private ResponseEvaluation evaluateResponse(
            RewriteResponse response,
            RewriteRequest request,
            CandidateProfile profile,
            NormalizedJob job,
            MatchResult targetMatch,
            CvEvidenceService.EvidenceMap evidenceMap
    ) {
        ValidationCounters counters =
                new ValidationCounters();

        if (response == null) {

            counters.rejectedNullResponse++;

            return new ResponseEvaluation(
                    null,
                    GeneratedContent.empty(),
                    counters.snapshot()
            );
        }

        Map<String, EditableNode> nodesById =
                new HashMap<>();

        for (EditableNode node :
                safeList(
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

        List<SuggestionItem> suggestions =
                normalizeSuggestions(
                        response.suggestions(),
                        nodesById,
                        allowedTargetSkillKeys,
                        profile,
                        job,
                        evidenceMap,
                        counters
                );

        List<CoachingItem> coaching =
                normalizeCoaching(
                        response.coaching(),
                        nodesById,
                        profile,
                        counters
                );

        return new ResponseEvaluation(
                response,

                new GeneratedContent(
                        suggestions,
                        coaching
                ),

                counters.snapshot()
        );
    }

    private List<SuggestionItem>
    normalizeSuggestions(
            List<RewriteSuggestion> rawSuggestions,
            Map<String, EditableNode> nodesById,
            Set<String> allowedTargetSkillKeys,
            CandidateProfile profile,
            NormalizedJob job,
            CvEvidenceService.EvidenceMap evidenceMap,
            ValidationCounters counters
    ) {
        List<RewriteSuggestion> safeRaw =
                safeList(
                        rawSuggestions
                );

        counters.rawRewriteCount +=
                safeRaw.size();

        Set<String> usedSourceIds =
                new LinkedHashSet<>();

        List<SuggestionItem> result =
                new ArrayList<>();

        for (RewriteSuggestion raw :
                safeRaw) {

            if (result.size()
                    >= properties
                    .getMaxRewriteCandidates()) {

                counters.rejectedRewriteLimit++;

                continue;
            }

            if (raw == null) {

                counters.rejectedRewriteNull++;

                continue;
            }

            EditableNode node =
                    nodesById.get(
                            raw.sourceId()
                    );

            if (node == null) {

                counters
                        .rejectedRewriteUnknownSource++;

                continue;
            }

            if (usedSourceIds.contains(
                    node.sourceId()
            )) {

                counters
                        .rejectedRewriteDuplicateSource++;

                continue;
            }

            if (!hasText(
                    raw.suggested()
            )) {

                counters
                        .rejectedRewriteEmptyText++;

                continue;
            }

            /*
             * Do not reject the entire rewrite merely
             * because model metadata contains one bad
             * targetSkill.
             *
             * Unsupported metadata is removed.
             *
             * SafetyGuard + Validator still reject the
             * actual rewritten text if it introduces an
             * unsupported skill.
             */
            NormalizedTargetSkills
                    targetSkillResult =
                    normalizeTargetSkills(
                            raw.targetSkills(),
                            allowedTargetSkillKeys
                    );

            counters.filteredTargetSkills +=
                    targetSkillResult
                            .filteredCount();

            /*
             * Same approach for evidence metadata.
             *
             * Invalid IDs are ignored.
             *
             * The original source provenance is always
             * included.
             *
             * If that is insufficient to support the
             * rewritten claim, SafetyGuard / Validator
             * will still reject it.
             */
            NormalizedEvidenceIds
                    evidenceResult =
                    normalizeEvidenceIds(
                            raw.evidenceIds(),
                            node
                    );

            counters.filteredEvidenceIds +=
                    evidenceResult
                            .filteredCount();

            CvSourceIdResolver.ResolvedSource
                    resolvedSource;

            try {
                resolvedSource =
                        sourceIdResolver.resolve(
                                profile,
                                node.sourceId()
                        );

            } catch (
                    IllegalArgumentException
                            exception
            ) {

                counters
                        .rejectedRewriteSourceResolve++;

                continue;
            }

            SuggestionItem suggestion =
                    new SuggestionItem(
                            "rewrite-ai-"
                                    + result.size(),

                            SuggestionType.REWRITE,

                            resolvedSource.section()
                                    == Section
                                    .PROFESSIONAL_SUMMARY
                                    ? SuggestionCategory
                                    .POSITION
                                    : SuggestionCategory
                                    .REWRITE,

                            priorityFor(
                                    resolvedSource,
                                    targetSkillResult
                                            .skills()
                            ),

                            resolvedSource.section(),

                            resolvedSource.sourceId(),

                            resolvedSource.text(),

                            raw
                                    .suggested()
                                    .trim(),

                            null,

                            buildInternalReason(
                                    targetSkillResult
                                            .skills()
                            ),

                            targetSkillResult
                                    .skills(),

                            evidenceResult
                                    .evidenceIds()
                    );

            if (!safetyGuard.isSafe(
                    suggestion,
                    evidenceMap,
                    job
            )) {

                counters
                        .rejectedRewriteSafety++;

                continue;
            }

            try {
                List<SuggestionItem> validated =
                        suggestionValidator
                                .validateAll(
                                        profile,
                                        evidenceMap,
                                        List.of(
                                                suggestion
                                        )
                                );

                if (validated.isEmpty()) {

                    counters
                            .rejectedRewriteValidator++;

                    continue;
                }

                result.add(
                        validated.getFirst()
                );

                usedSourceIds.add(
                        node.sourceId()
                );

                counters
                        .acceptedRewriteCount++;

            } catch (
                    IllegalArgumentException
                            exception
            ) {

                counters
                        .rejectedRewriteValidator++;
            }
        }

        return List.copyOf(
                result
        );
    }

    private List<CoachingItem>
    normalizeCoaching(
            List<CoachingSuggestion> rawCoaching,
            Map<String, EditableNode> nodesById,
            CandidateProfile profile,
            ValidationCounters counters
    ) {
        List<CoachingSuggestion> safeRaw =
                safeList(
                        rawCoaching
                );

        counters.rawCoachingCount +=
                safeRaw.size();

        Set<String> usedSourceIds =
                new LinkedHashSet<>();

        Set<String> usedQuestions =
                new LinkedHashSet<>();

        List<CoachingItem> result =
                new ArrayList<>();

        for (CoachingSuggestion raw :
                safeRaw) {

            if (result.size()
                    >= MAX_COACHING_ITEMS) {

                counters
                        .rejectedCoachingLimit++;

                continue;
            }

            if (raw == null
                    || !hasText(
                    raw.sourceId()
            )
                    || !hasText(
                    raw.question()
            )
                    || !hasText(
                    raw.reason()
            )) {

                counters
                        .rejectedCoachingInvalid++;

                continue;
            }

            EditableNode node =
                    nodesById.get(
                            raw.sourceId()
                    );

            if (node == null
                    || usedSourceIds.contains(
                    node.sourceId()
            )) {

                counters
                        .rejectedCoachingInvalid++;

                continue;
            }

            CvSourceIdResolver.ResolvedSource
                    source;

            try {
                source =
                        sourceIdResolver.resolve(
                                profile,
                                node.sourceId()
                        );

            } catch (
                    IllegalArgumentException
                            exception
            ) {

                counters
                        .rejectedCoachingInvalid++;

                continue;
            }

            if (source.section()
                    != Section.WORK_EXPERIENCE
                    && source.section()
                    != Section.PROJECT) {

                counters
                        .rejectedCoachingInvalid++;

                continue;
            }

            NormalizedEvidenceIds
                    evidenceResult =
                    normalizeEvidenceIds(
                            raw.evidenceIds(),
                            node
                    );

            counters.filteredEvidenceIds +=
                    evidenceResult
                            .filteredCount();

            String question =
                    cleanDisplayText(
                            raw.question(),
                            700
                    );

            String reason =
                    cleanDisplayText(
                            raw.reason(),
                            900
                    );

            String questionKey =
                    compactText(
                            question
                    );

            if (!hasText(
                    question
            )
                    || !hasText(
                    reason
            )
                    || questionKey.isBlank()) {

                counters
                        .rejectedCoachingInvalid++;

                continue;
            }

            if (isNearDuplicateQuestion(
                    questionKey,
                    usedQuestions
            )) {

                counters
                        .rejectedCoachingDuplicate++;

                continue;
            }

            usedQuestions.add(
                    questionKey
            );

            result.add(
                    new CoachingItem(
                            "needs-input-ai-"
                                    + result.size(),

                            CoachingType.NEEDS_INPUT,

                            source.kind()
                                    == CvSourceIdResolver
                                    .SourceKind
                                    .ACHIEVEMENT
                                    ? SuggestionPriority
                                    .HIGH
                                    : SuggestionPriority
                                    .MEDIUM,

                            source.section(),

                            source.sourceId(),

                            source.text(),

                            question,

                            reason,

                            List.of(),

                            evidenceResult
                                    .evidenceIds()
                    )
            );

            usedSourceIds.add(
                    node.sourceId()
            );

            counters
                    .acceptedCoachingCount++;
        }

        return List.copyOf(
                result
        );
    }

    /**
     * Provider metadata is not candidate truth.
     *
     * One spurious target skill from the model should
     * not automatically discard an otherwise safe
     * rewrite.
     *
     * Unsupported metadata is filtered.
     *
     * If rewritten text actually introduces that
     * unsupported skill, SafetyGuard / Validator still
     * reject the rewrite.
     */
    private NormalizedTargetSkills
    normalizeTargetSkills(
            List<String> rawTargetSkills,
            Set<String> allowedTargetSkillKeys
    ) {
        if (rawTargetSkills == null
                || rawTargetSkills.isEmpty()) {

            return new NormalizedTargetSkills(
                    List.of(),
                    0
            );
        }

        Map<String, String> byKey =
                new LinkedHashMap<>();

        int filtered =
                0;

        for (String rawSkill :
                rawTargetSkills) {

            if (!hasText(
                    rawSkill
            )) {

                filtered++;

                continue;
            }

            String key =
                    evidenceService
                            .canonicalSkillKey(
                                    rawSkill
                            );

            if (key.isBlank()
                    || !allowedTargetSkillKeys
                    .contains(
                            key
                    )) {

                filtered++;

                continue;
            }

            byKey.putIfAbsent(
                    key,
                    rawSkill.trim()
            );
        }

        return new NormalizedTargetSkills(
                List.copyOf(
                        byKey.values()
                ),
                filtered
        );
    }

    /**
     * Invalid model-selected evidence IDs are ignored.
     *
     * Original source provenance is always included
     * when available.
     *
     * This does NOT bypass safety:
     *
     * - SafetyGuard still checks factual additions.
     * - Validator still checks target skill evidence.
     */
    private NormalizedEvidenceIds
    normalizeEvidenceIds(
            List<String> rawEvidenceIds,
            EditableNode node
    ) {
        Set<String> allowed =
                new LinkedHashSet<>(
                        safeList(
                                node.allowedEvidenceIds()
                        )
                );

        Set<String> selected =
                new LinkedHashSet<>();

        int filtered =
                0;

        for (String evidenceId :
                safeList(
                        rawEvidenceIds
                )) {

            if (!hasText(
                    evidenceId
            )
                    || !allowed.contains(
                    evidenceId
            )) {

                filtered++;

                continue;
            }

            selected.add(
                    evidenceId
            );
        }

        /*
         * Always preserve original text provenance.
         */
        if (allowed.contains(
                node.sourceId()
        )) {

            selected.add(
                    node.sourceId()
            );
        }

        return new NormalizedEvidenceIds(
                List.copyOf(
                        selected
                ),
                filtered
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

        for (String skill :
                safeList(
                        targetMatch
                                .getMissingSkills()
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

        for (String skill :
                safeList(
                        targetMatch
                                .getMatchedSkills()
                )) {

            String key =
                    evidenceService
                            .canonicalSkillKey(
                                    skill
                            );

            if (!key.isBlank()
                    && !missing.contains(
                    key
            )) {

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
                                request
                                        .job()
                                        .title()
                        )
                                + " "
                                + String.join(
                                " ",
                                safeList(
                                        request
                                                .job()
                                                .skills()
                                )
                        )
                                + " "
                                + safeText(
                                request
                                        .job()
                                        .requirements()
                        )
                                + " "
                                + safeText(
                                request
                                        .job()
                                        .description()
                        )
                );

        for (var evidence :
                safeList(
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

            if (containsSearchPhrase(
                    jobCorpus,
                    compactForSearch(
                            evidence.value()
                    )
            )) {

                result.add(
                        evidence.canonicalSkillKey()
                );
            }
        }

        return result;
    }

    private SuggestionPriority priorityFor(
            CvSourceIdResolver.ResolvedSource source,
            List<String> targetSkills
    ) {
        if (source.section()
                == Section.PROFESSIONAL_SUMMARY
                || source.kind()
                == CvSourceIdResolver
                .SourceKind
                .ACHIEVEMENT
                || targetSkills.size() >= 2) {

            return SuggestionPriority.HIGH;
        }

        return SuggestionPriority.MEDIUM;
    }

    /**
     * Internal backend metadata.
     *
     * FE should not display this string.
     */
    private String buildInternalReason(
            List<String> targetSkills
    ) {
        if (targetSkills == null
                || targetSkills.isEmpty()) {

            return "Improves existing CV wording for the "
                    + "selected job without adding "
                    + "unsupported facts.";
        }

        return "Makes existing evidence for "
                + String.join(
                ", ",
                targetSkills
        )
                + " clearer for the selected job.";
    }

    private String cleanDisplayText(
            String value,
            int maxChars
    ) {
        if (!hasText(
                value
        )) {

            return null;
        }

        String cleaned =
                value
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );

        if (cleaned.length()
                <= maxChars) {

            return cleaned;
        }

        return cleaned
                .substring(
                        0,
                        maxChars
                )
                .trim();
    }

    private String compactText(
            String value
    ) {
        if (!hasText(
                value
        )) {

            return "";
        }

        return value
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "[^\\p{L}\\p{N}]+",
                        " "
                )
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }

    /**
     * Avoid showing three nearly identical coaching
     * questions with only one or two words changed.
     *
     * Domain/language agnostic.
     */
    private boolean isNearDuplicateQuestion(
            String candidate,
            Set<String> existingQuestions
    ) {
        Set<String> candidateTokens =
                tokenSet(
                        candidate
                );

        for (String existing :
                existingQuestions) {

            if (candidate.equals(
                    existing
            )) {

                return true;
            }

            Set<String> existingTokens =
                    tokenSet(
                            existing
                    );

            if (candidateTokens.isEmpty()
                    || existingTokens.isEmpty()) {

                continue;
            }

            Set<String> intersection =
                    new LinkedHashSet<>(
                            candidateTokens
                    );

            intersection.retainAll(
                    existingTokens
            );

            Set<String> union =
                    new LinkedHashSet<>(
                            candidateTokens
                    );

            union.addAll(
                    existingTokens
            );

            double similarity =
                    union.isEmpty()
                            ? 0.0d
                            : intersection.size()
                            / (double) union.size();

            if (similarity >= 0.72d) {

                return true;
            }
        }

        return false;
    }

    private Set<String> tokenSet(
            String value
    ) {
        if (!hasText(
                value
        )) {

            return Set.of();
        }

        Set<String> result =
                new LinkedHashSet<>();

        for (String token :
                compactText(
                        value
                ).split(
                        " "
                )) {

            if (token.length() >= 2) {

                result.add(
                        token
                );
            }
        }

        return result;
    }

    private String compactForSearch(
            String value
    ) {
        if (!hasText(
                value
        )) {

            return "";
        }

        /*
         * Do not retain ordinary punctuation because:
         *
         * "Bachelor of Nursing."
         *
         * must match:
         *
         * "Bachelor of Nursing"
         *
         * Keep + and # for C++ / C#.
         */
        String normalized =
                value
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .replaceAll(
                                "[^\\p{L}\\p{N}+#]+",
                                " "
                        )
                        .trim();

        return normalized.replaceAll(
                "\\s+",
                " "
        );
    }

    private boolean containsSearchPhrase(
            String text,
            String phrase
    ) {
        return hasText(
                text
        )
                && hasText(
                phrase
        )
                && (
                " "
                        + text
                        + " "
        ).contains(
                " "
                        + phrase
                        + " "
        );
    }

    private String resolveUiLocale() {

        Locale locale =
                LocaleContextHolder
                        .getLocale();

        return locale != null
                && locale.getLanguage() != null
                && locale
                .getLanguage()
                .toLowerCase(
                        Locale.ROOT
                )
                .startsWith(
                        "vi"
                )
                ? "vi"
                : "en";
    }

    private String contextKey(
            CandidateProfile profile,
            MatchResult targetMatch,
            String locale
    ) {
        return safeText(
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
                targetMatch
                        .getNormalizedJobId()
        )
                + "|"
                + safeText(
                targetMatch
                        .getCandidateEmbeddingId()
        )
                + "|"
                + safeText(
                targetMatch
                        .getRankingVersion()
        )
                + "|"
                + safeText(
                locale
        );
    }

    private String cacheKey(
            CandidateProfile profile,
            NormalizedJob job,
            MatchResult targetMatch,
            String locale
    ) {
        String material =
                contextKey(
                        profile,
                        targetMatch,
                        locale
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
                        + String.join(
                        ",",
                        safeList(
                                targetMatch
                                        .getMatchedSkills()
                        )
                )
                        + "|"
                        + String.join(
                        ",",
                        safeList(
                                targetMatch
                                        .getMissingSkills()
                        )
                )
                        + "|"
                        + properties
                        .getPromptVersion()

                        /*
                         * Explicit implementation version.
                         *
                         * This invalidates cache produced by the
                         * old coaching-first pipeline even if
                         * deployment configuration still says
                         * cv-coach-v2.
                         */
                        + "|rewrite-coaching-v3"

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

            return HexFormat
                    .of()
                    .formatHex(
                            digest.digest(
                                    material.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
                    );

        } catch (
                NoSuchAlgorithmException
                        exception
        ) {

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

        cache
                .entrySet()
                .removeIf(
                        entry ->
                                !now.isBefore(
                                        entry
                                                .getValue()
                                                .expiresAt()
                                )
                );

        latestByContext
                .entrySet()
                .removeIf(
                        entry ->
                                !now.isBefore(
                                        entry
                                                .getValue()
                                                .expiresAt()
                                )
                );
    }

    /**
     * Diagnostic logging intentionally contains counts only.
     *
     * Never log:
     * - CV text
     * - JD text
     * - candidate personal data
     * - API keys
     */
    private void logEvaluation(
            ResponseEvaluation evaluation
    ) {
        ValidationDiagnostics d =
                evaluation.diagnostics();

        String mode;

        if (d.rawRewriteCount() == 0
                && d.rawCoachingCount()
                > 0) {

            mode =
                    "coaching-only";

        } else if (
                d.rawRewriteCount() == 0
                        && d.rawCoachingCount()
                        == 0
        ) {

            mode =
                    "empty";

        } else if (
                d.acceptedRewriteCount()
                        == 0
        ) {

            mode =
                    "rewrite-rejected";

        } else {

            mode =
                    "rewrite-accepted";
        }

        log.info(
                "CV tailoring AI validation "
                        + "mode={} "
                        + "rawRewrites={} "
                        + "acceptedRewrites={} "
                        + "rawCoaching={} "
                        + "acceptedCoaching={} "
                        + "filteredTargetSkills={} "
                        + "filteredEvidenceIds={} "
                        + "rejectedUnknownSource={} "
                        + "rejectedDuplicateSource={} "
                        + "rejectedEmptyText={} "
                        + "rejectedSourceResolve={} "
                        + "rejectedSafety={} "
                        + "rejectedValidator={} "
                        + "rejectedRewriteLimit={} "
                        + "rejectedCoachingInvalid={} "
                        + "rejectedCoachingDuplicate={} "
                        + "rejectedCoachingLimit={}",

                mode,

                d.rawRewriteCount(),

                d.acceptedRewriteCount(),

                d.rawCoachingCount(),

                d.acceptedCoachingCount(),

                d.filteredTargetSkills(),

                d.filteredEvidenceIds(),

                d.rejectedRewriteUnknownSource(),

                d.rejectedRewriteDuplicateSource(),

                d.rejectedRewriteEmptyText(),

                d.rejectedRewriteSourceResolve(),

                d.rejectedRewriteSafety(),

                d.rejectedRewriteValidator(),

                d.rejectedRewriteLimit(),

                d.rejectedCoachingInvalid(),

                d.rejectedCoachingDuplicate(),

                d.rejectedCoachingLimit()
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

    private record GeneratedContent(
            List<SuggestionItem> suggestions,
            List<CoachingItem> coaching
    ) {

        private GeneratedContent {

            suggestions =
                    suggestions == null
                            ? List.of()
                            : List.copyOf(
                            suggestions
                    );

            coaching =
                    coaching == null
                            ? List.of()
                            : List.copyOf(
                            coaching
                    );
        }

        private static GeneratedContent empty() {

            return new GeneratedContent(
                    List.of(),
                    List.of()
            );
        }
    }

    private record CacheEntry(
            Instant expiresAt,
            String contextKey,
            GeneratedContent content
    ) {
    }

    private record NormalizedTargetSkills(
            List<String> skills,
            int filteredCount
    ) {
    }

    private record NormalizedEvidenceIds(
            List<String> evidenceIds,
            int filteredCount
    ) {
    }

    private record ResponseEvaluation(
            RewriteResponse response,
            GeneratedContent content,
            ValidationDiagnostics diagnostics
    ) {
    }

    private record ValidationDiagnostics(
            int rawRewriteCount,
            int acceptedRewriteCount,
            int rawCoachingCount,
            int acceptedCoachingCount,
            int filteredTargetSkills,
            int filteredEvidenceIds,
            int rejectedRewriteNull,
            int rejectedRewriteUnknownSource,
            int rejectedRewriteDuplicateSource,
            int rejectedRewriteEmptyText,
            int rejectedRewriteSourceResolve,
            int rejectedRewriteSafety,
            int rejectedRewriteValidator,
            int rejectedRewriteLimit,
            int rejectedCoachingInvalid,
            int rejectedCoachingDuplicate,
            int rejectedCoachingLimit,
            int rejectedNullResponse
    ) {
    }

    private static final class ValidationCounters {

        private int rawRewriteCount;

        private int acceptedRewriteCount;

        private int rawCoachingCount;

        private int acceptedCoachingCount;

        private int filteredTargetSkills;

        private int filteredEvidenceIds;

        private int rejectedRewriteNull;

        private int rejectedRewriteUnknownSource;

        private int rejectedRewriteDuplicateSource;

        private int rejectedRewriteEmptyText;

        private int rejectedRewriteSourceResolve;

        private int rejectedRewriteSafety;

        private int rejectedRewriteValidator;

        private int rejectedRewriteLimit;

        private int rejectedCoachingInvalid;

        private int rejectedCoachingDuplicate;

        private int rejectedCoachingLimit;

        private int rejectedNullResponse;

        private ValidationDiagnostics snapshot() {

            return new ValidationDiagnostics(
                    rawRewriteCount,
                    acceptedRewriteCount,
                    rawCoachingCount,
                    acceptedCoachingCount,
                    filteredTargetSkills,
                    filteredEvidenceIds,
                    rejectedRewriteNull,
                    rejectedRewriteUnknownSource,
                    rejectedRewriteDuplicateSource,
                    rejectedRewriteEmptyText,
                    rejectedRewriteSourceResolve,
                    rejectedRewriteSafety,
                    rejectedRewriteValidator,
                    rejectedRewriteLimit,
                    rejectedCoachingInvalid,
                    rejectedCoachingDuplicate,
                    rejectedCoachingLimit,
                    rejectedNullResponse
            );
        }
    }
}