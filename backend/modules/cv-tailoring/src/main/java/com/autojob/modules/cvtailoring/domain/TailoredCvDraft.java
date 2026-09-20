package com.autojob.modules.cvtailoring.domain;

import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.CoachingItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.CurrentMatch;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.GapItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.JobSnapshot;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "tailored_cv_drafts")
@CompoundIndex(
        name = "tailored_cv_draft_owner_candidate_job_updated_idx",
        def = "{'ownerUserId': 1, 'candidateProfileId': 1, 'normalizedJobId': 1, 'status': 1, 'updatedAt': -1}"
)
public record TailoredCvDraft(
        @Id
        String id,
        String ownerUserId,
        String candidateProfileId,
        String normalizedJobId,
        Status status,
        String analysisId,
        Instant analysisExpiresAt,
        Instant baseProfileUpdatedAt,
        String baseParserVersion,
        String baseSourceSha256,
        String jobRawContentHash,
        Instant jobNormalizedAt,
        String rankingVersion,
        JobSnapshot job,
        CurrentMatch baselineMatch,
        List<EvidenceItem> evidence,
        List<SuggestionItem> suggestions,
        List<CoachingItem> coaching,
        List<GapItem> gaps,
        List<UserConfirmedEvidence> userConfirmedEvidence,
        List<GeneratedCoachingSuggestion> generatedCoachingSuggestions,
        List<String> acceptedSuggestionIds,
        List<String> rejectedSuggestionIds,
        List<CoachingAnswer> coachingAnswers,
        Instant createdAt,
        Instant updatedAt,
        @Version
        Long version
) {

    public TailoredCvDraft {
        status = status == null
                ? Status.DRAFT
                : status;

        evidence = copy(evidence);
        suggestions = copy(suggestions);
        coaching = copy(coaching);
        gaps = copy(gaps);
        userConfirmedEvidence = copy(userConfirmedEvidence);
        generatedCoachingSuggestions = copy(generatedCoachingSuggestions);
        acceptedSuggestionIds = copy(acceptedSuggestionIds);
        rejectedSuggestionIds = copy(rejectedSuggestionIds);
        coachingAnswers = copy(coachingAnswers);
    }

    private static <T> List<T> copy(
            List<T> values
    ) {
        return values == null
                ? List.of()
                : List.copyOf(values);
    }

    public enum Status {
        DRAFT,
        READY,
        ARCHIVED
    }

    public record CoachingAnswer(
            String coachingId,
            String answer,
            Instant updatedAt
    ) {
    }

    /**
     * Evidence explicitly supplied and confirmed by the candidate in response
     * to one coaching question. It is persisted separately from the original
     * immutable CandidateProfile so provenance stays auditable.
     */
    public record UserConfirmedEvidence(
            String id,
            String coachingId,
            String answerHash,
            Section section,
            String sourceId,
            String scopeId,
            String coachingQuestion,
            String text,
            Instant confirmedAt
    ) {
    }

    /**
     * A rewrite generated from one UserConfirmedEvidence item.
     *
     * answerHash binds the generated rewrite to the exact answer revision that
     * produced it. If the user changes the answer, WorkspaceService removes the
     * stale generated suggestion before it can be previewed again.
     */
    public record GeneratedCoachingSuggestion(
            String coachingId,
            String answerHash,
            String userEvidenceId,
            SuggestionItem suggestion,
            Instant generatedAt
    ) {
    }
}