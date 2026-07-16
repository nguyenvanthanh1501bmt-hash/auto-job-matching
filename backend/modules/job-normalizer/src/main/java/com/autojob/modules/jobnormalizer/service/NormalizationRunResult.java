package com.autojob.modules.jobnormalizer.service;

import com.autojob.modules.jobcrawler.service.RawPayloadPurgeResult;

public record NormalizationRunResult(
        NormalizationExecution execution,
        RawPayloadPurgeResult rawPayloadPurgeResult,
        String purgeError
) {

    public boolean purgeFailed() {
        return purgeError != null;
    }

    public boolean rawPayloadPurged() {
        return rawPayloadPurgeResult != null
                && rawPayloadPurgeResult.matchedCount() > 0;
    }
}