package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.repository.CandidateProfileRepository;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.CoachingItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringDraftPreviewResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringDraftResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringDraftResponse.CoachingAnswerResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringDraftUpdateRequest;
import com.autojob.modules.cvtailoring.contract.CvTailoringDraftUpdateRequest.CoachingAnswerInput;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewRequest;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewResponse;
import com.autojob.modules.cvtailoring.domain.TailoredCvDraft;
import com.autojob.modules.cvtailoring.domain.TailoredCvDraft.CoachingAnswer;
import com.autojob.modules.cvtailoring.repository.TailoredCvDraftRepository;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CvTailoringWorkspaceService {

    private static final int MAX_COACHING_ANSWER_LENGTH = 4_000;

    private final CvTailoringAnalysisService analysisService;
    private final CvTailoringPreviewService previewService;
    private final TailoredCvDraftRepository draftRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final NormalizedJobRepository normalizedJobRepository;
    private final Clock clock;

    public CvTailoringWorkspaceService(
            CvTailoringAnalysisService analysisService,
            CvTailoringPreviewService previewService,
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

        PreservedState preserved = preserveState(existing, analysis);
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
                preserved.acceptedSuggestionIds(),
                preserved.rejectedSuggestionIds(),
                preserved.coachingAnswers(),
                existing == null ? now : existing.createdAt(),
                now
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

        List<String> accepted = validateSuggestionSelection(
                "acceptedSuggestionIds",
                request.acceptedSuggestionIds(),
                draft.suggestions()
        );

        List<String> rejected = validateSuggestionSelection(
                "rejectedSuggestionIds",
                request.rejectedSuggestionIds(),
                draft.suggestions()
        );

        Set<String> overlap = new HashSet<>(accepted);
        overlap.retainAll(rejected);

        if (!overlap.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The same suggestion cannot be both accepted and rejected"
            );
        }

        List<CoachingAnswer> answers = validateCoachingAnswers(
                request.coachingAnswers(),
                draft.coaching()
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
                accepted,
                rejected,
                answers,
                draft.createdAt(),
                Instant.now(clock)
        );

        return toResponse(draftRepository.save(updated));
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
            CvTailoringAnalyzeResponse analysis
    ) {
        if (existing == null) {
            return new PreservedState(
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

        List<String> accepted = existing.acceptedSuggestionIds()
                .stream()
                .filter(stableSuggestionIds::contains)
                .distinct()
                .toList();

        Set<String> acceptedSet = new HashSet<>(accepted);

        List<String> rejected = existing.rejectedSuggestionIds()
                .stream()
                .filter(stableSuggestionIds::contains)
                .filter(id -> !acceptedSet.contains(id))
                .distinct()
                .toList();

        Map<String, CoachingItem> previousCoaching = byCoachingId(
                existing.coaching()
        );
        Map<String, CoachingItem> currentCoaching = byCoachingId(
                analysis.coaching()
        );

        List<CoachingAnswer> answers = existing.coachingAnswers()
                .stream()
                .filter(answer -> {
                    CoachingItem previous = previousCoaching.get(
                            answer.coachingId()
                    );
                    CoachingItem current = currentCoaching.get(
                            answer.coachingId()
                    );
                    return previous != null
                            && current != null
                            && sameCoaching(previous, current);
                })
                .toList();

        return new PreservedState(accepted, rejected, answers);
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
            List<String> acceptedSuggestionIds,
            List<String> rejectedSuggestionIds,
            List<CoachingAnswer> coachingAnswers
    ) {
    }
}