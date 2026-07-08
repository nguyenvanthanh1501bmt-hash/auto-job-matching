package com.autojob.common.events;

import java.time.Instant;

public class JobRawCollectedEvent {

    private final String rawJobId;
    private final String sourceCode;
    private final String sourceJobId;
    private final String fingerprint;
    private final Instant collectedAt;

    public JobRawCollectedEvent(
            String rawJobId,
            String sourceCode,
            String sourceJobId,
            String fingerprint,
            Instant collectedAt
    ) {
        this.rawJobId = rawJobId;
        this.sourceCode = sourceCode;
        this.sourceJobId = sourceJobId;
        this.fingerprint = fingerprint;
        this.collectedAt = collectedAt;
    }

    public String getRawJobId() {
        return rawJobId;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getSourceJobId() {
        return sourceJobId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }
}