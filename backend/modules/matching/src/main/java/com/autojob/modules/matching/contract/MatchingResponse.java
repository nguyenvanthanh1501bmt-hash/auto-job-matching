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
     * Presentation threshold.
     *
     * Chỉ phục vụ explanation/tier.
     * Không ảnh hưởng ranking.
     */
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
                        : List.copyOf(
                        results
                );
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
                            document.getFreshnessScore(),

                            isTrue(
                                    document.getSkillKnown()
                            ),

                            isTrue(
                                    document.getSeniorityKnown()
                            ),

                            isTrue(
                                    document.getLocationKnown()
                            ),

                            isTrue(
                                    document.getFreshnessKnown()
                            )
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

    public enum MatchTier {

        /*
         * Skill overlap mạnh + semantic relevance tốt.
         */
        STRONG,

        /*
         * Có skill relevance nhưng seniority gap lớn.
         */
        STRETCH,

        /*
         * Có structured signal hoặc semantic signal tốt.
         */
        POSSIBLE,

        /*
         * Chủ yếu là semantic exploration.
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

        boolean skillKnown =
                isTrue(
                        document.getSkillKnown()
                );

        boolean seniorityKnown =
                isTrue(
                        document.getSeniorityKnown()
                );

        MatchTier tier =
                classifyTier(
                        semantic,
                        skill,
                        skillKnown,
                        seniority,
                        seniorityKnown
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
            boolean skillKnown,
            double seniority,
            boolean seniorityKnown
    ) {

        /*
         * STRONG
         *
         * Chỉ được dùng skill nếu skillKnown = true.
         *
         * Vì vậy:
         *
         * skillScore = 0.50
         * skillKnown = false
         *
         * KHÔNG bị hiểu là 50% skill match.
         */
        if (skillKnown
                && skill
                >= STRONG_SKILL_SCORE
                && semantic
                >= STRONG_MATCH_SEMANTIC_SCORE) {

            return MatchTier.STRONG;
        }

        /*
         * STRETCH.
         */
        if (skillKnown
                && skill
                >= STRETCH_SKILL_SCORE
                && seniorityKnown
                && seniority
                < SEVERE_SENIORITY_GAP) {

            return MatchTier.STRETCH;
        }

        /*
         * POSSIBLE bởi structured skill evidence.
         */
        if (skillKnown
                && skill
                >= SOME_SKILL_SCORE) {

            return MatchTier.POSSIBLE;
        }

        /*
         * Semantic-only possible.
         *
         * Cho phép adjacent opportunity nếu semantic rất cao,
         * miễn không có seniority contradiction thật sự.
         */
        if (semantic
                >= HIGH_SEMANTIC_SCORE
                && !isSevereSeniorityGap(
                seniority,
                seniorityKnown
        )) {

            return MatchTier.POSSIBLE;
        }

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

        boolean skillKnown =
                isTrue(
                        document.getSkillKnown()
                );

        boolean seniorityKnown =
                isTrue(
                        document.getSeniorityKnown()
                );

        boolean locationKnown =
                isTrue(
                        document.getLocationKnown()
                );

        /*
         * =====================================================
         * Skill explanation
         * =====================================================
         */
        if (!skillKnown) {

            /*
             * Đây là khác biệt quan trọng.
             *
             * Job không có structured skill data
             * không được nói:
             *
             * "50% skill compatibility"
             *
             * nữa.
             */
            explanations.add(
                    "Job does not provide enough structured skill data"
            );

        } else if (skill
                >= STRONG_SKILL_SCORE) {

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

        } else if (skill
                >= 0.25d) {

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
         * =====================================================
         * Semantic explanation
         * =====================================================
         */
        if (semantic
                >= HIGH_SEMANTIC_SCORE) {

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
         * =====================================================
         * Seniority explanation
         * =====================================================
         *
         * Không kiểm tra seniority == 0.50 nữa.
         *
         * Chỉ nhìn seniorityKnown.
         */
        if (seniorityKnown) {

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
         * =====================================================
         * Location explanation
         * =====================================================
         */
        if (locationKnown) {

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
         * Missing skill explanation.
         *
         * Nếu job không có structured skill data
         * thì không tạo missing-skill explanation.
         */
        if (skillKnown
                && !missingSkills.isEmpty()
                && tier
                != MatchTier.EXPLORE) {

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
            double score,
            boolean known
    ) {
        return known
                && Double.isFinite(
                score
        )
                && score
                < SEVERE_SENIORITY_GAP;
    }

    private static boolean isTrue(
            Boolean value
    ) {
        return Boolean.TRUE.equals(
                value
        );
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

    /*
     * API trả luôn known flags.
     *
     * Frontend sau này có thể:
     *
     * known = false
     * -> hiển thị "N/A"
     *
     * thay vì hiển thị 50%.
     */
    public record ScoreBreakdown(
            double finalScore,
            double semanticScore,
            double skillScore,
            double seniorityScore,
            double locationScore,
            double freshnessScore,

            boolean skillKnown,
            boolean seniorityKnown,
            boolean locationKnown,
            boolean freshnessKnown
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