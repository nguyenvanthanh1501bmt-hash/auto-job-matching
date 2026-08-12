package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class SeniorityNormalizer {

    /**
     * Rule regex được compile một lần khi Spring tạo bean,
     * không compile lại mỗi lần normalize job.
     */
    private final List<CompiledRule> rules;

    /**
     * Các threshold experience lấy từ seniority.yml.
     */
    private final NormalizationTaxonomyProperties.ExperienceThresholds
            experienceThresholds;

    public SeniorityNormalizer(
            NormalizationTaxonomyProperties taxonomyProperties
    ) {
        NormalizationTaxonomyProperties.Seniority config =
                taxonomyProperties.getSeniority();

        this.rules = config
                .getRules()
                .stream()
                .map(this::compileRule)
                .toList();

        this.experienceThresholds =
                config.getExperience();
    }

    /**
     * Thứ tự ưu tiên:
     *
     * 1. seniorityText
     * 2. title
     * 3. experience
     * 4. UNKNOWN
     */
    public SeniorityLevel normalize(
            String seniorityText,
            String title,
            ExperienceNormalizationResult experience
    ) {
        SeniorityLevel fromSeniorityText =
                detectExplicitLevel(
                        seniorityText
                );

        if (fromSeniorityText
                != SeniorityLevel.UNKNOWN) {
            return fromSeniorityText;
        }

        SeniorityLevel fromTitle =
                detectExplicitLevel(title);

        if (fromTitle
                != SeniorityLevel.UNKNOWN) {
            return fromTitle;
        }

        return inferFromExperience(experience);
    }

    private SeniorityLevel detectExplicitLevel(
            String value
    ) {
        String folded =
                NormalizationTextSupport.fold(value);

        if (folded.isBlank()) {
            return SeniorityLevel.UNKNOWN;
        }

        /*
         * Priority nằm trong thứ tự rules của seniority.yml.
         *
         * Ví dụ:
         *
         * DIRECTOR
         * MANAGER
         * LEAD
         * SENIOR
         * ...
         *
         * Vì vậy title "Senior Sales Manager"
         * vẫn ra MANAGER nếu MANAGER đứng trước SENIOR.
         */
        for (CompiledRule rule : rules) {

            if (!matchesAny(
                    rule.patterns(),
                    folded
            )) {
                continue;
            }

            boolean excluded =
                    matchesAny(
                            rule.excludePatterns(),
                            folded
                    );

            if (!excluded) {
                return rule.level();
            }

            /*
             * Nếu match exclude nhưng đồng thời match allow,
             * rule vẫn được chấp nhận.
             *
             * Dùng cho trường hợp như LEAD:
             *
             * "Lead Generation"         -> không phải LEAD
             * "Lead Generation Leader"  -> LEAD
             */
            boolean explicitlyAllowed =
                    matchesAny(
                            rule.allowPatterns(),
                            folded
                    );

            if (explicitlyAllowed) {
                return rule.level();
            }
        }

        return SeniorityLevel.UNKNOWN;
    }

    private SeniorityLevel inferFromExperience(
            ExperienceNormalizationResult experience
    ) {
        if (experience == null
                || !experience.known()) {
            return SeniorityLevel.UNKNOWN;
        }

        Double min = experience.min();
        Double max = experience.max();

        /*
         * Defensive handling nếu upstream đưa experience âm.
         */
        if (min != null && min < 0) {
            min = null;
        }

        if (max != null && max < 0) {
            max = null;
        }

        if (min == null && max == null) {
            return SeniorityLevel.UNKNOWN;
        }

        /*
         * Giữ nguyên behavior cũ:
         * ưu tiên min nếu có.
         */
        double effectiveYears =
                min != null
                        ? min
                        : max;

        if (effectiveYears
                < experienceThresholds.getFresherUnder()) {
            return SeniorityLevel.FRESHER;
        }

        if (effectiveYears
                < experienceThresholds.getJuniorUnder()) {
            return SeniorityLevel.JUNIOR;
        }

        if (effectiveYears
                < experienceThresholds.getMidUnder()) {
            return SeniorityLevel.MID;
        }

        /*
         * Experience fallback tuyệt đối không suy ra
         * LEAD / MANAGER / DIRECTOR.
         */
        return SeniorityLevel.SENIOR;
    }

    private CompiledRule compileRule(
            NormalizationTaxonomyProperties.SeniorityRule rule
    ) {
        return new CompiledRule(
                rule.getLevel(),
                compilePatterns(
                        rule.getPatterns()
                ),
                compilePatterns(
                        rule.getExcludePatterns()
                ),
                compilePatterns(
                        rule.getAllowPatterns()
                )
        );
    }

    private List<Pattern> compilePatterns(
            List<String> configuredPatterns
    ) {
        if (configuredPatterns == null
                || configuredPatterns.isEmpty()) {
            return List.of();
        }

        return configuredPatterns
                .stream()
                .filter(pattern ->
                        pattern != null
                                && !pattern.isBlank()
                )
                .map(Pattern::compile)
                .toList();
    }

    private boolean matchesAny(
            List<Pattern> patterns,
            String value
    ) {
        if (patterns == null
                || patterns.isEmpty()) {
            return false;
        }

        return patterns
                .stream()
                .anyMatch(
                        pattern ->
                                pattern
                                        .matcher(value)
                                        .find()
                );
    }

    private record CompiledRule(
            SeniorityLevel level,
            List<Pattern> patterns,
            List<Pattern> excludePatterns,
            List<Pattern> allowPatterns
    ) {
    }
}