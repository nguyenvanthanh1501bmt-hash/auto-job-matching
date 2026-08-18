package com.autojob.modules.matching.contract;

import com.autojob.modules.matching.domain.MatchResult;

import java.util.List;

/**
 * Output của một matching run.
 *
 * retrievedCount:
 * số vector hit Qdrant trả về trong invocation hiện tại.
 *
 * loadedJobCount:
 * số hit load được NormalizedJob tương ứng.
 *
 * matchedCount:
 * số result cuối cùng sau hard filter + hybrid reranking.
 *
 * Nếu reusedExisting = true thì invocation hiện tại
 * không gọi lại Qdrant, vì vậy retrievedCount và
 * loadedJobCount sẽ bằng 0.
 */
public record MatchingRunResult(
        String candidateProfileId,
        String candidateEmbeddingId,
        String rankingVersion,

        int retrievedCount,
        int loadedJobCount,
        int matchedCount,

        boolean reusedExisting,

        List<MatchResult> results
) {

    public MatchingRunResult {
        requireText(
                candidateProfileId,
                "candidateProfileId"
        );

        requireText(
                candidateEmbeddingId,
                "candidateEmbeddingId"
        );

        requireText(
                rankingVersion,
                "rankingVersion"
        );

        requireNonNegative(
                retrievedCount,
                "retrievedCount"
        );

        requireNonNegative(
                loadedJobCount,
                "loadedJobCount"
        );

        requireNonNegative(
                matchedCount,
                "matchedCount"
        );

        results =
                results == null
                        ? List.of()
                        : List.copyOf(results);

        if (matchedCount != results.size()) {
            throw new IllegalArgumentException(
                    "matchedCount must equal results.size()"
            );
        }
    }

    private static void requireText(
            String value,
            String fieldName
    ) {
        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    fieldName
                            + " must not be blank"
            );
        }
    }

    private static void requireNonNegative(
            int value,
            String fieldName
    ) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must not be negative"
            );
        }
    }
}