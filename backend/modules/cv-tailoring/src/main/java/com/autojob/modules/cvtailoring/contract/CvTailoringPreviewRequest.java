package com.autojob.modules.cvtailoring.contract;

import java.util.List;

public record CvTailoringPreviewRequest(
        String analysisId,
        List<String> acceptedSuggestionIds
) {

    public CvTailoringPreviewRequest {

        acceptedSuggestionIds =
                acceptedSuggestionIds == null
                        ? List.of()
                        : List.copyOf(
                        acceptedSuggestionIds
                );
    }
}