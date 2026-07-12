package com.autojob.common.events;

import java.time.Instant;

public class JobNormalizedReadyEvent {

    private final String normalizedJobId;
    private final String rawJobId;
    private final String sourceCode;
    private final String normalizationVersion;
    private final Instant occurredAt;

    public JobNormalizedReadyEvent(
            String normalizedJobId,
            String rawJobId,
            String sourceCode,
            String normalizationVersion,
            Instant occurredAt
    ) {
        this.normalizedJobId = normalizedJobId;
        this.rawJobId = rawJobId;
        this.sourceCode = sourceCode;
        this.normalizationVersion = normalizationVersion;
        this.occurredAt = occurredAt;
    }

    public String getNormalizedJobId() {
        return normalizedJobId;
    }

    public String getRawJobId() {
        return rawJobId;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getNormalizationVersion() {
        return normalizationVersion;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}