package com.autojob.modules.matching.filter;

import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.config.MatchingProperties;
import com.autojob.modules.matching.domain.HybridScore;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MatchAcceptanceFilter {

    private final MatchingProperties properties;

    public MatchAcceptanceFilter(
            MatchingProperties properties
    ) {
        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );
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
         * =====================================================
         * Stage 1: baseline relevance
         * =====================================================
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
         * =====================================================
         * Stage 2: strong professional skill override
         * =====================================================
         *
         * Chỉ KNOWN skill evidence mới được phép override
         * seniority/location contradiction.
         */
        boolean strongSkillEvidence =
                score.skillKnown()
                        && score.skillScore()
                        >= config.getStrongSkillScore();

        if (strongSkillEvidence) {

            return true;
        }

        /*
         * =====================================================
         * Stage 3: structured contradiction
         * =====================================================
         */
        if (hasStructuredContradiction(
                score,
                config
        )) {

            return false;
        }

        /*
         * =====================================================
         * Stage 4: JD has real primary/domain skill evidence
         * =====================================================
         *
         * Đây là rule quan trọng:
         *
         * Nếu skillKnown=true thì chúng ta ĐÃ BIẾT JD yêu cầu
         * professional/domain skills gì.
         *
         * Candidate phải đạt minimum skill fit.
         *
         * Semantic KHÔNG được phép cứu một candidate có
         * skill fit cực thấp.
         *
         * Ví dụ:
         *
         * Semantic = 0.92
         * Skill    = 0.03
         *
         * => REJECT.
         */
        if (score.skillKnown()) {

            return score.skillScore()
                    >= config.getMinimumSkillScore();
        }

        /*
         * =====================================================
         * Stage 5: semantic-only fallback
         * =====================================================
         *
         * Chỉ dùng khi skillKnown=false:
         *
         * - JD không có skill data
         * - JD chỉ có SECONDARY/generic skills
         * - không có professional/domain skill evidence
         *
         * Lúc này semantic mới được phép làm fallback.
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

        if (score.seniorityKnown()
                && score.seniorityScore()
                < threshold) {

            return true;
        }

        return score.locationKnown()
                && score.locationScore()
                < threshold;
    }
}