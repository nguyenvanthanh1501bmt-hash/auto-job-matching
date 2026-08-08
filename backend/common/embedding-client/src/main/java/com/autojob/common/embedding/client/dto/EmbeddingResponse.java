package com.autojob.common.embedding.client.dto;

import java.util.List;

public record EmbeddingResponse(
        List<Double> vector,
        Integer dimension,
        String modelName,
        String modelRevision,
        String embeddingVersion,
        String textHash,
        Boolean normalized
) {
}