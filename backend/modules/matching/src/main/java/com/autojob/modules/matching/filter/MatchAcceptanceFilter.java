package com.autojob.modules.matching.filter;

import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.config.MatchingProperties;
import com.autojob.modules.matching.domain.HybridScore;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MatchAcceptanceFilter {

    private static final double UNKNOWN_SENTINEL =
            0.50d;

    private static final double EPSILON =
            0.000000001d;

    private final MatchingProperties properties;

    public MatchAcceptanceFilter(
            MatchingProperties properties
    ) {
        this.properties =
                properties;
    }

    public boolean accept(
            NormalizedJob job,
            HybridScore score
    ) {
        Objects.requireNonNull(
                job,
                "job must not be null"
        );

        Objects.requireNonNull(
                score,
                "score must not be null"
        );

        MatchingProperties.Acceptance config =
                properties.getAcceptance();

        /*
         * -------------------------------------------------
         * Stage 1: baseline relevance
         * -------------------------------------------------
         */
        if (score.finalScore()
                < config.getMinimumFinalScore()) {

            return false;
        }

        if (score.semanticScore()
                < config.getMinimumSemanticScore()) {

            return false;
        }

        /*
         * -------------------------------------------------
         * Stage 2: strong skill evidence
         * -------------------------------------------------
         *
         * Strong core-stack compatibility được phép
         * override level/location mismatch.
         *
         * Ví dụ:
         *
         * candidate junior
         * nhưng match phần lớn stack của job mid.
         */
        if (score.skillScore()
                >= config.getStrongSkillScore()) {

            return true;
        }

        /*
         * -------------------------------------------------
         * Stage 3: structured contradiction gate
         * -------------------------------------------------
         *
         * Nếu skill chỉ yếu/vừa hoặc bằng 0 thì
         * seniority/location đã biết không được mâu thuẫn mạnh.
         */
        if (hasStructuredContradiction(
                score,
                config
        )) {

            return false;
        }

        /*
         * -------------------------------------------------
         * Stage 4: moderate skill corroboration
         * -------------------------------------------------
         */
        boolean hasJobSkills =
                job.getSkills() != null
                        && !job.getSkills().isEmpty();

        if (hasJobSkills
                && score.skillScore()
                >= config.getMinimumSkillScore()) {

            return true;
        }

        /*
         * -------------------------------------------------
         * Stage 5: semantic-only fallback
         * -------------------------------------------------
         *
         * Không có đủ structured skill overlap:
         * semantic phải thật sự mạnh.
         */
        return score.semanticScore()
                >= config.getStrongSemanticScore();
    }

    private boolean hasStructuredContradiction(
            HybridScore score,
            MatchingProperties.Acceptance config
    ) {
        double threshold =
                config
                        .getMinimumNonContradictoryStructuredScore();

        if (isContradictory(
                score.seniorityScore(),
                threshold
        )) {

            return true;
        }

        return isContradictory(
                score.locationScore(),
                threshold
        );
    }

    private boolean isContradictory(
            double score,
            double minimum
    ) {
        if (!Double.isFinite(score)) {
            return true;
        }

        /*
         * 0.5 = UNKNOWN/no-decision.
         *
         * Unknown không được coi là contradiction.
         */
        if (Math.abs(
                score - UNKNOWN_SENTINEL
        ) <= EPSILON) {

            return false;
        }

        return score < minimum;
    }
}