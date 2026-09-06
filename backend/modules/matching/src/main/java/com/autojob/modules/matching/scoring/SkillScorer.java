package com.autojob.modules.matching.scoring;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.jobnormalizer.config.SharedSkillTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.config.MatchingProperties;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SkillScorer {

    /*
     * Numeric placeholder khi professional skill signal
     * không đủ để đánh giá.
     *
     * known flag mới là source of truth.
     */
    private static final double UNKNOWN_SCORE =
            0.50d;

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{M}+");

    private static final Pattern NON_KEY =
            Pattern.compile("[^a-z0-9]+");

    private final Map<String, SkillMetadata>
            skillByAlias;

    /*
     * SECONDARY theo taxonomy ID.
     */
    private final Set<String>
            genericSkillIds;

    /*
     * SECONDARY theo taxonomy category.
     *
     * Hiện config dùng LANGUAGE.
     */
    private final Set<String>
            genericSkillCategories;

    private final MatchingProperties.SkillScoring
            config;

    public SkillScorer(
            SharedSkillTaxonomyProperties taxonomy,
            MatchingProperties properties
    ) {
        Objects.requireNonNull(
                taxonomy,
                "taxonomy must not be null"
        );

        Objects.requireNonNull(
                properties,
                "properties must not be null"
        );

        this.skillByAlias =
                buildAliasMap(
                        taxonomy.getItems()
                );

        this.config =
                Objects.requireNonNull(
                        properties.getSkillScoring(),
                        "skillScoring config must not be null"
                );

        this.genericSkillIds =
                config
                        .getGenericSkillIds()
                        .stream()
                        .filter(Objects::nonNull)
                        .map(
                                value ->
                                        value
                                                .trim()
                                                .toLowerCase(
                                                        Locale.ROOT
                                                )
                        )
                        .filter(
                                value ->
                                        !value.isBlank()
                        )
                        .collect(
                                Collectors.toUnmodifiableSet()
                        );

        this.genericSkillCategories =
                config
                        .getGenericSkillCategories()
                        .stream()
                        .filter(Objects::nonNull)
                        .map(
                                this::normalizeCategory
                        )
                        .filter(
                                value ->
                                        !value.isBlank()
                        )
                        .collect(
                                Collectors.toUnmodifiableSet()
                        );
    }

    public Result score(
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

        List<String> jobSkills =
                job.getSkills();

        /*
         * JD không có skill data.
         */
        if (jobSkills == null
                || jobSkills.isEmpty()) {

            return Result.unknown(
                    List.of(),
                    List.of()
            );
        }

        Map<String, Double> candidateSkills =
                candidateSkillConfidence(
                        candidate
                );

        List<String> matchedSkills =
                new ArrayList<>();

        /*
         * missingSkills chỉ chứa PRIMARY/domain skills.
         *
         * SECONDARY skill thiếu không phải professional gap.
         */
        List<String> missingSkills =
                new ArrayList<>();

        Set<String> seenJobSkills =
                new LinkedHashSet<>();

        int primarySkillCount =
                0;

        int secondarySkillCount =
                0;

        double matchedPrimaryConfidence =
                0.0d;

        double matchedSecondaryConfidence =
                0.0d;

        boolean matchedPrimarySkill =
                false;

        for (String rawJobSkill : jobSkills) {

            if (rawJobSkill == null
                    || rawJobSkill.isBlank()) {

                continue;
            }

            SkillResolution resolution =
                    resolve(
                            rawJobSkill
                    );

            if (resolution.key().isBlank()
                    || !seenJobSkills.add(
                    resolution.key()
            )) {

                continue;
            }

            boolean secondary =
                    isSecondary(
                            resolution.metadata()
                    );

            if (secondary) {

                secondarySkillCount++;

            } else {

                /*
                 * Mọi skill không bị classifier đánh dấu
                 * SECONDARY đều mặc định PRIMARY.
                 *
                 * Điều này rất quan trọng cho skill mới,
                 * nghề mới hoặc taxonomy chưa biết.
                 */
                primarySkillCount++;
            }

            double candidateConfidence =
                    candidateSkills.getOrDefault(
                            resolution.key(),
                            0.0d
                    );

            if (candidateConfidence > 0.0d) {

                matchedSkills.add(
                        rawJobSkill
                );

                if (secondary) {

                    matchedSecondaryConfidence +=
                            candidateConfidence;

                } else {

                    matchedPrimaryConfidence +=
                            candidateConfidence;

                    matchedPrimarySkill =
                            true;
                }

            } else {

                /*
                 * Chỉ PRIMARY skill mới được báo thiếu.
                 */
                if (!secondary) {

                    missingSkills.add(
                            rawJobSkill
                    );
                }
            }
        }

        /*
         * Không có skill hợp lệ sau normalize.
         */
        if (primarySkillCount == 0
                && secondarySkillCount == 0) {

            return Result.unknown(
                    matchedSkills,
                    missingSkills
            );
        }

        /*
         * =====================================================
         * JD chỉ có SECONDARY skills
         * =====================================================
         *
         * Ví dụ:
         *
         * Communication
         * Teamwork
         * English
         *
         * Đây không phải professional/domain evidence đủ mạnh.
         *
         * Skill component UNKNOWN.
         *
         * Semantic + các structured signals khác sẽ quyết định.
         */
        if (primarySkillCount == 0) {

            return Result.unknown(
                    matchedSkills,
                    missingSkills
            );
        }

        /*
         * =====================================================
         * PRIMARY score
         * =====================================================
         *
         * Chỉ denominator PRIMARY skill.
         *
         * Secondary skill thiếu không làm giảm primary coverage.
         */
        double primaryScore =
                clamp01(
                        matchedPrimaryConfidence
                                / primarySkillCount
                );

        /*
         * =====================================================
         * SECONDARY score
         * =====================================================
         */
        double secondaryScore =
                secondarySkillCount > 0
                        ? clamp01(
                        matchedSecondaryConfidence
                                / secondarySkillCount
                )
                        : 0.0d;

        /*
         * PRIMARY quyết định phần lớn skill score.
         *
         * SECONDARY chỉ bonus nhỏ.
         */
        double score =
                primaryScore
                        * config.getCoreWeight()
                        + secondaryScore
                        * config.getGenericWeight();

        score =
                clamp01(
                        score
                );

        /*
         * =====================================================
         * Candidate chỉ match SECONDARY
         * =====================================================
         *
         * JD có professional PRIMARY skills,
         * nhưng candidate chỉ match:
         *
         * English
         * Communication
         * Teamwork
         *
         * => không được coi là professional fit.
         *
         * Hard cap về genericOnlyCap.
         */
        if (!matchedPrimarySkill) {

            score =
                    Math.min(
                            score,
                            config.getGenericOnlyCap()
                    );
        }

        return Result.known(
                score,
                matchedSkills,
                missingSkills
        );
    }

    private Map<String, Double>
    candidateSkillConfidence(
            CandidateProfile candidate
    ) {
        Map<String, Double> result =
                new LinkedHashMap<>();

        /*
         * =====================================================
         * CandidateProfile.skills
         * =====================================================
         */
        if (candidate.getSkills() != null) {

            for (
                    CandidateProfile.Skill skill
                    : candidate.getSkills()
            ) {

                if (skill == null) {
                    continue;
                }

                double confidence =
                        confidenceFromEvidenceSources(
                                skill.evidenceSources()
                        );

                addSkill(
                        result,
                        skill.normalizedName(),
                        confidence
                );

                addSkill(
                        result,
                        skill.name(),
                        confidence
                );
            }
        }

        /*
         * =====================================================
         * Work experience
         * =====================================================
         */
        if (candidate.getWorkExperiences() != null) {

            for (
                    CandidateProfile.WorkExperience experience
                    : candidate.getWorkExperiences()
            ) {

                if (experience == null) {
                    continue;
                }

                addAll(
                        result,
                        experience.skills(),
                        config.getWorkExperienceConfidence()
                );

                addAll(
                        result,
                        experience.tools(),
                        config.getWorkExperienceConfidence()
                );

                addAll(
                        result,
                        experience.equipment(),
                        config.getWorkExperienceConfidence()
                );
            }
        }

        /*
         * =====================================================
         * Projects
         * =====================================================
         */
        if (candidate.getProjects() != null) {

            for (
                    CandidateProfile.ProjectExperience project
                    : candidate.getProjects()
            ) {

                if (project == null) {
                    continue;
                }

                addAll(
                        result,
                        project.skills(),
                        config.getProjectConfidence()
                );

                addAll(
                        result,
                        project.tools(),
                        config.getProjectConfidence()
                );

                addAll(
                        result,
                        project.equipment(),
                        config.getProjectConfidence()
                );
            }
        }

        return Map.copyOf(
                result
        );
    }

    private double confidenceFromEvidenceSources(
            List<String> evidenceSources
    ) {
        if (evidenceSources == null
                || evidenceSources.isEmpty()) {

            return config
                    .getUnknownEvidenceConfidence();
        }

        double best =
                0.0d;

        for (String source : evidenceSources) {

            if (source == null
                    || source.isBlank()) {

                continue;
            }

            String normalized =
                    source
                            .trim()
                            .toUpperCase(
                                    Locale.ROOT
                            );

            double confidence =
                    switch (normalized) {

                        case "SKILLS_SECTION" ->
                                config
                                        .getSkillsSectionConfidence();

                        case "WORK_EXPERIENCE" ->
                                config
                                        .getWorkExperienceConfidence();

                        case "PROJECTS" ->
                                config
                                        .getProjectConfidence();

                        case "PROFILE_TEXT" ->
                                config
                                        .getProfileTextConfidence();

                        case "SCOPED_TEXT" ->
                                config
                                        .getScopedTextConfidence();

                        default ->
                                config
                                        .getUnknownEvidenceConfidence();
                    };

            best =
                    Math.max(
                            best,
                            confidence
                    );
        }

        if (best <= 0.0d) {

            return config
                    .getUnknownEvidenceConfidence();
        }

        return clamp01(
                best
        );
    }

    private void addAll(
            Map<String, Double> target,
            List<String> values,
            double confidence
    ) {
        if (values == null) {
            return;
        }

        for (String value : values) {

            addSkill(
                    target,
                    value,
                    confidence
            );
        }
    }

    private void addSkill(
            Map<String, Double> target,
            String value,
            double confidence
    ) {
        SkillResolution resolution =
                resolve(
                        value
                );

        if (resolution.key().isBlank()) {
            return;
        }

        target.merge(
                resolution.key(),
                clamp01(
                        confidence
                ),
                Math::max
        );
    }

    private SkillResolution resolve(
            String value
    ) {
        String aliasKey =
                compact(
                        value
                );

        if (aliasKey.isBlank()) {

            return new SkillResolution(
                    "",
                    null
            );
        }

        SkillMetadata metadata =
                skillByAlias.get(
                        aliasKey
                );

        /*
         * Taxonomy chưa biết skill này.
         *
         * Giữ riêng raw key và mặc định PRIMARY.
         */
        if (metadata == null) {

            return new SkillResolution(
                    "raw:" + aliasKey,
                    null
            );
        }

        return new SkillResolution(
                "id:" + metadata.id(),
                metadata
        );
    }

    /*
     * =========================================================
     * PRIMARY / SECONDARY classifier
     * =========================================================
     *
     * SECONDARY nếu:
     *
     * 1. taxonomy ID nằm genericSkillIds
     *
     * HOẶC
     *
     * 2. taxonomy category nằm genericSkillCategories
     *
     * Nếu metadata=null:
     * => mặc định PRIMARY.
     */
    private boolean isSecondary(
            SkillMetadata metadata
    ) {
        if (metadata == null) {
            return false;
        }

        if (genericSkillIds.contains(
                metadata.id()
        )) {

            return true;
        }

        return metadata.category() != null
                && genericSkillCategories.contains(
                metadata.category()
        );
    }

    private Map<String, SkillMetadata>
    buildAliasMap(
            List<SharedSkillTaxonomyProperties.SkillDefinition>
                    definitions
    ) {
        if (definitions == null) {
            return Map.of();
        }

        Map<String, SkillMetadata> result =
                new LinkedHashMap<>();

        for (
                SharedSkillTaxonomyProperties.SkillDefinition definition
                : definitions
        ) {

            if (definition == null
                    || definition.getId() == null
                    || definition.getId().isBlank()) {

                continue;
            }

            SkillMetadata metadata =
                    new SkillMetadata(
                            definition
                                    .getId()
                                    .trim()
                                    .toLowerCase(
                                            Locale.ROOT
                                    ),

                            normalizeCategory(
                                    definition.getCategory()
                            )
                    );

            registerAlias(
                    result,
                    definition.getId(),
                    metadata
            );

            registerAlias(
                    result,
                    definition.getCanonical(),
                    metadata
            );

            if (definition.getAliases() != null) {

                for (
                        String alias
                        : definition.getAliases()
                ) {

                    registerAlias(
                            result,
                            alias,
                            metadata
                    );
                }
            }
        }

        return Map.copyOf(
                result
        );
    }

    private void registerAlias(
            Map<String, SkillMetadata> target,
            String value,
            SkillMetadata metadata
    ) {
        String key =
                compact(
                        value
                );

        if (!key.isBlank()) {

            target.putIfAbsent(
                    key,
                    metadata
            );
        }
    }

    private String normalizeCategory(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return "";
        }

        return value
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }

    private String compact(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return "";
        }

        String decomposed =
                Normalizer.normalize(
                        value,
                        Normalizer.Form.NFD
                );

        String folded =
                DIACRITICS
                        .matcher(
                                decomposed
                        )
                        .replaceAll("")
                        .replace(
                                'đ',
                                'd'
                        )
                        .replace(
                                'Đ',
                                'D'
                        )
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .trim();

        return NON_KEY
                .matcher(
                        folded
                )
                .replaceAll("");
    }

    private double clamp01(
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
            boolean known,
            List<String> matchedSkills,
            List<String> missingSkills
    ) {

        public Result {

            if (!Double.isFinite(
                    score
            )
                    || score < 0.0d
                    || score > 1.0d) {

                throw new IllegalArgumentException(
                        "score must be between 0.0 and 1.0"
                );
            }

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

        public static Result known(
                double score,
                List<String> matchedSkills,
                List<String> missingSkills
        ) {
            return new Result(
                    score,
                    true,
                    matchedSkills,
                    missingSkills
            );
        }

        public static Result unknown(
                List<String> matchedSkills,
                List<String> missingSkills
        ) {
            return new Result(
                    UNKNOWN_SCORE,
                    false,
                    matchedSkills,
                    missingSkills
            );
        }
    }

    private record SkillMetadata(
            String id,
            String category
    ) {
    }

    private record SkillResolution(
            String key,
            SkillMetadata metadata
    ) {
    }
}