package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class JobTypeNormalizer {

    /**
     * Job type business rules được load từ job-types.yml
     * và compile regex một lần khi Spring tạo bean.
     */
    private final List<CompiledRule> rules;

    public JobTypeNormalizer(
            NormalizationTaxonomyProperties taxonomyProperties
    ) {
        this.rules = taxonomyProperties
                .getJobType()
                .getRules()
                .stream()
                .map(this::compileRule)
                .toList();
    }

    /**
     * Ưu tiên jobTypeText từ crawler.
     * Chỉ dùng title làm fallback.
     */
    public NormalizedJobType normalize(
            String jobTypeText,
            String title
    ) {
        NormalizedJobType fromJobTypeText =
                detect(jobTypeText);

        if (fromJobTypeText
                != NormalizedJobType.UNKNOWN) {
            return fromJobTypeText;
        }

        return detect(title);
    }

    private NormalizedJobType detect(
            String value
    ) {
        String folded =
                NormalizationTextSupport.fold(value);

        if (folded.isBlank()) {
            return NormalizedJobType.UNKNOWN;
        }

        /*
         * Schema.org hoặc crawler có thể trả:
         *
         * FULL_TIME
         * PART_TIME
         *
         * Chuyển "_" thành space để cùng match với rule YAML.
         */
        String normalizedValue = folded
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        /*
         * Priority chính là thứ tự rule trong job-types.yml.
         */
        for (CompiledRule rule : rules) {

            boolean matched =
                    rule.patterns()
                            .stream()
                            .anyMatch(
                                    pattern ->
                                            pattern
                                                    .matcher(normalizedValue)
                                                    .find()
                            );

            if (matched) {
                return rule.type();
            }
        }

        return NormalizedJobType.UNKNOWN;
    }

    private CompiledRule compileRule(
            NormalizationTaxonomyProperties.JobTypeRule rule
    ) {
        List<Pattern> patterns =
                rule.getPatterns()
                        .stream()
                        .filter(pattern ->
                                pattern != null
                                        && !pattern.isBlank()
                        )
                        .map(Pattern::compile)
                        .toList();

        return new CompiledRule(
                rule.getType(),
                patterns
        );
    }

    private record CompiledRule(
            NormalizedJobType type,
            List<Pattern> patterns
    ) {
    }
}