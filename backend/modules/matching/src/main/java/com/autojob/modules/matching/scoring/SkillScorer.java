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

    private static final double NO_JOB_SKILLS_SCORE =
            0.50d;

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{M}+");

    private static final Pattern NON_KEY =
            Pattern.compile("[^a-z0-9]+");

    private final Map<String, SkillMetadata>
            skillByAlias;

    private final Set<String>
            genericSkillIds;

    private final MatchingProperties.SkillScoring
            config;

    public SkillScorer(
            SharedSkillTaxonomyProperties taxonomy,
            MatchingProperties properties
    ) {
        this.skillByAlias =
                buildAliasMap(
                        taxonomy.getItems()
                );

        this.config =
                properties.getSkillScoring();

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

        if (jobSkills == null
                || jobSkills.isEmpty()) {

            return new Result(
                    NO_JOB_SKILLS_SCORE,
                    List.of(),
                    List.of()
            );
        }

        /*
         * key -> confidence [0..1]
         */
        Map<String, Double> candidateSkills =
                candidateSkillConfidence(
                        candidate
                );

        List<String> matchedSkills =
                new ArrayList<>();

        List<String> missingSkills =
                new ArrayList<>();

        Set<String> seenJobSkills =
                new LinkedHashSet<>();

        double totalWeight =
                0.0d;

        double matchedWeight =
                0.0d;

        boolean hasCoreSkill =
                false;

        boolean matchedCoreSkill =
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

            boolean generic =
                    isGeneric(
                            resolution.metadata()
                    );

            double jobSkillWeight =
                    generic
                            ? config.getGenericWeight()
                            : config.getCoreWeight();

            totalWeight +=
                    jobSkillWeight;

            if (!generic) {
                hasCoreSkill = true;
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

                /*
                 * Match strength =
                 *
                 * job importance
                 * ×
                 * confidence that candidate really owns skill
                 */
                matchedWeight +=
                        jobSkillWeight
                                * candidateConfidence;

                if (!generic) {
                    matchedCoreSkill = true;
                }

            } else {

                missingSkills.add(
                        rawJobSkill
                );
            }
        }

        if (totalWeight <= 0.0d) {

            return new Result(
                    NO_JOB_SKILLS_SCORE,
                    List.copyOf(
                            matchedSkills
                    ),
                    List.copyOf(
                            missingSkills
                    )
            );
        }

        double score =
                clamp01(
                        matchedWeight
                                / totalWeight
                );

        /*
         * Nếu job có core skills nhưng candidate
         * chỉ match generic transferable skills:
         *
         * Communication
         * Teamwork
         * Problem Solving
         *
         * thì không được xem là strong fit.
         */
        if (hasCoreSkill
                && !matchedCoreSkill) {

            score =
                    Math.min(
                            score,
                            config.getGenericOnlyCap()
                    );
        }

        return new Result(
                score,
                List.copyOf(
                        matchedSkills
                ),
                List.copyOf(
                        missingSkills
                )
        );
    }

    /**
     * Build confidence map của candidate.
     *
     * Nhiều evidence cho cùng skill:
     *
     * lấy confidence mạnh nhất.
     *
     * Ví dụ React:
     *
     * SKILLS_SECTION = 1.0
     * PROJECTS       = 0.65
     *
     * => React confidence = 1.0
     *
     * Artificial Intelligence:
     *
     * PROJECTS only
     *
     * => confidence = 0.65
     */
    private Map<String, Double>
    candidateSkillConfidence(
            CandidateProfile candidate
    ) {
        Map<String, Double> result =
                new LinkedHashMap<>();

        /*
         * -------------------------------------------------
         * Structured CandidateProfile.skills
         * -------------------------------------------------
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
         * -------------------------------------------------
         * WorkExperience embedded skill/tool evidence
         * -------------------------------------------------
         *
         * Đây là direct professional evidence.
         */
        if (
                candidate.getWorkExperiences()
                        != null
        ) {

            for (
                    CandidateProfile.WorkExperience
                            experience
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
         * -------------------------------------------------
         * Project evidence
         * -------------------------------------------------
         *
         * Project skills rất hữu ích cho fresher/student,
         * nhưng confidence thấp hơn explicit skill section
         * để tránh một concept xuất hiện trong project
         * trở thành "expert skill".
         */
        if (candidate.getProjects() != null) {

            for (
                    CandidateProfile.ProjectExperience
                            project
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

        return clamp01(best);
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

        double safeConfidence =
                clamp01(
                        confidence
                );

        target.merge(
                resolution.key(),
                safeConfidence,
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

    private boolean isGeneric(
            SkillMetadata metadata
    ) {
        return metadata != null
                && genericSkillIds.contains(
                metadata.id()
        );
    }

    private Map<String, SkillMetadata>
    buildAliasMap(
            List<SharedSkillTaxonomyProperties.SkillDefinition>
                    definitions
    ) {
        Map<String, SkillMetadata> result =
                new LinkedHashMap<>();

        if (definitions == null) {
            return Map.of();
        }

        for (
                SharedSkillTaxonomyProperties.SkillDefinition
                        definition
                : definitions
        ) {

            if (definition == null
                    || definition.getId() == null
                    || definition
                    .getId()
                    .isBlank()) {

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
                            definition.getCanonical(),
                            definition.getCategory()
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

        return Map.copyOf(result);
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
                        .matcher(decomposed)
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
                .matcher(folded)
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
            List<String> matchedSkills,
            List<String> missingSkills
    ) {
    }

    private record SkillMetadata(
            String id,
            String canonical,
            String category
    ) {
    }

    private record SkillResolution(
            String key,
            SkillMetadata metadata
    ) {
    }
}