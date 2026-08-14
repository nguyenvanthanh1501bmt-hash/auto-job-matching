package com.autojob.modules.jobnormalizer.config;

import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared seniority taxonomy used by both
 * Job Normalizer and CV Parser.
 *
 * Source:
 *
 * configs/taxonomy/shared/seniority.yml
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(
        prefix = "autojob.taxonomy.shared.seniority"
)
public class SharedSeniorityTaxonomyProperties {

    @Valid
    @NotNull
    private ExperienceThresholds experience =
            new ExperienceThresholds();

    @Valid
    @NotEmpty
    private List<LevelDefinition> levels =
            new ArrayList<>();

    @Getter
    @Setter
    public static class ExperienceThresholds {

        @DecimalMin("0.0")
        private double entryLevelUnder = 0.5;

        @DecimalMin("0.0")
        private double juniorUnder = 2.0;

        @DecimalMin("0.0")
        private double midUnder = 5.0;
    }

    @Getter
    @Setter
    public static class LevelDefinition {

        @NotNull
        private SeniorityLevel level;

        /**
         * Explicit taxonomy rank.
         *
         * Never use enum.ordinal() as semantic rank.
         */
        @NotNull
        private Integer rank;

        /**
         * UNKNOWN intentionally has no patterns.
         */
        @NotNull
        private List<@NotBlank String> patterns =
                new ArrayList<>();

        @NotNull
        private List<@NotBlank String> excludePatterns =
                new ArrayList<>();

        @NotNull
        private List<@NotBlank String> allowPatterns =
                new ArrayList<>();
    }
}