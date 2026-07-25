package com.autojob.modules.auth.ratelimit;

import java.time.Duration;

final class TokenBucket {

    private final int capacity;
    private final double refillPerNano;

    private double availableTokens;
    private long lastRefillNanos;
    private volatile long lastAccessNanos;

    TokenBucket(
            int capacity,
            Duration refillPeriod
    ) {
        if (capacity < 1) {
            throw new IllegalArgumentException(
                    "Rate-limit capacity must be >= 1"
            );
        }

        if (refillPeriod == null
                || refillPeriod.isZero()
                || refillPeriod.isNegative()) {
            throw new IllegalArgumentException(
                    "Rate-limit refill period must be positive"
            );
        }

        this.capacity = capacity;
        this.availableTokens = capacity;
        this.lastRefillNanos = System.nanoTime();
        this.lastAccessNanos = this.lastRefillNanos;

        this.refillPerNano =
                (double) capacity
                        / refillPeriod.toNanos();
    }

    synchronized ConsumptionResult tryConsume() {
        long now = System.nanoTime();

        refill(now);
        lastAccessNanos = now;

        if (availableTokens >= 1.0d) {
            availableTokens -= 1.0d;

            return new ConsumptionResult(
                    true,
                    (int) Math.floor(availableTokens),
                    0
            );
        }

        double missing = 1.0d - availableTokens;
        long retryNanos =
                (long) Math.ceil(
                        missing / refillPerNano
                );

        long retrySeconds = Math.max(
                1,
                (long) Math.ceil(
                        retryNanos / 1_000_000_000.0d
                )
        );

        return new ConsumptionResult(
                false,
                0,
                retrySeconds
        );
    }

    long lastAccessNanos() {
        return lastAccessNanos;
    }

    private void refill(long now) {
        long elapsed = now - lastRefillNanos;

        if (elapsed <= 0) {
            return;
        }

        availableTokens = Math.min(
                capacity,
                availableTokens
                        + elapsed * refillPerNano
        );

        lastRefillNanos = now;
    }

    record ConsumptionResult(
            boolean allowed,
            int remaining,
            long retryAfterSeconds
    ) {
    }
}