package com.autojob.modules.jobnormalizer.config;

import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "autojob.normalization.taxonomy")
public class NormalizationTaxonomyProperties {

    @Valid
    @NotNull
    private Skill skill = new Skill();

    @Valid
    @NotNull
    private Location location = new Location();

    @Valid
    @NotNull
    private Seniority seniority = new Seniority();

    @Valid
    @NotNull
    private JobType jobType = new JobType();

    @Getter
    @Setter
    public static class Skill {

        @Min(1)
        private int richRawSkillCount = 2;

        private Set<String> ambiguousProseAliases =
                new LinkedHashSet<>();

        private Set<String> safeShortProseAliases =
                new LinkedHashSet<>();

        /**
         * Không dùng Map<canonical, aliases>.
         *
         * Spring ConfigurationProperties có thể sanitize Map key
         * chứa space, Unicode hoặc ký tự đặc biệt.
         *
         * Ví dụ:
         * "Spring Boot"   -> "SpringBoot"
         * "Hồ Chí Minh"  -> "HChMinh"
         *
         * Canonical được lưu thành field value để giữ nguyên text.
         */
        @Valid
        @NotEmpty
        private List<CanonicalAlias> aliases =
                new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Location {

        private Set<String> ignoredValues =
                new LinkedHashSet<>();

        @Valid
        @NotEmpty
        private List<CanonicalAlias> aliases =
                new ArrayList<>();
    }

    @Getter
    @Setter
    public static class CanonicalAlias {

        @NotBlank
        private String canonical;

        private List<String> aliases =
                new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Seniority {

        @Valid
        @NotEmpty
        private List<SeniorityRule> rules =
                new ArrayList<>();

        @Valid
        @NotNull
        private ExperienceThresholds experience =
                new ExperienceThresholds();
    }

    @Getter
    @Setter
    public static class SeniorityRule {

        @NotNull
        private SeniorityLevel level;

        @NotEmpty
        private List<@NotBlank String> patterns =
                new ArrayList<>();

        private List<@NotBlank String> excludePatterns =
                new ArrayList<>();

        private List<@NotBlank String> allowPatterns =
                new ArrayList<>();
    }

    @Getter
    @Setter
    public static class ExperienceThresholds {

        @DecimalMin("0.0")
        private double fresherUnder = 1.0;

        @DecimalMin("0.0")
        private double juniorUnder = 2.0;

        @DecimalMin("0.0")
        private double midUnder = 5.0;
    }

    @Getter
    @Setter
    public static class JobType {

        @Valid
        @NotEmpty
        private List<JobTypeRule> rules =
                new ArrayList<>();
    }

    @Getter
    @Setter
    public static class JobTypeRule {

        @NotNull
        private NormalizedJobType type;

        @NotEmpty
        private List<@NotBlank String> patterns =
                new ArrayList<>();
    }
}