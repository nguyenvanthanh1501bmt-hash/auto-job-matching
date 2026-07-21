package com.autojob.modules.jobembedding.vectorstore;

import java.util.List;

public record JobVectorPoint(
        String pointId,
        String normalizedJobId,
        String sourceCode,
        String normalizationVersion,
        String embeddingVersion,
        String textHash,
        List<Double> vector
) {
}