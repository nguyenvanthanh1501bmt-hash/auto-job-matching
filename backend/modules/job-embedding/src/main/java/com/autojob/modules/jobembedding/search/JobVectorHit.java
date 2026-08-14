package com.autojob.modules.jobembedding.search;

public record JobVectorHit(
        String normalizedJobId,
        String pointId,
        double score
) {
}