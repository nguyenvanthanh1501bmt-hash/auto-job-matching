package com.autojob.modules.cvtailoring.contract;

import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.CoachingItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.CurrentMatch;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.GapItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.JobSnapshot;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;

import java.time.Instant;
import java.util.List;

public record CvTailoringDraftResponse(
        String draftId,
        String candidateProfileId,
        String normalizedJobId,
        String status,
        String analysisId,
        Instant analysisExpiresAt,
        String rankingVersion,
        JobSnapshot job,
        CurrentMatch baselineMatch,
        List<EvidenceItem> evidence,
        List<SuggestionItem> suggestions,
        List<CoachingItem> coaching,
        List<GapItem> gaps,
        List<String> acceptedSuggestionIds,
        List<String> rejectedSuggestionIds,
        List<CoachingAnswerResponse> coachingAnswers,
        Instant createdAt,
        Instant updatedAt
) {

    public CvTailoringDraftResponse {
        evidence = copy(evidence);
        suggestions = copy(suggestions);
        coaching = copy(coaching);
        gaps = copy(gaps);
        acceptedSuggestionIds = copy(
                acceptedSuggestionIds
        );
        rejectedSuggestionIds = copy(
                rejectedSuggestionIds
        );
        coachingAnswers = copy(
                coachingAnswers
        );
    }

    private static <T> List<T> copy(
            List<T> values
    ) {
        return values == null
                ? List.of()
                : List.copyOf(values);
    }

    public record CoachingAnswerResponse(
            String coachingId,
            String answer,
            Instant updatedAt
    ) {
    }
}