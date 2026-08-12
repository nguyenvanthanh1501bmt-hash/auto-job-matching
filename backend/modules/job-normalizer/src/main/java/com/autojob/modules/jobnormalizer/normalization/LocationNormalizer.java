package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
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

    public LocationNormalizer(
            TextNormalizer textNormalizer,
            NormalizationTaxonomyProperties taxonomyProperties
    ) {
        this.textNormalizer = textNormalizer;

        this.locationAliases = buildAliases(
                taxonomyProperties
                        .getLocation()
                        .getAliases()
        );

        this.ignoredValues = foldSet(
                taxonomyProperties
                        .getLocation()
                        .getIgnoredValues()
        );
    }

    public List<String> normalize(String locationText) {
        String cleaned =
                textNormalizer.normalizeMultiline(
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
                LOCATION_SEPARATOR.split(cleaned);

        for (String locationPart : locationParts) {
            String canonicalLocation =
                    canonicalize(locationPart);

            if (canonicalLocation == null) {
                continue;
            }

            String deduplicationKey =
                    NormalizationTextSupport.fold(
                            canonicalLocation
                    );

            if (seenLocations.add(deduplicationKey)) {
                normalizedLocations.add(
                        canonicalLocation
                );
            }
        }

        return List.copyOf(normalizedLocations);
    }

    private String canonicalize(String value) {
        String cleaned =
                textNormalizer.normalizeInline(value);

        if (cleaned == null) {
            return null;
        }

        String compactKey =
                NormalizationTextSupport.compactKey(
                        cleaned
                );

        String mappedLocation =
                locationAliases.get(compactKey);

        if (mappedLocation != null) {
            return mappedLocation;
        }

        String folded =
                NormalizationTextSupport.fold(cleaned);

        if (folded.isBlank()
                || ignoredValues.contains(folded)) {
            return null;
        }

        /*
         * Location chưa có taxonomy vẫn được giữ nguyên.
         */
        return cleaned;
    }

    private static Map<String, String> buildAliases(
            List<NormalizationTaxonomyProperties.CanonicalAlias>
                    configuredAliases
    ) {
        if (configuredAliases == null
                || configuredAliases.isEmpty()) {
            return Map.of();
        }

        Map<String, String> aliases =
                new LinkedHashMap<>();

        for (NormalizationTaxonomyProperties.CanonicalAlias definition
                : configuredAliases) {

            if (definition == null) {
                continue;
            }

            String canonical =
                    definition.getCanonical();

            if (canonical == null
                    || canonical.isBlank()) {
                continue;
            }

            /*
             * Canonical cũng normalize về chính nó.
             */
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

        return Map.copyOf(aliases);
    }

    private static void register(
            Map<String, String> aliases,
            String canonical,
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return;
        }

        String compactKey =
                NormalizationTextSupport.compactKey(
                        value
                );

        if (compactKey.isBlank()) {
            return;
        }

        aliases.put(
                compactKey,
                canonical
        );
    }

    private static Set<String> foldSet(
            Set<String> values
    ) {
        if (values == null
                || values.isEmpty()) {
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
                folded.add(normalized);
            }
        }

        return Set.copyOf(folded);
    }
}