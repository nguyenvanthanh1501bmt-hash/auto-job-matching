package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.CoachingItem;
import com.autojob.modules.matching.domain.MatchResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class CvCoachingService {

    private final CvSuggestionService suggestionService;

    public CvCoachingService(
            CvSuggestionService suggestionService
    ) {
        this.suggestionService =
                Objects.requireNonNull(
                        suggestionService,
                        "suggestionService must not be null"
                );
    }

    public List<CoachingItem> buildNeedsInputItems(
            CandidateProfile profile,
            MatchResult targetMatch,
            CvEvidenceService.EvidenceMap evidenceMap
    ) {
        Objects.requireNonNull(
                profile,
                "profile must not be null"
        );

        Objects.requireNonNull(
                targetMatch,
                "targetMatch must not be null"
        );

        Objects.requireNonNull(
                evidenceMap,
                "evidenceMap must not be null"
        );

        return suggestionService
                .getCachedCoaching(
                        profile,
                        targetMatch
                );
    }
}