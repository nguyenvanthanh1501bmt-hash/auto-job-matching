package com.autojob.modules.cvtailoring.contract;

import java.util.List;

public record CvTailoringDraftUpdateRequest(
        List<String> acceptedSuggestionIds,
        List<String> rejectedSuggestionIds,
        List<CoachingAnswerInput> coachingAnswers
) {

    public CvTailoringDraftUpdateRequest {
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

    public record CoachingAnswerInput(
            String coachingId,
            String answer
    ) {
    }
}