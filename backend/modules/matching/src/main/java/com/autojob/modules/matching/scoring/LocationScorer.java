package com.autojob.modules.matching.scoring;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.jobnormalizer.config.SharedLocationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class LocationScorer {

    private static final double UNKNOWN_SCORE =
            0.50d;

    private static final double
            CURRENT_LOCATION_MATCH_SCORE =
            0.85d;

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{M}+");

    private static final Pattern NON_KEY =
            Pattern.compile("[^a-z0-9]+");

    private final Map<String, String>
            aliasToLocationId;

    private final Set<String>
            remoteAliases;

    public LocationScorer(
            SharedLocationTaxonomyProperties taxonomy
    ) {
        this.aliasToLocationId =
                buildAliasMap(
                        taxonomy.getItems()
                );

        this.remoteAliases =
                compactSet(
                        taxonomy
                                .getNonGeographicAliases()
                );
    }

    public double score(
            CandidateProfile candidate,
            NormalizedJob job
    ) {
        return evaluate(
                candidate,
                job
        ).score();
    }

    public Result evaluate(
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

        boolean acceptsRemote =
                candidate.getPreferredWorkModes() != null
                        && candidate
                        .getPreferredWorkModes()
                        .contains(
                                CandidateProfile
                                        .WorkMode
                                        .REMOTE
                        );

        if (acceptsRemote
                && containsRemoteSignal(
                job.getLocationText()
        )) {
            return Result.known(1.0d);
        }

        Set<String> jobLocations =
                normalizeLocations(
                        job.getLocations()
                );

        if (jobLocations.isEmpty()) {
            return Result.unknown();
        }

        Set<String> preferredLocations =
                normalizeLocations(
                        candidate
                                .getPreferredLocations()
                );

        if (!preferredLocations.isEmpty()) {

            if (intersects(
                    preferredLocations,
                    jobLocations
            )) {
                return Result.known(1.0d);
            }

            return Result.known(0.20d);
        }

        Set<String> currentLocations =
                candidateCurrentLocations(
                        candidate
                );

        if (currentLocations.isEmpty()) {
            return Result.unknown();
        }

        if (intersects(
                currentLocations,
                jobLocations
        )) {
            return Result.known(
                    CURRENT_LOCATION_MATCH_SCORE
            );
        }

        return Result.unknown();
    }

    private Set<String> candidateCurrentLocations(
            CandidateProfile candidate
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        CandidateProfile.ContactInformation contact =
                candidate.getContact();

        if (contact == null) {
            return result;
        }

        addLocation(
                result,
                contact.city()
        );

        addLocation(
                result,
                contact.provinceOrState()
        );

        addLocation(
                result,
                contact.addressText()
        );

        return result;
    }

    private void addLocation(
            Set<String> target,
            String value
    ) {
        String normalized =
                normalizeLocation(value);

        if (!normalized.isBlank()) {
            target.add(normalized);
        }
    }

    private Set<String> normalizeLocations(
            List<String> values
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        if (values == null) {
            return result;
        }

        for (String value : values) {

            String normalized =
                    normalizeLocation(value);

            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }

        return result;
    }

    private String normalizeLocation(
            String value
    ) {
        String key = compact(value);

        if (key.isBlank()) {
            return "";
        }

        String exact =
                aliasToLocationId.get(key);

        if (exact != null) {
            return exact;
        }

        String bestMatch = null;
        int longestAlias = -1;

        for (Map.Entry<String, String> entry
                : aliasToLocationId.entrySet()) {

            String alias =
                    entry.getKey();

            if (alias.length() < 3) {
                continue;
            }

            if (key.contains(alias)
                    && alias.length()
                    > longestAlias) {

                bestMatch =
                        entry.getValue();

                longestAlias =
                        alias.length();
            }
        }

        if (bestMatch != null) {
            return bestMatch;
        }

        return "raw:" + key;
    }

    private boolean intersects(
            Set<String> first,
            Set<String> second
    ) {
        for (String value : first) {
            if (second.contains(value)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsRemoteSignal(
            String locationText
    ) {
        String compactText =
                compact(locationText);

        if (compactText.isBlank()) {
            return false;
        }

        for (String alias : remoteAliases) {

            if (!alias.isBlank()
                    && compactText.contains(alias)) {

                return true;
            }
        }

        return false;
    }

    private static Map<String, String>
    buildAliasMap(
            List<SharedLocationTaxonomyProperties.LocationDefinition>
                    definitions
    ) {
        Map<String, String> result =
                new LinkedHashMap<>();

        if (definitions == null) {
            return result;
        }

        for (SharedLocationTaxonomyProperties.LocationDefinition definition
                : definitions) {

            if (definition == null
                    || definition.getId() == null
                    || definition
                    .getId()
                    .isBlank()) {

                continue;
            }

            String id =
                    definition
                            .getId()
                            .trim();

            register(
                    result,
                    id,
                    id
            );

            register(
                    result,
                    definition.getCanonical(),
                    id
            );

            if (definition.getAliases() != null) {

                for (String alias
                        : definition.getAliases()) {

                    register(
                            result,
                            alias,
                            id
                    );
                }
            }
        }

        return Map.copyOf(result);
    }

    private static Set<String> compactSet(
            Set<String> values
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        if (values == null) {
            return result;
        }

        for (String value : values) {

            String key =
                    compact(value);

            if (!key.isBlank()) {
                result.add(key);
            }
        }

        return Set.copyOf(result);
    }

    private static void register(
            Map<String, String> target,
            String value,
            String id
    ) {
        String key =
                compact(value);

        if (!key.isBlank()) {
            target.putIfAbsent(
                    key,
                    id
            );
        }
    }

    private static String compact(
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
                        .replace('đ', 'd')
                        .replace('Đ', 'D')
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .trim();

        return NON_KEY
                .matcher(folded)
                .replaceAll("");
    }

    public record Result(
            double score,
            boolean known
    ) {

        public Result {
            if (!Double.isFinite(score)
                    || score < 0.0d
                    || score > 1.0d) {

                throw new IllegalArgumentException(
                        "score must be between 0.0 and 1.0"
                );
            }
        }

        public static Result known(
                double score
        ) {
            return new Result(
                    score,
                    true
            );
        }

        public static Result unknown() {
            return new Result(
                    UNKNOWN_SCORE,
                    false
            );
        }
    }
}