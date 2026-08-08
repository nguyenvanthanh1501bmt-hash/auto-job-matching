package com.autojob.modules.candidateembedding.service;

public class CandidateEmbeddingNotFoundException
        extends RuntimeException {

    public CandidateEmbeddingNotFoundException(String message) {
        super(message);
    }

    public static CandidateEmbeddingNotFoundException candidateProfile(
            String candidateProfileId
    ) {
        return new CandidateEmbeddingNotFoundException(
                "Candidate profile was not found for candidateProfileId="
                        + candidateProfileId
        );
    }

    public static CandidateEmbeddingNotFoundException embedding(
            String candidateProfileId
    ) {
        return new CandidateEmbeddingNotFoundException(
                "Candidate embedding was not found for candidateProfileId="
                        + candidateProfileId
        );
    }
}