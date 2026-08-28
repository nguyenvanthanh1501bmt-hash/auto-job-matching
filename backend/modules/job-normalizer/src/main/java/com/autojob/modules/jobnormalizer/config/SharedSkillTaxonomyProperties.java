package com.autojob.modules.jobnormalizer.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        prefix = "autojob.taxonomy.shared.skills"
)
public class SharedSkillTaxonomyProperties {

    @Min(1)
    private int richRawSkillCount = 2;

    @NotNull
    private Set<String> ambiguousProseAliases =
            new LinkedHashSet<>();

    @NotNull
    private Set<String> safeShortProseAliases =
            new LinkedHashSet<>();

    /**
     * Whole-label từ structured field đã biết là job category/industry.
     * Chỉ dùng để làm sạch raw structured skills; prose extraction vẫn
     * được phép nhận diện skill thật có cùng từ khoá trong nội dung JD.
     */
    @NotNull
    private Set<String> ignoredStructuredLabels =
            new LinkedHashSet<>();

    @Valid
    @NotEmpty
    private List<SkillDefinition> items =
            new ArrayList<>();

    @Getter
    @Setter
    public static class SkillDefinition {

        /**
         * Stable machine-readable identity shared by:
         *
         * Job Normalizer
         * CV Parser
         * Matching
         *
         * Ví dụ:
         * aws
         * tax-accounting
         * spring-boot
         */
        @NotBlank
        @Pattern(
                regexp = "[a-z0-9]+(?:-[a-z0-9]+)*"
        )
        private String id;

        /**
         * Human-readable canonical label.
         *
         * Trong migration hiện tại Job Normalizer
         * vẫn trả canonical này trong NormalizedJob.skills
         * để không phá API/data contract cũ.
         */
        @NotBlank
        private String canonical;

        /**
         * Category dùng chung.
         *
         * Giữ String ở Java Job Normalizer để module này
         * không phụ thuộc enum của CV parser.
         */
        @NotBlank
        private String category;

        private List<@NotBlank String> aliases =
                new ArrayList<>();
    }
}