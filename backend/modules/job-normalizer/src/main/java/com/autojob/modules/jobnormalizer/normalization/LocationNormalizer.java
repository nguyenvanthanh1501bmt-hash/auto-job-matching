package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.SharedLocationTaxonomyProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class LocationNormalizer {

    private static final Pattern LOCATION_SEPARATOR =
            Pattern.compile(
                    "(?:\\s*[,;|/]\\s*|\\R+)"
            );

    private final TextNormalizer textNormalizer;

    private final Map<String, String> locationAliases;

    private final Set<String> ignoredValues;

    private final Set<String> ambiguousAliases;

    private final Set<String> nonGeographicAliases;

    /**
     * LocationNormalizer chỉ còn một constructor.
     *
     * Source of truth:
     *
     * configs/taxonomy/shared/locations.yml
     */
    public LocationNormalizer(
            TextNormalizer textNormalizer,
            SharedLocationTaxonomyProperties taxonomyProperties
    ) {
        this.textNormalizer =
                textNormalizer;

        this.locationAliases =
                buildAliases(
                        taxonomyProperties
                                .getItems()
                );

        this.ignoredValues =
                foldSet(
                        taxonomyProperties
                                .getIgnoredValues()
                );

        this.ambiguousAliases =
                compactSet(
                        taxonomyProperties
                                .getAmbiguousAliases()
                );

        this.nonGeographicAliases =
                compactSet(
                        taxonomyProperties
                                .getNonGeographicAliases()
                );
    }

    public List<String> normalize(
            String locationText
    ) {
        String cleaned =
                textNormalizer
                        .normalizeMultiline(
                                locationText
                        );

        if (cleaned == null) {
            return List.of();
        }

        List<String> normalizedLocations =
                new ArrayList<>();

        Set<String> seenLocations =
                new LinkedHashSet<>();

        String[] locationParts =
                LOCATION_SEPARATOR.split(
                        cleaned
                );

        for (String locationPart
                : locationParts) {

            String canonicalLocation =
                    canonicalize(
                            locationPart
                    );

            if (canonicalLocation == null) {
                continue;
            }

            String deduplicationKey =
                    NormalizationTextSupport.fold(
                            canonicalLocation
                    );

            if (
                    seenLocations.add(
                            deduplicationKey
                    )
            ) {
                normalizedLocations.add(
                        canonicalLocation
                );
            }
        }

        return List.copyOf(
                normalizedLocations
        );
    }

    private String canonicalize(
            String value
    ) {
        String cleaned =
                textNormalizer
                        .normalizeInline(
                                value
                        );

        if (cleaned == null) {
            return null;
        }

        String compactKey =
                NormalizationTextSupport
                        .compactKey(
                                cleaned
                        );

        /*
         * Remote/WFH/... describe work mode,
         * not geography.
         */
        if (
                nonGeographicAliases.contains(
                        compactKey
                )
        ) {
            return null;
        }

        /*
         * Never guess an ambiguous abbreviation.
         *
         * Example:
         *
         * DN = Da Nang OR Dong Nai.
         */
        if (
                ambiguousAliases.contains(
                        compactKey
                )
        ) {
            return cleaned;
        }

        String mappedLocation =
                locationAliases.get(
                        compactKey
                );

        if (mappedLocation != null) {
            return mappedLocation;
        }

        String folded =
                NormalizationTextSupport.fold(
                        cleaned
                );

        if (
                folded.isBlank()
                        || ignoredValues.contains(
                        folded
                )
        ) {
            return null;
        }

        /*
         * Unknown geography is preserved instead of
         * being incorrectly guessed.
         */
        return cleaned;
    }

    private static Map<String, String> buildAliases(
            List<SharedLocationTaxonomyProperties.LocationDefinition>
                    definitions
    ) {
        if (
                definitions == null
                        || definitions.isEmpty()
        ) {
            return Map.of();
        }

        Map<String, String> aliases =
                new LinkedHashMap<>();

        for (
                SharedLocationTaxonomyProperties.LocationDefinition
                        definition
                : definitions
        ) {
            if (definition == null) {
                continue;
            }

            String canonical =
                    definition.getCanonical();

            if (
                    canonical == null
                            || canonical.isBlank()
            ) {
                continue;
            }

            register(
                    aliases,
                    canonical,
                    canonical
            );

            List<String> values =
                    definition.getAliases();

            if (values == null) {
                continue;
            }

            for (String value : values) {
                register(
                        aliases,
                        canonical,
                        value
                );
            }
        }

        return Map.copyOf(
                aliases
        );
    }

    private static void register(
            Map<String, String> aliases,
            String canonical,
            String value
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            return;
        }

        String compactKey =
                NormalizationTextSupport
                        .compactKey(
                                value
                        );

        if (compactKey.isBlank()) {
            return;
        }

        String existing =
                aliases.putIfAbsent(
                        compactKey,
                        canonical
                );

        if (
                existing != null
                        && !existing.equals(
                        canonical
                )
        ) {
            throw new IllegalStateException(
                    "Shared location alias collision: "
                            + "alias="
                            + value
                            + ", firstCanonical="
                            + existing
                            + ", secondCanonical="
                            + canonical
            );
        }
    }

    private static Set<String> foldSet(
            Set<String> values
    ) {
        if (
                values == null
                        || values.isEmpty()
        ) {
            return Set.of();
        }

        Set<String> folded =
                new LinkedHashSet<>();

        for (String value : values) {
            if (value == null) {
                continue;
            }

            String normalized =
                    NormalizationTextSupport.fold(
                            value
                    );

            if (!normalized.isBlank()) {
                folded.add(
                        normalized
                );
            }
        }

        return Set.copyOf(
                folded
        );
    }

    private static Set<String> compactSet(
            Set<String> values
    ) {
        if (
                values == null
                        || values.isEmpty()
        ) {
            return Set.of();
        }

        Set<String> compacted =
                new LinkedHashSet<>();

        for (String value : values) {
            if (value == null) {
                continue;
            }

            String key =
                    NormalizationTextSupport
                            .compactKey(
                                    value
                            );

            if (!key.isBlank()) {
                compacted.add(
                        key
                );
            }
        }

        return Set.copyOf(
                compacted
        );
    }
}