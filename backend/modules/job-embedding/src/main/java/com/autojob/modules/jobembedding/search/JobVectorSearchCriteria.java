package com.autojob.modules.jobembedding.search;

public record JobVectorSearchCriteria(
        int limit,
        String normalizationVersion,
        String embeddingVersion,
        String textVersion
) {

    public JobVectorSearchCriteria {
        if (limit < 1) {
            throw new IllegalArgumentException(
                    "limit must be greater than 0"
            );
        }

        requireText(
                normalizationVersion,
                "normalizationVersion"
        );

        requireText(
                embeddingVersion,
                "embeddingVersion"
        );

        requireText(
                textVersion,
                "textVersion"
        );
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
}