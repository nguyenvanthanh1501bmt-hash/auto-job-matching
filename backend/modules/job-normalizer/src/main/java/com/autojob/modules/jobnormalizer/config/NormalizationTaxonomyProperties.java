package com.autojob.modules.jobnormalizer.config;

import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
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
@ConfigurationProperties(
        prefix = "autojob.normalization.taxonomy"
)
public class NormalizationTaxonomyProperties {

    /*
     * Shared taxonomies:
     *
     * Skills:
     *   SharedSkillTaxonomyProperties
     *
     * Locations:
     *   SharedLocationTaxonomyProperties
     *
     * Seniority:
     *   SharedSeniorityTaxonomyProperties
     *
     * This class contains only taxonomy that
     * remains specific to Job Normalizer.
     */

    @Valid
    @NotNull
    private JobType jobType =
            new JobType();

    @Valid
    @NotNull
    private Salary salary =
            new Salary();

    @Valid
    @NotNull
    private Experience experience =
            new Experience();

    @Valid
    @NotNull
    private DateRules date =
            new DateRules();

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

    @Getter
    @Setter
    public static class Salary {

        @Valid
        @NotEmpty
        private List<SalaryMultiplierRule> multipliers =
                new ArrayList<>();

        @Valid
        @NotEmpty
        private List<SalaryCurrencyRule> currencies =
                new ArrayList<>();

        private Set<String> negotiablePhrases =
                new LinkedHashSet<>();

        private Set<String> rangeWords =
                new LinkedHashSet<>();

        private Set<String> upperBoundPhrases =
                new LinkedHashSet<>();

        private Set<String> lowerBoundPhrases =
                new LinkedHashSet<>();

        @Min(1)
        private long sharedMultiplierMin =
                1_000_000L;

        @Min(1)
        private long sharedMultiplierMaxUnscaledValue =
                100_000L;
    }

    @Getter
    @Setter
    public static class SalaryMultiplierRule {

        @Min(1)
        private long multiplier;

        @NotEmpty
        private List<@NotBlank String> aliases =
                new ArrayList<>();
    }

    @Getter
    @Setter
    public static class SalaryCurrencyRule {

        @NotBlank
        private String code;

        private List<@NotBlank String> originalMarkers =
                new ArrayList<>();

        private List<@NotBlank String> foldedPhrases =
                new ArrayList<>();

        private List<@NotBlank String> inferredUnitAliases =
                new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Experience {

        @Valid
        @NotEmpty
        private List<ExperienceUnitRule> units =
                new ArrayList<>();

        private Set<String> noExperiencePhrases =
                new LinkedHashSet<>();

        private Set<String> rangeWords =
                new LinkedHashSet<>();

        private Set<String> upperBoundPhrases =
                new LinkedHashSet<>();

        private Set<String> lowerBoundPhrases =
                new LinkedHashSet<>();

        /**
         * Chỉ được dùng làm fallback khi experienceText
         * explicit bị thiếu hoặc không parse được.
         *
         * Không parse số từ toàn bộ requirements.
         */
        private Set<String> contextPhrases =
                new LinkedHashSet<>();

        /**
         * Số ký tự giữ lại mỗi bên quanh context phrase.
         *
         * Ví dụ:
         *
         * "Kinh nghiệm: từ 2 năm trở lên..."
         *
         * chỉ parse vùng gần "kinh nghiệm",
         * không parse các con số ở cuối JD.
         */
        @Min(20)
        private int contextWindowChars = 120;
    }

    @Getter
    @Setter
    public static class ExperienceUnitRule {

        @NotNull
        private ExperienceUnit unit;

        @NotEmpty
        private List<@NotBlank String> aliases =
                new ArrayList<>();
    }

    public enum ExperienceUnit {
        YEAR,
        MONTH
    }

    @Getter
    @Setter
    public static class DateRules {

        @NotEmpty
        private Set<@NotBlank String> todayPhrases =
                new LinkedHashSet<>();

        @NotEmpty
        private Set<@NotBlank String> yesterdayPhrases =
                new LinkedHashSet<>();

        @NotEmpty
        private Set<@NotBlank String> tomorrowPhrases =
                new LinkedHashSet<>();

        @Valid
        @NotEmpty
        private List<DateUnitRule> units =
                new ArrayList<>();

        @NotEmpty
        private Set<@NotBlank String> agoWords =
                new LinkedHashSet<>();

        @NotEmpty
        private Set<@NotBlank String> deadlineInPrefixes =
                new LinkedHashSet<>();

        @NotEmpty
        private Set<@NotBlank String> deadlineLeftSuffixes =
                new LinkedHashSet<>();

        @NotEmpty
        private Set<@NotBlank String> deadlineFutureSuffixes =
                new LinkedHashSet<>();

        @NotEmpty
        private Set<@NotBlank String> deadlineRemainingPrefixes =
                new LinkedHashSet<>();
    }

    @Getter
    @Setter
    public static class DateUnitRule {

        @NotNull
        private DateUnit unit;

        @NotEmpty
        private List<@NotBlank String> aliases =
                new ArrayList<>();
    }

    public enum DateUnit {
        DAY,
        WEEK,
        MONTH,
        HOUR,
        MINUTE
    }
}