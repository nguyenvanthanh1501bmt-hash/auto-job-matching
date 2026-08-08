package com.autojob.modules.candidateembedding.service;

public class CandidateEmbeddingProcessingException
        extends RuntimeException {

    private final String candidateProfileId;

    public CandidateEmbeddingProcessingException(
            String candidateProfileId,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.candidateProfileId = candidateProfileId;
    }

    public String getCandidateProfileId() {
        return candidateProfileId;
    }
}