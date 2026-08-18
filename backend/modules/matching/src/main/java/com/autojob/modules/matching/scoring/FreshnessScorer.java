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

    private static final double UNKNOWN_SCORE = 0.50d;

    private final MatchingProperties properties;
    private final Clock clock;

    public FreshnessScorer(
            MatchingProperties properties,
            Clock clock
    ) {
        this.properties = properties;
        this.clock = clock;
    }

    public double score(
            NormalizedJob job
    ) {
        Objects.requireNonNull(
                job,
                "job must not be null"
        );

        Instant referenceTime =
                job.getPostedAt() != null
                        ? job.getPostedAt()
                        : job.getNormalizedAt();

        if (referenceTime == null) {
            return UNKNOWN_SCORE;
        }

        Instant now =
                Instant.now(clock);

        if (referenceTime.isAfter(now)) {
            return 1.0d;
        }

        long ageDays =
                Duration.between(
                        referenceTime,
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
            return 1.0d;
        }

        if (ageDays >= maxAgeDays) {
            return 0.0d;
        }

        if (maxAgeDays <= freshDays) {
            return 0.0d;
        }

        double progress =
                (double) (ageDays - freshDays)
                        / (double) (maxAgeDays - freshDays);

        return clamp01(
                1.0d - progress
        );
    }

    private static double clamp01(
            double value
    ) {
        return Math.max(
                0.0d,
                Math.min(1.0d, value)
        );
    }
}