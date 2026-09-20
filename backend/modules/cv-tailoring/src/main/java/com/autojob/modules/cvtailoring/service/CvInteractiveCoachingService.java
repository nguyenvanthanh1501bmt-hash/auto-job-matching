package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.EditableNode;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.EvidenceValue;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.JobContext;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteRequest;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteResponse;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteSuggestion;
import com.autojob.modules.cvtailoring.ai.CvRewriteProviderRouter;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.CoachingItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionCategory;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionType;
import com.autojob.modules.cvtailoring.domain.TailoredCvDraft.GeneratedCoachingSuggestion;
import com.autojob.modules.cvtailoring.domain.TailoredCvDraft.UserConfirmedEvidence;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CvInteractiveCoachingService {

    private final CvRewriteProviderRouter providerRouter;
    private final CvRewriteSafetyGuard safetyGuard;
    private final CvSuggestionValidator suggestionValidator;
    private final CvEvidenceService evidenceService;
    private final CvSourceIdResolver sourceIdResolver;
    private final Clock clock;

    public CvInteractiveCoachingService(
            CvRewriteProviderRouter providerRouter,
            CvRewriteSafetyGuard safetyGuard,
            CvSuggestionValidator suggestionValidator,
            CvEvidenceService evidenceService,
            CvSourceIdResolver sourceIdResolver,
            Clock clock
    ) {
        this.providerRouter = Objects.requireNonNull(
                providerRouter,
                "providerRouter must not be null"
        );
        this.safetyGuard = Objects.requireNonNull(
                safetyGuard,
                "safetyGuard must not be null"
        );
        this.suggestionValidator = Objects.requireNonNull(
                suggestionValidator,
                "suggestionValidator must not be null"
        );
        this.evidenceService = Objects.requireNonNull(
                evidenceService,
                "evidenceService must not be null"
        );
        this.sourceIdResolver = Objects.requireNonNull(
                sourceIdResolver,
                "sourceIdResolver must not be null"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
    }

    public GeneratedCoachingSuggestion generate(
            CandidateProfile profile,
            NormalizedJob job,
            CoachingItem coaching,
            UserConfirmedEvidence confirmedEvidence
    ) {
        Objects.requireNonNull(
                profile,
                "profile must not be null"
        );
        Objects.requireNonNull(
                job,
                "job must not be null"
        );
        Objects.requireNonNull(
                coaching,
                "coaching must not be null"
        );
        Objects.requireNonNull(
                confirmedEvidence,
                "confirmedEvidence must not be null"
        );

        validateBinding(
                coaching,
                confirmedEvidence
        );

        CvSourceIdResolver.ResolvedSource source;

        try {
            source = sourceIdResolver.resolve(
                    profile,
                    coaching.sourceId()
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The coached CV node changed. Analyze again before generating a rewrite."
            );
        }

        if (source.section() != coaching.section()
                || !Objects.equals(
                source.text(),
                coaching.original()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The coached CV node changed. Analyze again before generating a rewrite."
            );
        }

        CvEvidenceService.EvidenceMap baseEvidence =
                evidenceService.build(profile);

        EvidenceItem userEvidenceItem =
                toEvidenceItem(
                        confirmedEvidence
                );

        CvEvidenceService.EvidenceMap augmentedEvidence =
                appendEvidence(
                        baseEvidence,
                        userEvidenceItem
                );

        AllowedEvidence allowedEvidence =
                buildAllowedEvidence(
                        coaching,
                        source,
                        baseEvidence,
                        userEvidenceItem
                );

        RewriteRequest request =
                buildRequest(
                        job,
                        coaching,
                        source,
                        allowedEvidence
                );

        AtomicReference<GeneratedCoachingSuggestion> accepted =
                new AtomicReference<>();

        Optional<RewriteResponse> routed =
                providerRouter.generate(
                        request,
                        response -> {
                            GeneratedCoachingSuggestion generated =
                                    evaluateResponse(
                                            response,
                                            profile,
                                            job,
                                            coaching,
                                            confirmedEvidence,
                                            source,
                                            augmentedEvidence,
                                            allowedEvidence.allowedIds()
                                    );

                            if (generated == null) {
                                return false;
                            }

                            accepted.set(generated);
                            return true;
                        }
                );

        GeneratedCoachingSuggestion generated =
                accepted.get();

        if (generated != null) {
            return generated;
        }

        if (routed.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No CV rewrite provider is currently available"
            );
        }

        throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "AI could not produce a safe evidence-backed rewrite for this coaching answer"
        );
    }

    private void validateBinding(
            CoachingItem coaching,
            UserConfirmedEvidence evidence
    ) {
        if (!hasText(coaching.id())
                || !hasText(coaching.sourceId())
                || !hasText(coaching.original())
                || coaching.section() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The coaching item is no longer valid. Analyze again."
            );
        }

        if (!hasText(evidence.id())
                || !hasText(evidence.answerHash())
                || !hasText(evidence.text())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A non-empty user-confirmed answer is required before generating a rewrite"
            );
        }

        if (!Objects.equals(
                coaching.id(),
                evidence.coachingId()
        )
                || !Objects.equals(
                coaching.sourceId(),
                evidence.sourceId()
        )
                || coaching.section()
                != evidence.section()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The saved coaching evidence no longer matches this coaching item"
            );
        }
    }

    private AllowedEvidence buildAllowedEvidence(
            CoachingItem coaching,
            CvSourceIdResolver.ResolvedSource source,
            CvEvidenceService.EvidenceMap baseEvidence,
            EvidenceItem userEvidence
    ) {
        Map<String, EvidenceItem> byId =
                new LinkedHashMap<>();

        for (EvidenceItem item : baseEvidence.items()) {
            if (item != null
                    && hasText(item.id())) {
                byId.putIfAbsent(
                        item.id(),
                        item
                );
            }
        }

        LinkedHashSet<String> allowedIds =
                new LinkedHashSet<>();

        addIfAllowed(
                allowedIds,
                byId.get(source.sourceId()),
                source
        );

        for (String evidenceId : safeList(
                coaching.evidenceIds()
        )) {
            addIfAllowed(
                    allowedIds,
                    byId.get(evidenceId),
                    source
            );
        }

        allowedIds.add(
                userEvidence.id()
        );

        Map<String, EvidenceValue> catalog =
                new LinkedHashMap<>();

        for (String evidenceId : allowedIds) {
            EvidenceItem item =
                    evidenceId.equals(userEvidence.id())
                            ? userEvidence
                            : byId.get(evidenceId);

            if (item == null) {
                continue;
            }

            String kind = evidenceId.equals(
                    userEvidence.id()
            )
                    ? "USER_CONFIRMED_EVIDENCE"
                    : item.kind() == null
                    ? "TEXT"
                    : item.kind().name();

            catalog.put(
                    evidenceId,
                    new EvidenceValue(
                            evidenceId,
                            kind,
                            safeText(item.text()),
                            item.canonicalSkillKey()
                    )
            );
        }

        return new AllowedEvidence(
                List.copyOf(allowedIds),
                List.copyOf(catalog.values())
        );
    }

    private void addIfAllowed(
            Set<String> target,
            EvidenceItem item,
            CvSourceIdResolver.ResolvedSource source
    ) {
        if (item == null
                || !hasText(item.id())) {
            return;
        }

        if ((source.section() == Section.WORK_EXPERIENCE
                || source.section() == Section.PROJECT)
                && !Objects.equals(
                source.scopeId(),
                item.scopeId()
        )) {
            return;
        }

        target.add(item.id());
    }

    private RewriteRequest buildRequest(
            NormalizedJob job,
            CoachingItem coaching,
            CvSourceIdResolver.ResolvedSource source,
            AllowedEvidence allowedEvidence
    ) {
        JobContext jobContext =
                new JobContext(
                        job.getTitle(),
                        safeList(job.getSkills()),
                        job.getRequirementsText(),
                        job.getDescriptionText()
                );

        String context = String.join(
                "\n",
                "Interactive coaching rewrite for exactly one CV node.",
                "Coaching question: " + safeText(
                        coaching.question()
                ),
                "Why the question was asked: " + safeText(
                        coaching.reason()
                ),
                "Use USER_CONFIRMED_EVIDENCE only as factual evidence explicitly confirmed by the candidate.",
                "If the confirmed answer says a metric is unknown or unavailable, do not invent or infer that metric."
        );

        EditableNode node =
                new EditableNode(
                        source.sourceId(),
                        source.section().name(),
                        context,
                        source.text(),
                        allowedEvidence.allowedIds()
                );

        return new RewriteRequest(
                resolveUiLocale(),
                jobContext,
                List.of(node),
                allowedEvidence.catalog()
        );
    }

    private GeneratedCoachingSuggestion evaluateResponse(
            RewriteResponse response,
            CandidateProfile profile,
            NormalizedJob job,
            CoachingItem coaching,
            UserConfirmedEvidence confirmedEvidence,
            CvSourceIdResolver.ResolvedSource source,
            CvEvidenceService.EvidenceMap augmentedEvidence,
            List<String> allowedEvidenceIds
    ) {
        if (response == null) {
            return null;
        }

        Set<String> allowed =
                new LinkedHashSet<>(
                        safeList(allowedEvidenceIds)
                );

        for (RewriteSuggestion raw : safeList(
                response.suggestions()
        )) {
            if (raw == null
                    || !Objects.equals(
                    source.sourceId(),
                    raw.sourceId()
            )
                    || !hasText(raw.suggested())) {
                continue;
            }

            String suggested = raw.suggested().trim();

            if (suggested.equals(source.text())) {
                continue;
            }

            LinkedHashSet<String> evidenceIds =
                    new LinkedHashSet<>();

            for (String evidenceId : safeList(
                    raw.evidenceIds()
            )) {
                if (hasText(evidenceId)
                        && allowed.contains(
                        evidenceId
                )) {
                    evidenceIds.add(evidenceId);
                }
            }

            /*
             * Provenance is mandatory for an interactive coaching rewrite.
             * Always cite the original node and the exact user-confirmed
             * evidence revision, even when the provider omitted either ID.
             */
            evidenceIds.add(
                    source.sourceId()
            );
            evidenceIds.add(
                    confirmedEvidence.id()
            );

            SuggestionItem suggestion =
                    new SuggestionItem(
                            generatedSuggestionId(
                                    coaching.id(),
                                    confirmedEvidence.answerHash()
                            ),
                            SuggestionType.REWRITE,
                            source.section()
                                    == Section.PROFESSIONAL_SUMMARY
                                    ? SuggestionCategory.POSITION
                                    : SuggestionCategory.REWRITE,
                            coaching.priority(),
                            source.section(),
                            source.sourceId(),
                            source.text(),
                            suggested,
                            coaching.reason(),
                            coaching.reason(),
                            /*
                             * Keep targetSkills empty here. The rewritten text
                             * still passes SafetyGuard against the augmented
                             * evidence corpus, while we avoid treating model
                             * metadata as candidate truth.
                             */
                            List.of(),
                            List.copyOf(evidenceIds)
                    );

            if (!safetyGuard.isSafe(
                    suggestion,
                    augmentedEvidence,
                    job
            )) {
                continue;
            }

            try {
                List<SuggestionItem> validated =
                        suggestionValidator.validateAll(
                                profile,
                                augmentedEvidence,
                                List.of(suggestion)
                        );

                if (validated.isEmpty()) {
                    continue;
                }

                return new GeneratedCoachingSuggestion(
                        coaching.id(),
                        confirmedEvidence.answerHash(),
                        confirmedEvidence.id(),
                        validated.getFirst(),
                        Instant.now(clock)
                );
            } catch (IllegalArgumentException exception) {
                // Try the next provider suggestion, if any.
            }
        }

        return null;
    }

    private CvEvidenceService.EvidenceMap appendEvidence(
            CvEvidenceService.EvidenceMap base,
            EvidenceItem extra
    ) {
        List<EvidenceItem> items =
                new ArrayList<>(
                        base.items()
                );

        items.removeIf(
                item -> item != null
                        && Objects.equals(
                        item.id(),
                        extra.id()
                )
        );

        items.add(extra);

        return new CvEvidenceService.EvidenceMap(
                items
        );
    }

    private EvidenceItem toEvidenceItem(
            UserConfirmedEvidence evidence
    ) {
        return new EvidenceItem(
                evidence.id(),
                evidence.section(),
                evidence.scopeId(),
                EvidenceKind.TEXT,
                evidence.text(),
                null
        );
    }

    private String generatedSuggestionId(
            String coachingId,
            String answerHash
    ) {
        String seed = safeText(coachingId)
                + "\n"
                + safeText(answerHash);

        return "coaching-rewrite-"
                + sha256(seed).substring(
                0,
                24
        );
    }

    private String sha256(
            String value
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash = digest.digest(
                    value.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            StringBuilder builder =
                    new StringBuilder(
                            hash.length * 2
                    );

            for (byte item : hash) {
                builder.append(
                        String.format(
                                "%02x",
                                item & 0xff
                        )
                );
            }

            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }

    private String resolveUiLocale() {
        Locale locale =
                LocaleContextHolder.getLocale();

        return locale != null
                && locale.getLanguage()
                .toLowerCase(Locale.ROOT)
                .startsWith("vi")
                ? "vi"
                : "en";
    }

    private <T> List<T> safeList(
            List<T> values
    ) {
        return values == null
                ? List.of()
                : values;
    }

    private String safeText(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    private record AllowedEvidence(
            List<String> allowedIds,
            List<EvidenceValue> catalog
    ) {
    }
}