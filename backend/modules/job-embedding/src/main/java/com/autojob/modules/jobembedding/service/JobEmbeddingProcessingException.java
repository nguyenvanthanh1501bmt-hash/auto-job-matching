package com.autojob.modules.jobembedding.service;

public class JobEmbeddingProcessingException
        extends RuntimeException {

    private final String normalizedJobId;

    public JobEmbeddingProcessingException(
            String normalizedJobId,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.normalizedJobId = normalizedJobId;
    }

    public String getNormalizedJobId() {
        return normalizedJobId;
    }
}