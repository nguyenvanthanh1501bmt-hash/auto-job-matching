package com.autojob.modules.matching.contract;

import com.autojob.modules.matching.domain.MatchResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record MatchingResponse(
        String candidateProfileId,
        String candidateEmbeddingId,
        String rankingVersion,

        int retrievedCount,
        int loadedJobCount,
        int matchedCount,

        boolean reusedExisting,

        List<MatchItem> results
) {

    /*
     * =========================================================
     * Presentation thresholds
     * =========================================================
     *
     * IMPORTANT:
     *
     * Các threshold này KHÔNG:
     *
     * - thay đổi finalScore
     * - thay đổi ranking
     * - loại job
     * - ảnh hưởng Qdrant retrieval
     *
     * Chúng chỉ giúp frontend giải thích recommendation
     * theo cách dễ hiểu hơn.
     */

    private static final double UNKNOWN_STRUCTURED_SCORE =
            0.50d;

    private static final double EPSILON =
            0.000000001d;

    private static final double STRONG_SKILL_SCORE =
            0.55d;

    private static final double STRETCH_SKILL_SCORE =
            0.15d;

    private static final double SOME_SKILL_SCORE =
            0.10d;

    private static final double HIGH_SEMANTIC_SCORE =
            0.85d;

    private static final double STRONG_MATCH_SEMANTIC_SCORE =
            0.75d;

    private static final double MODERATE_SEMANTIC_SCORE =
            0.65d;

    private static final double SEVERE_SENIORITY_GAP =
            0.20d;

    private static final double MODERATE_SENIORITY_GAP =
            0.35d;

    private static final double GOOD_STRUCTURED_SCORE =
            0.65d;

    private static final double GOOD_LOCATION_SCORE =
            0.80d;

    private static final double WEAK_LOCATION_SCORE =
            0.35d;

    private static final int MAX_EXPLAINED_SKILLS =
            4;

    public MatchingResponse {

        results =
                results == null
                        ? List.of()
                        : List.copyOf(results);
    }

    public static MatchingResponse from(
            MatchingRunResult result
    ) {
        Objects.requireNonNull(
                result,
                "result must not be null"
        );

        List<MatchItem> items =
                result
                        .results()
                        .stream()
                        .map(
                                MatchItem::from
                        )
                        .toList();

        return new MatchingResponse(
                result.candidateProfileId(),
                result.candidateEmbeddingId(),
                result.rankingVersion(),

                result.retrievedCount(),
                result.loadedJobCount(),
                result.matchedCount(),

                result.reusedExisting(),

                items
        );
    }

    public record MatchItem(
            String normalizedJobId,
            String qdrantPointId,

            int rank,

            JobSnapshot job,

            ScoreBreakdown score,

            MatchTier matchTier,

            List<String> explanations,

            List<String> matchedSkills,
            List<String> missingSkills,

            VersionSnapshot versions,

            Instant generatedAt
    ) {

        public MatchItem {

            explanations =
                    explanations == null
                            ? List.of()
                            : List.copyOf(
                            explanations
                    );

            matchedSkills =
                    matchedSkills == null
                            ? List.of()
                            : List.copyOf(
                            matchedSkills
                    );

            missingSkills =
                    missingSkills == null
                            ? List.of()
                            : List.copyOf(
                            missingSkills
                    );
        }

        static MatchItem from(
                MatchResult document
        ) {
            ScoreBreakdown score =
                    new ScoreBreakdown(
                            document.getFinalScore(),
                            document.getSemanticScore(),
                            document.getSkillScore(),
                            document.getSeniorityScore(),
                            document.getLocationScore(),
                            document.getFreshnessScore()
                    );

            MatchPresentation presentation =
                    buildPresentation(
                            document
                    );

            return new MatchItem(
                    document.getNormalizedJobId(),
                    document.getQdrantPointId(),

                    document.getRank(),

                    new JobSnapshot(
                            document.getSourceCode(),
                            document.getSourceJobId(),

                            document.getJobTitle(),
                            document.getCompanyName(),

                            document.getLocations(),
                            document.getLocationText(),
                            document.getSalaryText(),

                            document.getJobType(),
                            document.getApplyType(),

                            document.getDetailUrl(),
                            document.getApplyUrl(),

                            document.getPostedAt(),
                            document.getDeadlineAt()
                    ),

                    score,

                    presentation.tier(),

                    presentation.explanations(),

                    document.getMatchedSkills(),
                    document.getMissingSkills(),

                    new VersionSnapshot(
                            document.getParserVersion(),
                            document.getNormalizationVersion(),
                            document.getEmbeddingVersion(),
                            document.getCandidateTextVersion(),
                            document.getJobTextVersion(),
                            document.getRankingVersion()
                    ),

                    document.getGeneratedAt()
            );
        }
    }

    /*
     * =========================================================
     * Match presentation
     * =========================================================
     */

    public enum MatchTier {

        /**
         * Job có skill overlap mạnh và semantic relevance tốt.
         *
         * Không có nghĩa là candidate match 100%.
         */
        STRONG,

        /**
         * Job có technical/skill relevance đáng kể nhưng
         * có gap rõ, thường là seniority.
         */
        STRETCH,

        /**
         * Job có một số tín hiệu phù hợp và đáng để xem.
         */
        POSSIBLE,

        /**
         * Job được giữ chủ yếu vì semantic relevance.
         *
         * Đây là recommendation mang tính khám phá.
         */
        EXPLORE
    }

    private static MatchPresentation buildPresentation(
            MatchResult document
    ) {
        double semantic =
                document.getSemanticScore();

        double skill =
                document.getSkillScore();

        double seniority =
                document.getSeniorityScore();

        MatchTier tier =
                classifyTier(
                        semantic,
                        skill,
                        seniority
                );

        List<String> explanations =
                buildExplanations(
                        document,
                        tier
                );

        return new MatchPresentation(
                tier,
                explanations
        );
    }

    private static MatchTier classifyTier(
            double semantic,
            double skill,
            double seniority
    ) {
        /*
         * -----------------------------------------------------
         * STRONG
         * -----------------------------------------------------
         *
         * Stack/skill overlap mạnh + semantic relevance tốt.
         *
         * Seniority không biến một job thành "không phù hợp";
         * tier này mô tả relevance tổng thể.
         */
        if (skill >= STRONG_SKILL_SCORE
                && semantic
                >= STRONG_MATCH_SEMANTIC_SCORE) {

            return MatchTier.STRONG;
        }

        /*
         * -----------------------------------------------------
         * STRETCH
         * -----------------------------------------------------
         *
         * Có skill overlap thật nhưng seniority gap lớn.
         *
         * Ví dụ:
         *
         * candidate junior
         * -> senior backend role
         *
         * Vẫn đáng xem, chỉ là stretch.
         */
        if (skill >= STRETCH_SKILL_SCORE
                && isKnownStructuredScore(
                seniority
        )
                && seniority
                < SEVERE_SENIORITY_GAP) {

            return MatchTier.STRETCH;
        }

        /*
         * -----------------------------------------------------
         * POSSIBLE
         * -----------------------------------------------------
         *
         * Có ít nhất một structured skill signal.
         */
        if (skill >= SOME_SKILL_SCORE) {

            return MatchTier.POSSIBLE;
        }

        /*
         * Không có direct skill overlap nhưng semantic rất cao
         * và không có seniority contradiction cực mạnh.
         *
         * Vẫn có thể là một adjacent opportunity.
         */
        if (semantic >= HIGH_SEMANTIC_SCORE
                && !isSevereSeniorityGap(
                seniority
        )) {

            return MatchTier.POSSIBLE;
        }

        /*
         * -----------------------------------------------------
         * EXPLORE
         * -----------------------------------------------------
         *
         * Semantic retrieval thấy có liên quan nhưng
         * structured evidence còn yếu.
         */
        return MatchTier.EXPLORE;
    }

    private static List<String> buildExplanations(
            MatchResult document,
            MatchTier tier
    ) {
        List<String> explanations =
                new ArrayList<>();

        List<String> matchedSkills =
                safeList(
                        document.getMatchedSkills()
                );

        List<String> missingSkills =
                safeList(
                        document.getMissingSkills()
                );

        double semantic =
                document.getSemanticScore();

        double skill =
                document.getSkillScore();

        double seniority =
                document.getSeniorityScore();

        double location =
                document.getLocationScore();

        /*
         * -----------------------------------------------------
         * Skill explanation
         * -----------------------------------------------------
         */
        if (skill >= STRONG_SKILL_SCORE) {

            if (!matchedSkills.isEmpty()) {

                explanations.add(
                        "Strong skill overlap: "
                                + summarizeSkills(
                                matchedSkills
                        )
                );

            } else {

                explanations.add(
                        "Strong structured skill compatibility"
                );
            }

        } else if (skill >= 0.25d) {

            if (!matchedSkills.isEmpty()) {

                explanations.add(
                        "Moderate skill overlap: "
                                + summarizeSkills(
                                matchedSkills
                        )
                );

            } else {

                explanations.add(
                        "Moderate structured skill compatibility"
                );
            }

        } else if (skill > 0.0d) {

            if (!matchedSkills.isEmpty()) {

                explanations.add(
                        "Some skill overlap: "
                                + summarizeSkills(
                                matchedSkills
                        )
                );

            } else {

                explanations.add(
                        "Some structured skill compatibility"
                );
            }

        } else {

            explanations.add(
                    "No direct structured skill overlap found"
            );
        }

        /*
         * -----------------------------------------------------
         * Semantic explanation
         * -----------------------------------------------------
         */
        if (semantic >= HIGH_SEMANTIC_SCORE) {

            explanations.add(
                    "High semantic relevance between CV and job"
            );

        } else if (semantic
                >= MODERATE_SEMANTIC_SCORE) {

            explanations.add(
                    "Moderate semantic relevance between CV and job"
            );

        } else {

            explanations.add(
                    "Limited semantic relevance between CV and job"
            );
        }

        /*
         * -----------------------------------------------------
         * Seniority explanation
         * -----------------------------------------------------
         */
        if (isKnownStructuredScore(
                seniority
        )) {

            if (seniority
                    < SEVERE_SENIORITY_GAP) {

                explanations.add(
                        "Seniority is a significant stretch"
                );

            } else if (seniority
                    < MODERATE_SENIORITY_GAP) {

                explanations.add(
                        "Seniority may be a stretch"
                );

            } else if (seniority
                    >= GOOD_STRUCTURED_SCORE) {

                explanations.add(
                        "Seniority aligns well"
                );
            }
        }

        /*
         * -----------------------------------------------------
         * Location explanation
         * -----------------------------------------------------
         */
        if (isKnownStructuredScore(
                location
        )) {

            if (location
                    >= GOOD_LOCATION_SCORE) {

                explanations.add(
                        "Location aligns with candidate context"
                );

            } else if (location
                    < WEAK_LOCATION_SCORE) {

                explanations.add(
                        "Location may be less aligned"
                );
            }
        }

        /*
         * -----------------------------------------------------
         * Missing skills
         * -----------------------------------------------------
         *
         * Chỉ expose như informational explanation.
         * Missing skill không đồng nghĩa candidate không thể
         * apply.
         */
        if (!missingSkills.isEmpty()
                && tier != MatchTier.EXPLORE) {

            explanations.add(
                    "Additional job skills not found in CV: "
                            + summarizeSkills(
                            missingSkills
                    )
            );
        }

        return List.copyOf(
                explanations
        );
    }

    private static boolean isSevereSeniorityGap(
            double score
    ) {
        return isKnownStructuredScore(
                score
        )
                && score
                < SEVERE_SENIORITY_GAP;
    }

    private static boolean isKnownStructuredScore(
            double score
    ) {
        return Double.isFinite(score)
                && Math.abs(
                score
                        - UNKNOWN_STRUCTURED_SCORE
        ) > EPSILON;
    }

    private static String summarizeSkills(
            List<String> skills
    ) {
        if (skills == null
                || skills.isEmpty()) {

            return "";
        }

        List<String> clean =
                skills
                        .stream()
                        .filter(
                                Objects::nonNull
                        )
                        .map(
                                String::trim
                        )
                        .filter(
                                value ->
                                        !value.isBlank()
                        )
                        .distinct()
                        .limit(
                                MAX_EXPLAINED_SKILLS
                        )
                        .toList();

        return String.join(
                ", ",
                clean
        );
    }

    private static List<String> safeList(
            List<String> values
    ) {
        if (values == null
                || values.isEmpty()) {

            return List.of();
        }

        return List.copyOf(
                values
        );
    }

    private record MatchPresentation(
            MatchTier tier,
            List<String> explanations
    ) {

        private MatchPresentation {

            Objects.requireNonNull(
                    tier,
                    "tier must not be null"
            );

            explanations =
                    explanations == null
                            ? List.of()
                            : List.copyOf(
                            explanations
                    );
        }
    }

    /**
     * Những field frontend cần để render job card.
     *
     * Đây là snapshot tại thời điểm matching,
     * không phải live lookup từ normalized_jobs.
     */
    public record JobSnapshot(
            String sourceCode,
            String sourceJobId,

            String title,
            String companyName,

            List<String> locations,
            String locationText,
            String salaryText,

            String jobType,
            String applyType,

            String detailUrl,
            String applyUrl,

            Instant postedAt,
            Instant deadlineAt
    ) {

        public JobSnapshot {

            locations =
                    locations == null
                            ? List.of()
                            : List.copyOf(
                            locations
                    );
        }
    }

    public record ScoreBreakdown(
            double finalScore,
            double semanticScore,
            double skillScore,
            double seniorityScore,
            double locationScore,
            double freshnessScore
    ) {
    }

    public record VersionSnapshot(
            String parserVersion,
            String normalizationVersion,
            String embeddingVersion,
            String candidateTextVersion,
            String jobTextVersion,
            String rankingVersion
    ) {
    }
}