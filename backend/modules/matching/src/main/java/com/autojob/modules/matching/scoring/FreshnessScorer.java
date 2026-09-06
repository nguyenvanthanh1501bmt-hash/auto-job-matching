package com.autojob.modules.matching.scoring;

import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.config.MatchingProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Component
public class FreshnessScorer {

    /**
     * Chỉ dùng làm numeric placeholder khi freshness UNKNOWN.
     *
     * Ranking KHÔNG được suy luận UNKNOWN từ giá trị 0.50.
     * Phải dùng Result.known().
     */
    private static final double UNKNOWN_SCORE =
            0.50d;

    private final MatchingProperties properties;
    private final Clock clock;

    public FreshnessScorer(
            MatchingProperties properties,
            Clock clock
    ) {
        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );

        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock must not be null"
                );
    }

    /**
     * Backward-compatible numeric API.
     *
     * Ranking mới nên dùng evaluate() để biết freshness
     * có evidence thật hay không.
     */
    public double score(
            NormalizedJob job
    ) {
        return evaluate(
                job
        ).score();
    }

    /**
     * Freshness chỉ được tính khi crawler/normalizer
     * thực sự có postedAt.
     *
     * KHÔNG fallback sang normalizedAt.
     *
     * normalizedAt chỉ cho biết lúc hệ thống của chúng ta
     * xử lý job, không cho biết lúc nhà tuyển dụng đăng job.
     */
    public Result evaluate(
            NormalizedJob job
    ) {
        Objects.requireNonNull(
                job,
                "job must not be null"
        );

        Instant postedAt =
                job.getPostedAt();

        /*
         * Crawler không lấy được datePosted.
         *
         * Ví dụ:
         *
         * postedAt     = null
         * normalizedAt = hôm nay
         *
         * Không được chấm freshness = 1.0 chỉ vì hệ thống
         * vừa crawl/normalize job hôm nay.
         */
        if (postedAt == null) {
            return Result.unknown();
        }

        Instant now =
                Instant.now(
                        clock
                );

        /*
         * Một số source có thể lệch timezone hoặc datePosted
         * hơi nằm trong tương lai.
         *
         * Không cần reject; coi là fresh.
         */
        if (postedAt.isAfter(now)) {
            return Result.known(
                    1.0d
            );
        }

        long ageDays =
                Duration.between(
                        postedAt,
                        now
                ).toDays();

        int freshDays =
                properties
                        .getFreshness()
                        .getFreshDays();

        int maxAgeDays =
                properties
                        .getFreshness()
                        .getMaxAgeDays();

        if (ageDays <= freshDays) {

            return Result.known(
                    1.0d
            );
        }

        if (ageDays >= maxAgeDays) {

            return Result.known(
                    0.0d
            );
        }

        /*
         * Config không hợp lý:
         *
         * maxAgeDays <= freshDays
         *
         * Không chia cho 0 / âm.
         */
        if (maxAgeDays <= freshDays) {

            return Result.known(
                    0.0d
            );
        }

        double progress =
                (double) (
                        ageDays - freshDays
                )
                        / (double) (
                        maxAgeDays - freshDays
                );

        double score =
                clamp01(
                        1.0d - progress
                );

        return Result.known(
                score
        );
    }

    private static double clamp01(
            double value
    ) {
        return Math.max(
                0.0d,
                Math.min(
                        1.0d,
                        value
                )
        );
    }

    public record Result(
            double score,
            boolean known
    ) {

        public Result {

            if (!Double.isFinite(score)
                    || score < 0.0d
                    || score > 1.0d) {

                throw new IllegalArgumentException(
                        "score must be between 0.0 and 1.0"
                );
            }
        }

        public static Result known(
                double score
        ) {
            return new Result(
                    score,
                    true
            );
        }

        public static Result unknown() {
            return new Result(
                    UNKNOWN_SCORE,
                    false
            );
        }
    }
}