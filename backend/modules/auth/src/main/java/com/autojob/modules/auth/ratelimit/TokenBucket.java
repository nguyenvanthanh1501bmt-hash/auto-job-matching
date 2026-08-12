package com.autojob.modules.auth.ratelimit;

import java.time.Duration;

final class TokenBucket {

    // Số token tối đa bucket có thể chứa.
    private final int capacity;

    // Tốc độ refill, tính theo số token được thêm vào mỗi nanosecond.
    private final double refillPerNano;

    // Số token hiện tại trong bucket.
    private double availableTokens;

    // Thời điểm lần cuối thực hiện refill.
    private long lastRefillNanos;

    // Thời điểm bucket được truy cập gần nhất.
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

        // Tính tốc độ refill dựa trên capacity và khoảng thời gian refill.
        this.refillPerNano =
                (double) capacity
                        / refillPeriod.toNanos();
    }

    // Thử tiêu thụ 1 token cho một request.
    synchronized ConsumptionResult tryConsume() {
        long now = System.nanoTime();

        // Cập nhật số token trước khi xử lý request.
        refill(now);
        lastAccessNanos = now;

        if (availableTokens >= 1.0d) {
            // Có đủ token -> cho phép request và tiêu thụ 1 token.
            availableTokens -= 1.0d;

            return new ConsumptionResult(
                    true,
                    (int) Math.floor(availableTokens),
                    0
            );
        }

        // Tính số token còn thiếu để đạt đủ 1 token.
        double missing = 1.0d - availableTokens;

        // Tính thời gian cần chờ để refill đủ số token còn thiếu.
        long retryNanos =
                (long) Math.ceil(
                        missing / refillPerNano
                );

        // Chuyển thời gian chờ từ nanosecond sang giây.
        long retrySeconds = Math.max(
                1,
                (long) Math.ceil(
                        retryNanos / 1_000_000_000.0d
                )
        );

        // Không đủ token -> từ chối request và trả về thời gian retry.
        return new ConsumptionResult(
                false,
                0,
                retrySeconds
        );
    }

    // Lấy thời điểm bucket được truy cập gần nhất.
    long lastAccessNanos() {
        return lastAccessNanos;
    }

    // Refill token dựa trên khoảng thời gian đã trôi qua.
    private void refill(long now) {
        // Tính thời gian đã trôi qua kể từ lần refill trước.
        long elapsed = now - lastRefillNanos;

        if (elapsed <= 0) {
            return;
        }

        // Thêm token theo thời gian đã trôi qua nhưng không vượt quá capacity.
        availableTokens = Math.min(
                capacity,
                availableTokens
                        + elapsed * refillPerNano
        );

        // Cập nhật mốc thời gian refill.
        lastRefillNanos = now;
    }

    // Kết quả sau khi thử consume một token.
    record ConsumptionResult(
            boolean allowed,          // Request có được phép hay không.
            int remaining,            // Số token còn lại.
            long retryAfterSeconds    // Số giây cần chờ nếu request bị từ chối.
    ) {
    }
}