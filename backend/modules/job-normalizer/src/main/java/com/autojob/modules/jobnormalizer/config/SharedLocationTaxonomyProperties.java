package com.autojob.modules.jobnormalizer.config;

import jakarta.validation.Valid;
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
        prefix = "autojob.taxonomy.shared.locations"
)
public class SharedLocationTaxonomyProperties {

    @NotNull
    private Set<@NotBlank String> ignoredValues =
            new LinkedHashSet<>();

    /**
     * Geographic aliases that are deliberately
     * not mapped because they are ambiguous.
     *
     * Example:
     *
     * DN -> Da Nang or Dong Nai.
     */
    @NotNull
    private Set<@NotBlank String> ambiguousAliases =
            new LinkedHashSet<>();

    /**
     * Text that describes work arrangement rather
     * than geography.
     *
     * Examples:
     *
     * Remote
     * WFH
     * Work from home
     *
     * LocationNormalizer must exclude these from
     * NormalizedJob.locations.
     *
     * Raw locationText remains available for a
     * dedicated work-mode normalizer later.
     */
    @NotNull
    private Set<@NotBlank String> nonGeographicAliases =
            new LinkedHashSet<>();

    @Valid
    @NotEmpty
    private List<LocationDefinition> items =
            new ArrayList<>();

    @Getter
    @Setter
    public static class LocationDefinition {

        @NotBlank
        @Pattern(
                regexp = "[a-z0-9]+(?:-[a-z0-9]+)*"
        )
        private String id;

        @NotBlank
        private String canonical;

        @NotBlank
        @Pattern(
                regexp = "CITY|REGION|COUNTRY"
        )
        private String kind;

        private List<@NotBlank String> aliases =
                new ArrayList<>();
    }
}