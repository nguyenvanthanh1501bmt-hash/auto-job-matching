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

    /*
     * Candidate chỉ đang sống ở cùng thành phố,
     * nhưng không nói đó là preferred location.
     *
     * Vì vậy không cho full 1.0.
     */
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
        Objects.requireNonNull(
                candidate,
                "candidate must not be null"
        );

        Objects.requireNonNull(
                job,
                "job must not be null"
        );

        /*
         * -------------------------------------------------
         * 1. Explicit remote preference.
         * -------------------------------------------------
         */
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
            return 1.0d;
        }

        /*
         * -------------------------------------------------
         * 2. Job locations.
         * -------------------------------------------------
         */
        Set<String> jobLocations =
                normalizeLocations(
                        job.getLocations()
                );

        if (jobLocations.isEmpty()) {
            return UNKNOWN_SCORE;
        }

        /*
         * -------------------------------------------------
         * 3. Explicit preferred locations.
         *
         * Nếu candidate thật sự khai preferred location
         * thì đây là signal mạnh.
         * -------------------------------------------------
         */
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
                return 1.0d;
            }

            /*
             * Candidate nói rõ preference nhưng job
             * nằm chỗ khác.
             */
            return 0.20d;
        }

        /*
         * -------------------------------------------------
         * 4. Fallback về current/home location.
         *
         * CV parser của bạn:
         *
         * preferredLocations = []
         * contact.city = Hồ Chí Minh
         *
         * Không nên mất hoàn toàn location signal.
         * -------------------------------------------------
         */
        Set<String> currentLocations =
                candidateCurrentLocations(
                        candidate
                );

        if (currentLocations.isEmpty()) {
            return UNKNOWN_SCORE;
        }

        if (intersects(
                currentLocations,
                jobLocations
        )) {
            return CURRENT_LOCATION_MATCH_SCORE;
        }

        /*
         * Candidate chỉ đang sống ở HCM,
         * không tuyên bố "chỉ muốn HCM".
         *
         * Vì vậy job Hà Nội/Đà Nẵng không bị phạt
         * nặng như explicit preference mismatch.
         */
        return UNKNOWN_SCORE;
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

        /*
         * addressText là fallback cuối.
         *
         * Ví dụ:
         * "Thu Duc, Ho Chi Minh City"
         */
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

        /*
         * Exact taxonomy alias.
         */
        String exact =
                aliasToLocationId.get(key);

        if (exact != null) {
            return exact;
        }

        /*
         * addressText có thể là:
         *
         * "Thu Duc, Ho Chi Minh City"
         *
         * nên exact key sẽ không match.
         *
         * Tìm taxonomy alias nằm trong full address.
         */
        String bestMatch = null;
        int longestAlias = -1;

        for (Map.Entry<String, String> entry
                : aliasToLocationId.entrySet()) {

            String alias =
                    entry.getKey();

            /*
             * Bỏ alias quá ngắn để tránh false positive:
             * HCM được phép vì 3 chars,
             * nhưng những alias 1-2 chars dễ match nhầm.
             */
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
}