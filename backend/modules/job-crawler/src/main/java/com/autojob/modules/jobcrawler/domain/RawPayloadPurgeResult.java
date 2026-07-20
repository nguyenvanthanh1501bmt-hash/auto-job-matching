package com.autojob.modules.jobcrawler.domain;

import java.time.Instant;

public record RawPayloadPurgeResult(
        String rawJobId,
        long matchedCount,
        long modifiedCount,
        Instant purgedAt
) {
}