package com.autojob.modules.matching.service;

/**
 * Matching chưa thể thực hiện vì input hoặc upstream state
 * chưa thỏa điều kiện.
 */
public class MatchingPreconditionException
        extends RuntimeException {

    private final Reason reason;
    private final String candidateProfileId;

    public MatchingPreconditionException(
            Reason reason,
            String candidateProfileId,
            String message
    ) {
        super(message);

        this.reason = reason;
        this.candidateProfileId =
                candidateProfileId;
    }

    public Reason getReason() {
        return reason;
    }

    public String getCandidateProfileId() {
        return candidateProfileId;
    }

    public static MatchingPreconditionException
    authenticationRequired(
            String candidateProfileId
    ) {
        return new MatchingPreconditionException(
                Reason.AUTHENTICATION_REQUIRED,
                candidateProfileId,
                "Authentication is required"
        );
    }

    public static MatchingPreconditionException
    candidateProfileNotFound(
            String candidateProfileId
    ) {
        return new MatchingPreconditionException(
                Reason.CANDIDATE_PROFILE_NOT_FOUND,
                candidateProfileId,
                "Candidate profile not found: "
                        + candidateProfileId
        );
    }

    public static MatchingPreconditionException
    readyEmbeddingNotFound(
            String candidateProfileId,
            String textVersion
    ) {
        return new MatchingPreconditionException(
                Reason.READY_CANDIDATE_EMBEDDING_NOT_FOUND,
                candidateProfileId,
                "No READY candidate embedding found "
                        + "for candidateProfileId="
                        + candidateProfileId
                        + " and textVersion="
                        + textVersion
        );
    }

    public static MatchingPreconditionException
    staleEmbedding(
            String candidateProfileId
    ) {
        return new MatchingPreconditionException(
                Reason.CANDIDATE_EMBEDDING_STALE,
                candidateProfileId,
                "Candidate embedding is older than "
                        + "the current candidate profile: "
                        + candidateProfileId
        );
    }

    public static MatchingPreconditionException
    invalidEmbedding(
            String candidateProfileId,
            String message
    ) {
        return new MatchingPreconditionException(
                Reason.CANDIDATE_EMBEDDING_INVALID,
                candidateProfileId,
                message
        );
    }

    public static MatchingPreconditionException
    matchResultNotFound(
            String candidateProfileId
    ) {
        return new MatchingPreconditionException(
                Reason.MATCH_RESULT_NOT_FOUND,
                candidateProfileId,
                "No current matching result found for "
                        + "candidateProfileId="
                        + candidateProfileId
        );
    }

    public enum Reason {

        AUTHENTICATION_REQUIRED,

        CANDIDATE_PROFILE_NOT_FOUND,

        READY_CANDIDATE_EMBEDDING_NOT_FOUND,

        CANDIDATE_EMBEDDING_STALE,

        CANDIDATE_EMBEDDING_INVALID,

        MATCH_RESULT_NOT_FOUND
    }
}