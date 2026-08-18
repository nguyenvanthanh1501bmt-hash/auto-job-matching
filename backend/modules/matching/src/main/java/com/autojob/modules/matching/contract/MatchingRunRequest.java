package com.autojob.modules.matching.contract;

/**
 * Input contract cho một hybrid matching run.
 *
 * force = false:
 * - nếu đúng candidateEmbedding + rankingVersion đã có result
 *   thì reuse result cũ.
 *
 * force = true:
 * - chạy lại Qdrant + hybrid ranking
 * - replace result của matching run đó.
 */
public record MatchingRunRequest(
        String candidateProfileId,
        boolean force
) {

    public MatchingRunRequest {
        if (candidateProfileId == null
                || candidateProfileId.isBlank()) {

            throw new IllegalArgumentException(
                    "candidateProfileId must not be blank"
            );
        }

        candidateProfileId =
                candidateProfileId.trim();
    }

    public static MatchingRunRequest of(
            String candidateProfileId
    ) {
        return new MatchingRunRequest(
                candidateProfileId,
                false
        );
    }
}