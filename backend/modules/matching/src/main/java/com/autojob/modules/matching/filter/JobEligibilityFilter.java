package com.autojob.modules.matching.filter;

import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.config.MatchingProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Hard filter chạy trước ranking.
 *
 * Những job không còn hợp lệ phải bị loại hẳn,
 * không biến thành một score thấp rồi vẫn trả về frontend.
 */
@Component
public class JobEligibilityFilter {

    private final MatchingProperties properties;
    private final Clock clock;

    public JobEligibilityFilter(
            MatchingProperties properties,
            Clock clock
    ) {
        this.properties = Objects.requireNonNull(
                properties,
                "properties must not be null"
        );

        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
    }

    public EligibilityDecision evaluate(
            NormalizedJob job
    ) {
        if (job == null) {
            return EligibilityDecision.reject(
                    Reason.MISSING_JOB
            );
        }

        if (job.getId() == null
                || job.getId().isBlank()) {
            return EligibilityDecision.reject(
                    Reason.MISSING_JOB_ID
            );
        }

        String expectedNormalizationVersion =
                properties
                        .getCompatibility()
                        .getNormalizationVersion();

        if (!Objects.equals(
                expectedNormalizationVersion,
                job.getNormalizationVersion()
        )) {
            return EligibilityDecision.reject(
                    Reason.NORMALIZATION_VERSION_MISMATCH
            );
        }

        Instant now = Instant.now(clock);

        if (job.getDeadlineAt() != null
                && job.getDeadlineAt().isBefore(now)) {
            return EligibilityDecision.reject(
                    Reason.DEADLINE_PASSED
            );
        }

        if (isDefinitelyTooOld(job, now)) {
            return EligibilityDecision.reject(
                    Reason.TOO_OLD
            );
        }

        return EligibilityDecision.allow();
    }

    public boolean isEligible(
            NormalizedJob job
    ) {
        return evaluate(job).eligible();
    }

    /**
     * Hard age filter chỉ dùng postedAt vì đây mới là
     * thời điểm nghiệp vụ của job.
     *
     * Không dùng normalizedAt để reject cứng vì normalizedAt
     * chỉ là thời điểm hệ thống xử lý document.
     */
    private boolean isDefinitelyTooOld(
            NormalizedJob job,
            Instant now
    ) {
        Instant postedAt = job.getPostedAt();

        if (postedAt == null
                || postedAt.isAfter(now)) {
            return false;
        }

        int maxAgeDays =
                properties
                        .getFreshness()
                        .getMaxAgeDays();

        Duration maxAge = Duration.ofDays(
                maxAgeDays
        );

        return postedAt
                .plus(maxAge)
                .isBefore(now);
    }

    public enum Reason {
        ELIGIBLE,
        MISSING_JOB,
        MISSING_JOB_ID,
        NORMALIZATION_VERSION_MISMATCH,
        DEADLINE_PASSED,
        TOO_OLD
    }

    public record EligibilityDecision(
            boolean eligible,
            Reason reason
    ) {

        public EligibilityDecision {
            Objects.requireNonNull(
                    reason,
                    "reason must not be null"
            );
        }

        public static EligibilityDecision allow() {
            return new EligibilityDecision(
                    true,
                    Reason.ELIGIBLE
            );
        }

        public static EligibilityDecision reject(
                Reason reason
        ) {
            if (reason == Reason.ELIGIBLE) {
                throw new IllegalArgumentException(
                        "Reject reason must not be ELIGIBLE"
                );
            }

            return new EligibilityDecision(
                    false,
                    reason
            );
        }
    }
}