package com.autojob.modules.matching.scoring;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.jobnormalizer.config.SharedSeniorityTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class SeniorityScorer {

    private static final double UNKNOWN_SCORE = 0.50d;

    private final Map<String, Integer> rankByLevel;
    private final SharedSeniorityTaxonomyProperties taxonomy;

    public SeniorityScorer(
            SharedSeniorityTaxonomyProperties taxonomy
    ) {
        this.taxonomy = taxonomy;

        this.rankByLevel = buildRankMap(
                taxonomy.getLevels()
        );
    }

    /**
     * Backward-compatible numeric API.
     *
     * Với ranking/filter mới, hãy dùng evaluate() để lấy cả cờ known.
     */
    public double score(
            CandidateProfile candidate,
            NormalizedJob job
    ) {
        return evaluate(
                candidate,
                job
        ).score();
    }

    /**
     * Trả cả numeric score và việc score đó có evidence thật hay không.
     *
     * Không dùng 0.50 như sentinel nữa vì một score hợp lệ hoàn toàn
     * có thể bằng đúng 0.50 sau khi tính experience/seniority.
     */
    public Result evaluate(
            CandidateProfile candidate,
            NormalizedJob job
    ) {
        Objects.requireNonNull(
                candidate,
                "candidate must not be null"
        );

        Objects.requireNonNull(
                job,
                "job must not be null"
        );

        CandidateProfile.Seniority candidateLevel =
                resolveCandidateSeniority(candidate);

        Double levelScore =
                levelScore(
                        candidateLevel,
                        job.getSeniority()
                );

        Double candidateYears =
                resolveCandidateExperienceYears(
                        candidate
                );

        Double experienceScore =
                experienceScore(
                        candidateYears,
                        job.getExperienceMin(),
                        job.getExperienceMax()
                );

        if (levelScore != null
                && experienceScore != null) {

            return Result.known(
                    clamp01(
                            levelScore * 0.75d
                                    + experienceScore * 0.25d
                    )
            );
        }

        if (levelScore != null) {
            return Result.known(
                    levelScore
            );
        }

        if (experienceScore != null) {
            return Result.known(
                    experienceScore
            );
        }

        return Result.unknown();
    }

    private CandidateProfile.Seniority
    resolveCandidateSeniority(
            CandidateProfile candidate
    ) {
        if (candidate.getSeniority() != null
                && candidate.getSeniority()
                != CandidateProfile.Seniority.UNKNOWN) {

            return candidate.getSeniority();
        }

        if (candidate.getPreferredEmploymentTypes() != null
                && candidate
                .getPreferredEmploymentTypes()
                .contains(
                        CandidateProfile
                                .EmploymentType
                                .INTERNSHIP
                )) {

            return CandidateProfile.Seniority.INTERN;
        }

        String text = candidateIntentText(
                candidate
        );

        if (containsAny(
                text,
                "internship",
                "intern ",
                "intern position",
                "intern role",
                "thực tập",
                "thuc tap"
        )) {
            return CandidateProfile.Seniority.INTERN;
        }

        if (containsAny(
                text,
                "trainee",
                "học việc",
                "hoc viec"
        )) {
            return CandidateProfile.Seniority.TRAINEE;
        }

        if (containsAny(
                text,
                "fresher",
                "fresh graduate"
        )) {
            return CandidateProfile.Seniority.FRESHER;
        }

        if (containsAny(
                text,
                "entry level",
                "entry-level",
                "new graduate"
        )) {
            return CandidateProfile.Seniority.ENTRY_LEVEL;
        }

        if (containsAny(
                text,
                "junior",
                "jr."
        )) {
            return CandidateProfile.Seniority.JUNIOR;
        }

        Double years = validNonNegative(
                candidate.getExperienceYears()
        );

        if (years != null) {

            double entryUnder =
                    taxonomy
                            .getExperience()
                            .getEntryLevelUnder();

            double juniorUnder =
                    taxonomy
                            .getExperience()
                            .getJuniorUnder();

            double midUnder =
                    taxonomy
                            .getExperience()
                            .getMidUnder();

            if (years < entryUnder) {
                return CandidateProfile
                        .Seniority
                        .ENTRY_LEVEL;
            }

            if (years < juniorUnder) {
                return CandidateProfile
                        .Seniority
                        .JUNIOR;
            }

            if (years < midUnder) {
                return CandidateProfile
                        .Seniority
                        .MID;
            }

            return CandidateProfile
                    .Seniority
                    .SENIOR;
        }

        if (isCurrentStudent(candidate)
                && hasNoWorkExperience(candidate)) {

            return CandidateProfile
                    .Seniority
                    .ENTRY_LEVEL;
        }

        return CandidateProfile.Seniority.UNKNOWN;
    }

    private Double resolveCandidateExperienceYears(
            CandidateProfile candidate
    ) {
        Double explicit =
                validNonNegative(
                        candidate.getExperienceYears()
                );

        if (explicit != null) {
            return explicit;
        }

        if (isCurrentStudent(candidate)
                && hasNoWorkExperience(candidate)) {

            return 0.0d;
        }

        return null;
    }

    private boolean isCurrentStudent(
            CandidateProfile candidate
    ) {
        if (candidate.getEducations() == null) {
            return false;
        }

        for (CandidateProfile.Education education
                : candidate.getEducations()) {

            if (education != null
                    && Boolean.TRUE.equals(
                    education.current()
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean hasNoWorkExperience(
            CandidateProfile candidate
    ) {
        return candidate.getWorkExperiences() == null
                || candidate
                .getWorkExperiences()
                .isEmpty();
    }

    private String candidateIntentText(
            CandidateProfile candidate
    ) {
        StringBuilder builder =
                new StringBuilder();

        append(
                builder,
                candidate.getHeadline()
        );

        append(
                builder,
                candidate.getProfessionalSummary()
        );

        append(
                builder,
                candidate.getCareerObjective()
        );

        if (candidate.getTargetJobTitles() != null) {
            for (String title
                    : candidate.getTargetJobTitles()) {

                append(
                        builder,
                        title
                );
            }
        }

        return builder
                .toString()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private static void append(
            StringBuilder builder,
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return;
        }

        builder
                .append(' ')
                .append(value);
    }

    private static boolean containsAny(
            String text,
            String... signals
    ) {
        if (text == null
                || text.isBlank()) {

            return false;
        }

        for (String signal : signals) {
            if (text.contains(signal)) {
                return true;
            }
        }

        return false;
    }

    private Double levelScore(
            CandidateProfile.Seniority candidateLevel,
            SeniorityLevel jobLevel
    ) {
        if (candidateLevel == null
                || candidateLevel
                == CandidateProfile.Seniority.UNKNOWN
                || jobLevel == null
                || jobLevel == SeniorityLevel.UNKNOWN) {

            return null;
        }

        Integer candidateRank =
                rankByLevel.get(
                        candidateLevel.name()
                );

        Integer jobRank =
                rankByLevel.get(
                        jobLevel.name()
                );

        if (candidateRank == null
                || jobRank == null
                || candidateRank < 0
                || jobRank < 0) {

            return null;
        }

        int difference =
                Math.abs(
                        candidateRank - jobRank
                );

        return switch (difference) {
            case 0 -> 1.00d;
            case 1 -> 0.85d;
            case 2 -> 0.65d;
            case 3 -> 0.45d;
            case 4 -> 0.25d;
            default -> 0.10d;
        };
    }

    private Double experienceScore(
            Double candidateYears,
            Double jobMin,
            Double jobMax
    ) {
        if (candidateYears == null
                || !Double.isFinite(candidateYears)
                || candidateYears < 0.0d) {

            return null;
        }

        Double min =
                validNonNegative(jobMin);

        Double max =
                validNonNegative(jobMax);

        if (min == null && max == null) {
            return null;
        }

        if (min != null
                && candidateYears < min) {

            double gap =
                    min - candidateYears;

            return clamp01(
                    Math.max(
                            0.10d,
                            1.0d - gap * 0.25d
                    )
            );
        }

        if (max != null
                && candidateYears > max) {

            double gap =
                    candidateYears - max;

            return clamp01(
                    Math.max(
                            0.60d,
                            1.0d - gap * 0.08d
                    )
            );
        }

        return 1.0d;
    }

    private static Double validNonNegative(
            Double value
    ) {
        if (value == null
                || !Double.isFinite(value)
                || value < 0.0d) {

            return null;
        }

        return value;
    }

    private static Map<String, Integer>
    buildRankMap(
            List<SharedSeniorityTaxonomyProperties.LevelDefinition>
                    definitions
    ) {
        Map<String, Integer> result =
                new LinkedHashMap<>();

        if (definitions == null) {
            return result;
        }

        for (SharedSeniorityTaxonomyProperties.LevelDefinition definition
                : definitions) {

            if (definition == null
                    || definition.getLevel() == null
                    || definition.getRank() == null) {

                continue;
            }

            result.put(
                    definition
                            .getLevel()
                            .name(),
                    definition.getRank()
            );
        }

        return Map.copyOf(result);
    }

    private static double clamp01(
            double value
    ) {
        return Math.max(
                0.0d,
                Math.min(1.0d, value)
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