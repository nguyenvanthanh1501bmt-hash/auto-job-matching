package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.repository.CandidateProfileRepository;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.CoachingItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringDraftPreviewResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringDraftResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringDraftResponse.CoachingAnswerResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringDraftResponse.GeneratedCoachingSuggestionResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringDraftUpdateRequest;
import com.autojob.modules.cvtailoring.contract.CvTailoringDraftUpdateRequest.CoachingAnswerInput;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewRequest;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewResponse;
import com.autojob.modules.cvtailoring.domain.TailoredCvDraft;
import com.autojob.modules.cvtailoring.domain.TailoredCvDraft.CoachingAnswer;
import com.autojob.modules.cvtailoring.domain.TailoredCvDraft.GeneratedCoachingSuggestion;
import com.autojob.modules.cvtailoring.domain.TailoredCvDraft.UserConfirmedEvidence;
import com.autojob.modules.cvtailoring.repository.TailoredCvDraftRepository;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CvTailoringWorkspaceService {

    private static final int MAX_COACHING_ANSWER_LENGTH = 4_000;

    private final CvTailoringAnalysisService analysisService;
    private final CvTailoringPreviewService previewService;
    private final CvInteractiveCoachingService interactiveCoachingService;
    private final CvTailoringAnalysisStore analysisStore;
    private final CvSourceIdResolver sourceIdResolver;
    private final TailoredCvDraftRepository draftRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final NormalizedJobRepository normalizedJobRepository;
    private final Clock clock;

    public CvTailoringWorkspaceService(
            CvTailoringAnalysisService analysisService,
            CvTailoringPreviewService previewService,
            CvInteractiveCoachingService interactiveCoachingService,
            CvTailoringAnalysisStore analysisStore,
            CvSourceIdResolver sourceIdResolver,
            TailoredCvDraftRepository draftRepository,
            CandidateProfileRepository candidateProfileRepository,
            NormalizedJobRepository normalizedJobRepository,
            Clock clock
    ) {
        this.analysisService = Objects.requireNonNull(
                analysisService,
                "analysisService must not be null"
        );
        this.previewService = Objects.requireNonNull(
                previewService,
                "previewService must not be null"
        );
        this.interactiveCoachingService = Objects.requireNonNull(
                interactiveCoachingService,
                "interactiveCoachingService must not be null"
        );
        this.analysisStore = Objects.requireNonNull(
                analysisStore,
                "analysisStore must not be null"
        );
        this.sourceIdResolver = Objects.requireNonNull(
                sourceIdResolver,
                "sourceIdResolver must not be null"
        );
        this.draftRepository = Objects.requireNonNull(
                draftRepository,
                "draftRepository must not be null"
        );
        this.candidateProfileRepository = Objects.requireNonNull(
                candidateProfileRepository,
                "candidateProfileRepository must not be null"
        );
        this.normalizedJobRepository = Objects.requireNonNull(
                normalizedJobRepository,
                "normalizedJobRepository must not be null"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
    }

    public CvTailoringDraftResponse startOrRefresh(
            String candidateProfileId,
            String normalizedJobId,
            String ownerUserId
    ) {
        requireText(candidateProfileId, "candidateProfileId");
        requireText(normalizedJobId, "normalizedJobId");
        requireText(ownerUserId, "ownerUserId");

        CvTailoringAnalyzeResponse analysis = analysisService.analyze(
                candidateProfileId,
                normalizedJobId,
                ownerUserId
        );

        CandidateProfile profile = loadOwnedProfile(
                candidateProfileId,
                ownerUserId
        );

        NormalizedJob job = normalizedJobRepository
                .findById(normalizedJobId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Selected job is no longer available"
                ));

        TailoredCvDraft existing = draftRepository
                .findFirstByOwnerUserIdAndCandidateProfileIdAndNormalizedJobIdAndStatusOrderByUpdatedAtDesc(
                        ownerUserId,
                        candidateProfileId,
                        normalizedJobId,
                        TailoredCvDraft.Status.DRAFT
                )
                .orElse(null);

        PreservedState preserved = preserveState(
                existing,
                analysis,
                profile,
                job
        );
        Instant now = Instant.now(clock);

        TailoredCvDraft draft = new TailoredCvDraft(
                existing == null
                        ? UUID.randomUUID().toString()
                        : existing.id(),
                ownerUserId,
                candidateProfileId,
                normalizedJobId,
                TailoredCvDraft.Status.DRAFT,
                analysis.analysisId(),
                analysis.expiresAt(),
                profile.getUpdatedAt(),
                profile.getParserVersion(),
                profile.getSourceSha256(),
                job.getRawContentHash(),
                job.getNormalizedAt(),
                analysis.currentMatch().rankingVersion(),
                analysis.job(),
                analysis.currentMatch(),
                analysis.evidence(),
                analysis.suggestions(),
                analysis.coaching(),
                analysis.gaps(),
                preserved.userConfirmedEvidence(),
                preserved.generatedCoachingSuggestions(),
                preserved.acceptedSuggestionIds(),
                preserved.rejectedSuggestionIds(),
                preserved.coachingAnswers(),
                existing == null ? now : existing.createdAt(),
                now,
                existing == null ? null : existing.version()
        );

        return toResponse(draftRepository.save(draft));
    }

    public CvTailoringDraftResponse getCurrent(
            String candidateProfileId,
            String normalizedJobId,
            String ownerUserId
    ) {
        requireText(candidateProfileId, "candidateProfileId");
        requireText(normalizedJobId, "normalizedJobId");
        requireText(ownerUserId, "ownerUserId");

        TailoredCvDraft draft = draftRepository
                .findFirstByOwnerUserIdAndCandidateProfileIdAndNormalizedJobIdAndStatusOrderByUpdatedAtDesc(
                        ownerUserId,
                        candidateProfileId,
                        normalizedJobId,
                        TailoredCvDraft.Status.DRAFT
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "CV tailoring draft was not found"
                ));

        return toResponse(draft);
    }

    public CvTailoringDraftResponse getById(
            String draftId,
            String ownerUserId
    ) {
        return toResponse(requireOwnedDraft(draftId, ownerUserId));
    }

    public CvTailoringDraftResponse update(
            String draftId,
            String ownerUserId,
            CvTailoringDraftUpdateRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null");

        TailoredCvDraft draft = requireOwnedDraft(
                draftId,
                ownerUserId
        );

        if (draft.status() != TailoredCvDraft.Status.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an active CV tailoring draft can be edited"
            );
        }

        List<SuggestionItem> availableBeforeUpdate =
                availableSuggestions(draft);

        List<String> accepted = validateSuggestionSelection(
                "acceptedSuggestionIds",
                request.acceptedSuggestionIds(),
                availableBeforeUpdate
        );

        List<String> rejected = validateSuggestionSelection(
                "rejectedSuggestionIds",
                request.rejectedSuggestionIds(),
                availableBeforeUpdate
        );

        List<CoachingAnswer> answers = validateCoachingAnswers(
                request.coachingAnswers(),
                draft.coaching()
        );

        CandidateProfile profile = loadOwnedProfile(
                draft.candidateProfileId(),
                ownerUserId
        );

        List<UserConfirmedEvidence> confirmedEvidence =
                buildUserConfirmedEvidence(
                        draft,
                        answers,
                        profile
                );

        Map<String, UserConfirmedEvidence> evidenceById =
                confirmedEvidence
                        .stream()
                        .collect(Collectors.toMap(
                                UserConfirmedEvidence::id,
                                Function.identity(),
                                (left, right) -> left
                        ));

        List<GeneratedCoachingSuggestion> retainedGenerated =
                draft.generatedCoachingSuggestions()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(generated -> {
                            UserConfirmedEvidence evidence =
                                    evidenceById.get(
                                            generated.userEvidenceId()
                                    );

                            return evidence != null
                                    && Objects.equals(
                                    evidence.coachingId(),
                                    generated.coachingId()
                            )
                                    && Objects.equals(
                                    evidence.answerHash(),
                                    generated.answerHash()
                            );
                        })
                        .toList();

        List<SuggestionItem> availableAfterUpdate =
                combineSuggestions(
                        draft.suggestions(),
                        retainedGenerated
                                .stream()
                                .map(GeneratedCoachingSuggestion::suggestion)
                                .toList()
                );

        Set<String> availableIds = availableAfterUpdate
                .stream()
                .map(SuggestionItem::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        accepted = accepted
                .stream()
                .filter(availableIds::contains)
                .toList();

        rejected = rejected
                .stream()
                .filter(availableIds::contains)
                .toList();

        validateSelectionState(
                accepted,
                rejected,
                availableAfterUpdate
        );

        TailoredCvDraft updated = new TailoredCvDraft(
                draft.id(),
                draft.ownerUserId(),
                draft.candidateProfileId(),
                draft.normalizedJobId(),
                draft.status(),
                draft.analysisId(),
                draft.analysisExpiresAt(),
                draft.baseProfileUpdatedAt(),
                draft.baseParserVersion(),
                draft.baseSourceSha256(),
                draft.jobRawContentHash(),
                draft.jobNormalizedAt(),
                draft.rankingVersion(),
                draft.job(),
                draft.baselineMatch(),
                draft.evidence(),
                draft.suggestions(),
                draft.coaching(),
                draft.gaps(),
                confirmedEvidence,
                retainedGenerated,
                accepted,
                rejected,
                answers,
                draft.createdAt(),
                Instant.now(clock),
                draft.version()
        );

        return toResponse(draftRepository.save(updated));
    }

    public CvTailoringDraftResponse generateCoachingSuggestion(
            String draftId,
            String coachingId,
            String ownerUserId
    ) {
        requireText(coachingId, "coachingId");

        String requestedCoachingId =
                coachingId.trim();

        TailoredCvDraft requestedDraft =
                requireOwnedDraft(
                        draftId,
                        ownerUserId
                );

        TailoredCvDraft draft = refreshIfExpired(
                requestedDraft,
                ownerUserId
        );

        String liveCoachingId =
                resolveCompatibleCoachingId(
                        requestedDraft,
                        requestedCoachingId,
                        draft
                );

        try {
            return generateCoachingSuggestionAgainstLiveAnalysis(
                    draft,
                    liveCoachingId,
                    ownerUserId
            );
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value()
                    != HttpStatus.NOT_FOUND.value()) {
                throw exception;
            }

            CvTailoringDraftResponse refreshed = startOrRefresh(
                    draft.candidateProfileId(),
                    draft.normalizedJobId(),
                    ownerUserId
            );

            TailoredCvDraft refreshedDraft = requireOwnedDraft(
                    refreshed.draftId(),
                    ownerUserId
            );

            String refreshedCoachingId =
                    resolveCompatibleCoachingId(
                            draft,
                            liveCoachingId,
                            refreshedDraft
                    );

            return generateCoachingSuggestionAgainstLiveAnalysis(
                    refreshedDraft,
                    refreshedCoachingId,
                    ownerUserId
            );
        }
    }


    private String resolveCompatibleCoachingId(
            TailoredCvDraft previousDraft,
            String previousCoachingId,
            TailoredCvDraft currentDraft
    ) {
        CoachingItem previous = previousDraft
                .coaching()
                .stream()
                .filter(Objects::nonNull)
                .filter(item -> Objects.equals(
                        previousCoachingId,
                        item.id()
                ))
                .findFirst()
                .orElse(null);

        if (previous == null) {
            return previousCoachingId;
        }

        return currentDraft
                .coaching()
                .stream()
                .filter(Objects::nonNull)
                .filter(item -> hasText(item.id()))
                .filter(item -> sameCoaching(
                        previous,
                        item
                ))
                .map(CoachingItem::id)
                .findFirst()
                .orElse(previousCoachingId);
    }

    private CvTailoringDraftResponse
    generateCoachingSuggestionAgainstLiveAnalysis(
            TailoredCvDraft draft,
            String coachingId,
            String ownerUserId
    ) {
        CoachingItem coaching = draft.coaching()
                .stream()
                .filter(item -> item != null
                        && coachingId.equals(
                        item.id()
                ))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The coaching item is no longer compatible with the current analysis"
                ));

        CoachingAnswer answer = draft.coachingAnswers()
                .stream()
                .filter(item -> item != null
                        && coachingId.equals(
                        item.coachingId()
                ))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Save a non-empty coaching answer before generating a rewrite"
                ));

        UserConfirmedEvidence confirmedEvidence =
                draft.userConfirmedEvidence()
                        .stream()
                        .filter(item -> item != null
                                && coachingId.equals(
                                item.coachingId()
                        ))
                        .filter(item -> Objects.equals(
                                item.answerHash(),
                                answerHash(
                                        answer.answer()
                                )
                        ))
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "The saved coaching evidence is stale. Save the draft again before generating."
                        ));

        Optional<GeneratedCoachingSuggestion> existing =
                draft.generatedCoachingSuggestions()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(item -> coachingId.equals(
                                item.coachingId()
                        ))
                        .filter(item -> Objects.equals(
                                item.answerHash(),
                                confirmedEvidence.answerHash()
                        ))
                        .filter(item -> Objects.equals(
                                item.userEvidenceId(),
                                confirmedEvidence.id()
                        ))
                        .findFirst();

        if (existing.isPresent()) {
            return toResponse(draft);
        }

        CandidateProfile profile = loadOwnedProfile(
                draft.candidateProfileId(),
                ownerUserId
        );

        NormalizedJob job = normalizedJobRepository
                .findById(draft.normalizedJobId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Selected job is no longer available"
                ));

        CvTailoringAnalysisStore.AnalysisContext context =
                analysisStore.require(
                        draft.analysisId(),
                        ownerUserId,
                        draft.candidateProfileId(),
                        draft.normalizedJobId()
                );

        analysisStore.assertProfileUnchanged(
                context,
                profile
        );

        analysisStore.assertJobUnchanged(
                context,
                job
        );

        GeneratedCoachingSuggestion generated =
                interactiveCoachingService.generate(
                        profile,
                        job,
                        coaching,
                        confirmedEvidence
                );

        List<GeneratedCoachingSuggestion> nextGenerated =
                new ArrayList<>();

        for (GeneratedCoachingSuggestion item :
                draft.generatedCoachingSuggestions()) {
            if (item != null
                    && !coachingId.equals(
                    item.coachingId()
            )) {
                nextGenerated.add(item);
            }
        }

        nextGenerated.add(generated);

        Set<String> validIds = combineSuggestions(
                draft.suggestions(),
                nextGenerated
                        .stream()
                        .map(GeneratedCoachingSuggestion::suggestion)
                        .toList()
        )
                .stream()
                .map(SuggestionItem::id)
                .collect(Collectors.toSet());

        List<String> accepted = draft.acceptedSuggestionIds()
                .stream()
                .filter(validIds::contains)
                .toList();

        List<String> rejected = draft.rejectedSuggestionIds()
                .stream()
                .filter(validIds::contains)
                .toList();

        TailoredCvDraft updated = new TailoredCvDraft(
                draft.id(),
                draft.ownerUserId(),
                draft.candidateProfileId(),
                draft.normalizedJobId(),
                draft.status(),
                draft.analysisId(),
                draft.analysisExpiresAt(),
                draft.baseProfileUpdatedAt(),
                draft.baseParserVersion(),
                draft.baseSourceSha256(),
                draft.jobRawContentHash(),
                draft.jobNormalizedAt(),
                draft.rankingVersion(),
                draft.job(),
                draft.baselineMatch(),
                draft.evidence(),
                draft.suggestions(),
                draft.coaching(),
                draft.gaps(),
                draft.userConfirmedEvidence(),
                List.copyOf(nextGenerated),
                accepted,
                rejected,
                draft.coachingAnswers(),
                draft.createdAt(),
                Instant.now(clock),
                draft.version()
        );

        return toResponse(
                draftRepository.save(updated)
        );
    }

    public CvTailoringDraftPreviewResponse preview(
            String draftId,
            String ownerUserId
    ) {
        TailoredCvDraft draft = refreshIfExpired(
                requireOwnedDraft(draftId, ownerUserId),
                ownerUserId
        );

        try {
            return new CvTailoringDraftPreviewResponse(
                    toResponse(draft),
                    runPreview(draft, ownerUserId)
            );
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value()
                    != HttpStatus.NOT_FOUND.value()) {
                throw exception;
            }

            /*
             * Analysis sessions intentionally live in memory. If the backend
             * restarted, Mongo still has the draft but the analysisId is gone.
             * Re-analyze once, preserve decisions that still point to identical
             * suggestions, then retry preview with the new authoritative session.
             */
            CvTailoringDraftResponse refreshed = startOrRefresh(
                    draft.candidateProfileId(),
                    draft.normalizedJobId(),
                    ownerUserId
            );

            TailoredCvDraft refreshedDraft = requireOwnedDraft(
                    refreshed.draftId(),
                    ownerUserId
            );

            return new CvTailoringDraftPreviewResponse(
                    toResponse(refreshedDraft),
                    runPreview(refreshedDraft, ownerUserId)
            );
        }
    }

    private CvTailoringPreviewResponse runPreview(
            TailoredCvDraft draft,
            String ownerUserId
    ) {
        syncAnalysisExtensions(
                draft,
                ownerUserId
        );

        return previewService.preview(
                draft.candidateProfileId(),
                draft.normalizedJobId(),
                ownerUserId,
                new CvTailoringPreviewRequest(
                        draft.analysisId(),
                        draft.acceptedSuggestionIds()
                )
        );
    }

    private TailoredCvDraft refreshIfExpired(
            TailoredCvDraft draft,
            String ownerUserId
    ) {
        Instant expiresAt = draft.analysisExpiresAt();

        if (expiresAt != null && expiresAt.isAfter(Instant.now(clock))) {
            return draft;
        }

        CvTailoringDraftResponse refreshed = startOrRefresh(
                draft.candidateProfileId(),
                draft.normalizedJobId(),
                ownerUserId
        );

        return requireOwnedDraft(refreshed.draftId(), ownerUserId);
    }

    private CandidateProfile loadOwnedProfile(
            String candidateProfileId,
            String ownerUserId
    ) {
        CandidateProfile profile = candidateProfileRepository
                .findById(candidateProfileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Candidate profile was not found"
                ));

        if (!ownerUserId.equals(profile.getOwnerUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Candidate profile was not found"
            );
        }

        return profile;
    }

    private TailoredCvDraft requireOwnedDraft(
            String draftId,
            String ownerUserId
    ) {
        requireText(draftId, "draftId");
        requireText(ownerUserId, "ownerUserId");

        return draftRepository
                .findByIdAndOwnerUserId(draftId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "CV tailoring draft was not found"
                ));
    }

    private PreservedState preserveState(
            TailoredCvDraft existing,
            CvTailoringAnalyzeResponse analysis,
            CandidateProfile profile,
            NormalizedJob job
    ) {
        if (existing == null) {
            return new PreservedState(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        Map<String, SuggestionItem> previousSuggestions = bySuggestionId(
                existing.suggestions()
        );
        Map<String, SuggestionItem> currentSuggestions = bySuggestionId(
                analysis.suggestions()
        );

        Set<String> stableSuggestionIds = currentSuggestions
                .entrySet()
                .stream()
                .filter(entry -> {
                    SuggestionItem previous = previousSuggestions.get(
                            entry.getKey()
                    );
                    return previous != null
                            && sameSuggestion(previous, entry.getValue());
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, CoachingItem> previousCoaching = byCoachingId(
                existing.coaching()
        );
        Map<String, CoachingItem> currentCoaching = byCoachingId(
                analysis.coaching()
        );

        Set<String> compatibleCoachingIds = currentCoaching
                .entrySet()
                .stream()
                .filter(entry -> {
                    CoachingItem previous = previousCoaching.get(
                            entry.getKey()
                    );
                    return previous != null
                            && sameCoaching(previous, entry.getValue());
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<CoachingAnswer> answers = preserveCoachingAnswers(
                existing.coachingAnswers(),
                existing.coaching(),
                analysis.coaching()
        );

        List<UserConfirmedEvidence> confirmedEvidence =
                buildUserConfirmedEvidence(
                        answers,
                        analysis.coaching(),
                        profile,
                        existing.userConfirmedEvidence()
                );

        Map<String, UserConfirmedEvidence> evidenceById =
                confirmedEvidence
                        .stream()
                        .collect(Collectors.toMap(
                                UserConfirmedEvidence::id,
                                Function.identity(),
                                (left, right) -> left
                        ));

        boolean sameBaseRevision =
                Objects.equals(
                        existing.baseProfileUpdatedAt(),
                        profile.getUpdatedAt()
                )
                        && Objects.equals(
                        existing.baseParserVersion(),
                        profile.getParserVersion()
                )
                        && Objects.equals(
                        existing.baseSourceSha256(),
                        profile.getSourceSha256()
                )
                        && Objects.equals(
                        existing.jobRawContentHash(),
                        job.getRawContentHash()
                )
                        && Objects.equals(
                        existing.jobNormalizedAt(),
                        job.getNormalizedAt()
                );

        List<GeneratedCoachingSuggestion> generated =
                sameBaseRevision
                        ? existing.generatedCoachingSuggestions()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(item -> compatibleCoachingIds.contains(
                                item.coachingId()
                        ))
                        .filter(item -> {
                            UserConfirmedEvidence evidence =
                                    evidenceById.get(
                                            item.userEvidenceId()
                                    );

                            CoachingItem coaching =
                                    currentCoaching.get(
                                            item.coachingId()
                                    );

                            SuggestionItem suggestion =
                                    item.suggestion();

                            return evidence != null
                                    && coaching != null
                                    && suggestion != null
                                    && Objects.equals(
                                    evidence.answerHash(),
                                    item.answerHash()
                            )
                                    && Objects.equals(
                                    suggestion.sourceId(),
                                    coaching.sourceId()
                            )
                                    && Objects.equals(
                                    suggestion.original(),
                                    coaching.original()
                            )
                                    && suggestion.section()
                                    == coaching.section();
                        })
                        .toList()
                        : List.of();

        Set<String> preservableIds =
                new LinkedHashSet<>(
                        stableSuggestionIds
                );

        for (GeneratedCoachingSuggestion item : generated) {
            if (item.suggestion() != null
                    && hasText(
                    item.suggestion().id()
            )) {
                preservableIds.add(
                        item.suggestion().id()
                );
            }
        }

        List<String> accepted = existing.acceptedSuggestionIds()
                .stream()
                .filter(preservableIds::contains)
                .distinct()
                .toList();

        Set<String> acceptedSet =
                new HashSet<>(accepted);

        List<String> rejected = existing.rejectedSuggestionIds()
                .stream()
                .filter(preservableIds::contains)
                .filter(id -> !acceptedSet.contains(id))
                .distinct()
                .toList();

        validateSelectionState(
                accepted,
                rejected,
                combineSuggestions(
                        analysis.suggestions(),
                        generated
                                .stream()
                                .map(GeneratedCoachingSuggestion::suggestion)
                                .toList()
                )
        );

        return new PreservedState(
                confirmedEvidence,
                generated,
                accepted,
                rejected,
                answers
        );
    }

    private Map<String, SuggestionItem> bySuggestionId(
            List<SuggestionItem> suggestions
    ) {
        return suggestions.stream().collect(Collectors.toMap(
                SuggestionItem::id,
                Function.identity(),
                (left, right) -> left
        ));
    }

    private Map<String, CoachingItem> byCoachingId(
            List<CoachingItem> coaching
    ) {
        return coaching.stream().collect(Collectors.toMap(
                CoachingItem::id,
                Function.identity(),
                (left, right) -> left
        ));
    }

    private boolean sameSuggestion(
            SuggestionItem left,
            SuggestionItem right
    ) {
        return left.type() == right.type()
                && left.category() == right.category()
                && left.section() == right.section()
                && Objects.equals(left.sourceId(), right.sourceId())
                && Objects.equals(left.original(), right.original())
                && Objects.equals(left.suggested(), right.suggested());
    }

    private boolean sameCoaching(
            CoachingItem left,
            CoachingItem right
    ) {
        return left.type() == right.type()
                && left.section() == right.section()
                && Objects.equals(left.sourceId(), right.sourceId())
                && Objects.equals(left.question(), right.question())
                && Objects.equals(left.original(), right.original());
    }

    private List<CoachingAnswer> preserveCoachingAnswers(
            List<CoachingAnswer> previousAnswers,
            List<CoachingItem> previousCoaching,
            List<CoachingItem> currentCoaching
    ) {
        Map<String, CoachingItem> previousById =
                byCoachingId(
                        safeList(previousCoaching)
                );

        List<CoachingItem> current =
                new ArrayList<>(
                        safeList(currentCoaching)
                );

        Set<String> usedCurrentIds =
                new HashSet<>();

        List<CoachingAnswer> preserved =
                new ArrayList<>();

        for (CoachingAnswer answer : safeList(previousAnswers)) {
            if (answer == null
                    || !hasText(answer.coachingId())
                    || !hasText(answer.answer())) {
                continue;
            }

            CoachingItem previous =
                    previousById.get(
                            answer.coachingId()
                    );

            if (previous == null) {
                continue;
            }

            CoachingItem compatible = current
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(item -> hasText(item.id()))
                    .filter(item -> !usedCurrentIds.contains(
                            item.id()
                    ))
                    .filter(item -> sameCoaching(
                            previous,
                            item
                    ))
                    .findFirst()
                    .orElse(null);

            if (compatible == null) {
                continue;
            }

            usedCurrentIds.add(
                    compatible.id()
            );

            preserved.add(
                    new CoachingAnswer(
                            compatible.id(),
                            answer.answer(),
                            answer.updatedAt()
                    )
            );
        }

        return List.copyOf(preserved);
    }

    private List<SuggestionItem> availableSuggestions(
            TailoredCvDraft draft
    ) {
        return combineSuggestions(
                draft.suggestions(),
                draft.generatedCoachingSuggestions()
                        .stream()
                        .filter(Objects::nonNull)
                        .map(GeneratedCoachingSuggestion::suggestion)
                        .toList()
        );
    }

    private List<SuggestionItem> combineSuggestions(
            List<SuggestionItem> base,
            List<SuggestionItem> generated
    ) {
        List<SuggestionItem> result =
                new ArrayList<>();

        Set<String> seen =
                new HashSet<>();

        for (SuggestionItem item : safeList(base)) {
            if (item != null
                    && hasText(item.id())
                    && seen.add(item.id())) {
                result.add(item);
            }
        }

        for (SuggestionItem item : safeList(generated)) {
            if (item != null
                    && hasText(item.id())
                    && seen.add(item.id())) {
                result.add(item);
            }
        }

        return List.copyOf(result);
    }

    private void validateSelectionState(
            List<String> accepted,
            List<String> rejected,
            List<SuggestionItem> available
    ) {
        Set<String> overlap =
                new HashSet<>(accepted);
        overlap.retainAll(rejected);

        if (!overlap.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The same suggestion cannot be both accepted and rejected"
            );
        }

        Map<String, SuggestionItem> byId =
                bySuggestionId(available);

        Set<String> acceptedRewriteSources =
                new HashSet<>();

        for (String id : accepted) {
            SuggestionItem suggestion =
                    byId.get(id);

            if (suggestion == null
                    || suggestion.type()
                    != CvTailoringAnalyzeResponse.SuggestionType.REWRITE) {
                continue;
            }

            if (!hasText(suggestion.sourceId())) {
                continue;
            }

            if (!acceptedRewriteSources.add(
                    suggestion.sourceId()
            )) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Only one rewrite can be accepted for the same CV node"
                );
            }
        }
    }

    private List<String> validateSuggestionSelection(
            String fieldName,
            List<String> values,
            List<SuggestionItem> availableSuggestions
    ) {
        Set<String> availableIds = availableSuggestions
                .stream()
                .map(SuggestionItem::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        for (String value : values) {
            requireText(value, fieldName + " item");
            String id = value.trim();

            if (!availableIds.contains(id)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown suggestion id: " + id
                );
            }

            if (!normalized.add(id)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        fieldName + " must not contain duplicates"
                );
            }
        }

        return List.copyOf(normalized);
    }

    private List<CoachingAnswer> validateCoachingAnswers(
            List<CoachingAnswerInput> inputs,
            List<CoachingItem> availableCoaching
    ) {
        Map<String, CoachingItem> byId = byCoachingId(availableCoaching);
        Set<String> seen = new HashSet<>();
        Instant now = Instant.now(clock);
        List<CoachingAnswer> result = new ArrayList<>();

        for (CoachingAnswerInput input : inputs) {
            if (input == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "coachingAnswers must not contain null values"
                );
            }

            requireText(input.coachingId(), "coachingAnswers.coachingId");
            String coachingId = input.coachingId().trim();

            if (!seen.add(coachingId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "coachingAnswers must not contain duplicate coaching ids"
                );
            }

            if (!byId.containsKey(coachingId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown coaching id: " + coachingId
                );
            }

            String answer = normalizeAnswer(input.answer());

            if (answer != null) {
                result.add(
                        new CoachingAnswer(
                                coachingId,
                                answer,
                                now
                        )
                );
            }
        }

        return List.copyOf(result);
    }

    private List<UserConfirmedEvidence> buildUserConfirmedEvidence(
            TailoredCvDraft draft,
            List<CoachingAnswer> answers,
            CandidateProfile profile
    ) {
        return buildUserConfirmedEvidence(
                answers,
                draft.coaching(),
                profile,
                draft.userConfirmedEvidence()
        );
    }

    private List<UserConfirmedEvidence> buildUserConfirmedEvidence(
            List<CoachingAnswer> answers,
            List<CoachingItem> coachingItems,
            CandidateProfile profile,
            List<UserConfirmedEvidence> previousEvidence
    ) {
        Map<String, CoachingItem> coachingById =
                byCoachingId(coachingItems);

        Map<String, UserConfirmedEvidence> previousById =
                safeList(previousEvidence)
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(item -> hasText(item.id()))
                        .collect(Collectors.toMap(
                                UserConfirmedEvidence::id,
                                Function.identity(),
                                (left, right) -> left
                        ));

        List<UserConfirmedEvidence> result =
                new ArrayList<>();

        for (CoachingAnswer answer : safeList(answers)) {
            if (answer == null
                    || !hasText(answer.answer())) {
                continue;
            }

            CoachingItem coaching =
                    coachingById.get(
                            answer.coachingId()
                    );

            if (coaching == null
                    || !hasText(coaching.sourceId())) {
                continue;
            }

            CvSourceIdResolver.ResolvedSource source;

            try {
                source = sourceIdResolver.resolve(
                        profile,
                        coaching.sourceId()
                );
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The coached CV node changed. Analyze again before saving coaching evidence."
                );
            }

            if (source.section() != coaching.section()
                    || !Objects.equals(
                    source.text(),
                    coaching.original()
            )) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The coached CV node changed. Analyze again before saving coaching evidence."
                );
            }

            String answerHash =
                    answerHash(
                            answer.answer()
                    );

            String evidenceId =
                    userEvidenceId(
                            coaching.id(),
                            answerHash
                    );

            UserConfirmedEvidence previous =
                    previousById.get(
                            evidenceId
                    );

            result.add(
                    new UserConfirmedEvidence(
                            evidenceId,
                            coaching.id(),
                            answerHash,
                            coaching.section(),
                            coaching.sourceId(),
                            source.scopeId(),
                            coaching.question(),
                            answer.answer(),
                            previous == null
                                    ? Instant.now(clock)
                                    : previous.confirmedAt()
                    )
            );
        }

        return List.copyOf(result);
    }

    private String normalizeAnswer(
            String answer
    ) {
        if (answer == null || answer.isBlank()) {
            return null;
        }

        String normalized = answer.trim();

        if (normalized.length() > MAX_COACHING_ANSWER_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A coaching answer must not exceed "
                            + MAX_COACHING_ANSWER_LENGTH
                            + " characters"
            );
        }

        return normalized;
    }

    private void syncAnalysisExtensions(
            TailoredCvDraft draft,
            String ownerUserId
    ) {
        Set<String> referencedEvidenceIds =
                draft.generatedCoachingSuggestions()
                        .stream()
                        .filter(Objects::nonNull)
                        .map(GeneratedCoachingSuggestion::userEvidenceId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        List<EvidenceItem> workspaceEvidence =
                draft.userConfirmedEvidence()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(item -> referencedEvidenceIds.contains(
                                item.id()
                        ))
                        .map(item -> new EvidenceItem(
                                item.id(),
                                item.section(),
                                item.scopeId(),
                                EvidenceKind.TEXT,
                                item.text(),
                                null
                        ))
                        .toList();

        List<SuggestionItem> generatedSuggestions =
                draft.generatedCoachingSuggestions()
                        .stream()
                        .filter(Objects::nonNull)
                        .map(GeneratedCoachingSuggestion::suggestion)
                        .filter(Objects::nonNull)
                        .toList();

        analysisStore.replaceWorkspaceExtensions(
                draft.analysisId(),
                ownerUserId,
                draft.candidateProfileId(),
                draft.normalizedJobId(),
                workspaceEvidence,
                draft.suggestions(),
                generatedSuggestions
        );
    }

    private String userEvidenceId(
            String coachingId,
            String answerHash
    ) {
        return "user-confirmed-"
                + sha256(
                coachingId
                        + "\n"
                        + answerHash
        ).substring(0, 24);
    }

    private String answerHash(
            String answer
    ) {
        return sha256(
                answer == null
                        ? ""
                        : answer.trim()
        );
    }

    private String sha256(
            String value
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance(
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

    private <T> List<T> safeList(
            List<T> values
    ) {
        return values == null
                ? List.of()
                : values;
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    private CvTailoringDraftResponse toResponse(
            TailoredCvDraft draft
    ) {
        List<CoachingAnswerResponse> answers = draft
                .coachingAnswers()
                .stream()
                .map(answer -> new CoachingAnswerResponse(
                        answer.coachingId(),
                        answer.answer(),
                        answer.updatedAt()
                ))
                .toList();

        List<GeneratedCoachingSuggestionResponse> generated =
                draft.generatedCoachingSuggestions()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(item -> item.suggestion() != null)
                        .map(item -> new GeneratedCoachingSuggestionResponse(
                                item.coachingId(),
                                item.userEvidenceId(),
                                item.suggestion(),
                                item.generatedAt()
                        ))
                        .toList();

        return new CvTailoringDraftResponse(
                draft.id(),
                draft.candidateProfileId(),
                draft.normalizedJobId(),
                draft.status().name(),
                draft.analysisId(),
                draft.analysisExpiresAt(),
                draft.rankingVersion(),
                draft.job(),
                draft.baselineMatch(),
                draft.evidence(),
                draft.suggestions(),
                draft.coaching(),
                draft.gaps(),
                generated,
                draft.acceptedSuggestionIds(),
                draft.rejectedSuggestionIds(),
                answers,
                draft.createdAt(),
                draft.updatedAt()
        );
    }

    private void requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must not be blank"
            );
        }
    }

    private record PreservedState(
            List<UserConfirmedEvidence> userConfirmedEvidence,
            List<GeneratedCoachingSuggestion> generatedCoachingSuggestions,
            List<String> acceptedSuggestionIds,
            List<String> rejectedSuggestionIds,
            List<CoachingAnswer> coachingAnswers
    ) {
    }
}