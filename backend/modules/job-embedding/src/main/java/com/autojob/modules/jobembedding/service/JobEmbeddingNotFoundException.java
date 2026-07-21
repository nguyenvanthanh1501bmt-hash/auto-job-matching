package com.autojob.modules.jobembedding.service;

public class JobEmbeddingNotFoundException
        extends RuntimeException {

    public JobEmbeddingNotFoundException(
            String normalizedJobId
    ) {
        super(
                "Job embedding was not found for normalizedJobId="
                        + normalizedJobId
        );
    }
}