package com.autojob.modules.jobcrawler.service;

import java.time.Instant;

public record RawPayloadPurgeResult(
        String rawJobId,
        long matchedCount,
        long modifiedCount,
        Instant purgedAt
) {
}