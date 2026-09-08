package com.autojob.modules.cvtailoring.contract;

import java.util.List;

public record CvTailoringPreviewResponse(
        String analysisId,
        String candidateProfileId,
        String normalizedJobId,
        String rankingVersion,
        String temporaryEmbeddingVersion,
        List<String> appliedSuggestionIds,
        ScoreSnapshot before,
        ScoreSnapshot after,
        int retrievedCount,
        int hydratedCount
) {

    public CvTailoringPreviewResponse {

        appliedSuggestionIds =
                appliedSuggestionIds == null
                        ? List.of()
                        : List.copyOf(
                        appliedSuggestionIds
                );
    }

    public record ScoreSnapshot(
            PreviewStatus status,
            Integer rank,
            Double finalScore,
            Double semanticScore,
            Double skillScore,
            Double seniorityScore,
            Double locationScore,
            Double freshnessScore,
            Boolean skillKnown,
            Boolean seniorityKnown,
            Boolean locationKnown,
            Boolean freshnessKnown,
            List<String> matchedSkills,
            List<String> missingSkills,
            String reason
    ) {

        public ScoreSnapshot {

            matchedSkills =
                    matchedSkills == null
                            ? List.of()
                            : List.copyOf(
                            matchedSkills
                    );

            missingSkills =
                    missingSkills == null
                            ? List.of()
                            : List.copyOf(
                            missingSkills
                    );
        }
    }

    public enum PreviewStatus {

        MATCHED,

        NOT_RETRIEVED,

        NOT_MATCHED
    }
}