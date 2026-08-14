package com.autojob.modules.jobembedding.vectorstore;

import java.util.List;

public record JobVectorPoint(
        String pointId,
        String normalizedJobId,
        String sourceCode,
        String normalizationVersion,
        String embeddingVersion,
        String textVersion,
        String textHash,
        List<Double> vector
) {

    /**
     * Compatibility constructor cho các caller/test cũ.
     *
     * Production path mới luôn truyền textVersion rõ ràng.
     */
    public JobVectorPoint(
            String pointId,
            String normalizedJobId,
            String sourceCode,
            String normalizationVersion,
            String embeddingVersion,
            String textHash,
            List<Double> vector
    ) {
        this(
                pointId,
                normalizedJobId,
                sourceCode,
                normalizationVersion,
                embeddingVersion,
                "job-text-v1",
                textHash,
                vector
        );
    }
}