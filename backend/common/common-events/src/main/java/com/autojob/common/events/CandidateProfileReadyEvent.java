package com.autojob.common.events;

import java.time.Instant;

public class CandidateProfileReadyEvent {

    private final String candidateProfileId;
    private final String rawCvId;
    private final String parserVersion;
    private final Instant occurredAt;

    public CandidateProfileReadyEvent(
            String candidateProfileId,
            String rawCvId,
            String parserVersion,
            Instant occurredAt
    ) {
        this.candidateProfileId = candidateProfileId;
        this.rawCvId = rawCvId;
        this.parserVersion = parserVersion;
        this.occurredAt = occurredAt;
    }

    public String getCandidateProfileId() {
        return candidateProfileId;
    }

    public String getRawCvId() {
        return rawCvId;
    }

    public String getParserVersion() {
        return parserVersion;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}