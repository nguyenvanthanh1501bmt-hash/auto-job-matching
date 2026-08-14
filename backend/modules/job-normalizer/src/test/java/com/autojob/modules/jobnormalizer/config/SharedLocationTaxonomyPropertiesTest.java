package com.autojob.modules.jobnormalizer.config;

import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SharedLocationTaxonomyPropertiesTest {

    @Test
    void shouldLoadSharedLocationTaxonomy() {
        SharedLocationTaxonomyProperties properties =
                TaxonomyTestLoader
                        .loadSharedLocations();

        assertThat(
                properties.getItems()
        ).hasSize(32);

        assertThat(
                properties.getIgnoredValues()
        ).isNotNull();

        assertThat(
                properties.getAmbiguousAliases()
        ).contains(
                "DN"
        );
    }

    @Test
    void shouldHaveUniqueStableLocationIds() {
        SharedLocationTaxonomyProperties properties =
                TaxonomyTestLoader
                        .loadSharedLocations();

        Set<String> ids =
                new HashSet<>();

        for (SharedLocationTaxonomyProperties.LocationDefinition definition
                : properties.getItems()) {

            assertThat(
                    definition.getId()
            ).isNotBlank();

            assertThat(
                    definition.getCanonical()
            ).isNotBlank();

            assertThat(
                    definition.getKind()
            ).isIn(
                    "CITY",
                    "REGION",
                    "COUNTRY"
            );

            assertThat(
                    ids.add(
                            definition.getId()
                    )
            )
                    .as(
                            "duplicate shared location id: %s",
                            definition.getId()
                    )
                    .isTrue();
        }
    }

    @Test
    void shouldMergeHoChiMinhJobAndCvNames() {
        SharedLocationTaxonomyProperties.LocationDefinition hcm =
                findById(
                        "ho-chi-minh"
                );

        assertThat(
                hcm.getCanonical()
        ).isEqualTo(
                "Hồ Chí Minh"
        );

        assertThat(
                hcm.getKind()
        ).isEqualTo(
                "CITY"
        );

        assertThat(
                hcm.getAliases()
        ).anyMatch(
                value ->
                        value.equalsIgnoreCase(
                                "Ho Chi Minh City"
                        )
        );
    }

    @Test
    void shouldMergeHaNoiJobAndCvNames() {
        SharedLocationTaxonomyProperties.LocationDefinition haNoi =
                findByCanonical(
                        "Hà Nội"
                );

        assertThat(
                haNoi.getKind()
        ).isEqualTo(
                "CITY"
        );

        assertThat(
                haNoi.getAliases()
        ).anyMatch(
                value ->
                        value.equalsIgnoreCase(
                                "Hanoi"
                        )
        );
    }

    @Test
    void shouldKeepHueCityAndThuaThienHueRegionSeparate() {
        SharedLocationTaxonomyProperties.LocationDefinition hue =
                findByCanonical(
                        "Huế"
                );

        SharedLocationTaxonomyProperties.LocationDefinition region =
                findByCanonical(
                        "Thua Thien Hue"
                );

        assertThat(
                hue.getKind()
        ).isEqualTo(
                "CITY"
        );

        assertThat(
                region.getKind()
        ).isEqualTo(
                "REGION"
        );

        assertThat(
                hue.getAliases()
        ).noneMatch(
                value ->
                        value.equalsIgnoreCase(
                                "Thừa Thiên Huế"
                        )
        );
    }

    @Test
    void shouldNotTreatRemoteAsGeographicLocation() {
        SharedLocationTaxonomyProperties properties =
                TaxonomyTestLoader
                        .loadSharedLocations();

        assertThat(
                properties
                        .getItems()
                        .stream()
                        .map(
                                SharedLocationTaxonomyProperties
                                        .LocationDefinition
                                        ::getCanonical
                        )
                        .toList()
        ).doesNotContain(
                "Remote"
        );
    }

    @Test
    void shouldNotAssignAmbiguousDnAliasToAnyLocation() {
        SharedLocationTaxonomyProperties properties =
                TaxonomyTestLoader
                        .loadSharedLocations();

        assertThat(
                properties.getAmbiguousAliases()
        ).contains(
                "DN"
        );

        assertThat(
                properties
                        .getItems()
                        .stream()
                        .flatMap(
                                definition ->
                                        definition
                                                .getAliases()
                                                .stream()
                        )
                        .toList()
        ).noneMatch(
                value ->
                        value.equalsIgnoreCase(
                                "DN"
                        )
        );
    }

    private SharedLocationTaxonomyProperties.LocationDefinition findById(
            String id
    ) {
        return items()
                .stream()
                .filter(
                        definition ->
                                id.equals(
                                        definition.getId()
                                )
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Missing shared location id: "
                                                + id
                                )
                );
    }

    private SharedLocationTaxonomyProperties.LocationDefinition findByCanonical(
            String canonical
    ) {
        return items()
                .stream()
                .filter(
                        definition ->
                                canonical.equals(
                                        definition.getCanonical()
                                )
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Missing shared location canonical: "
                                                + canonical
                                )
                );
    }

    private List<SharedLocationTaxonomyProperties.LocationDefinition> items() {
        return TaxonomyTestLoader
                .loadSharedLocations()
                .getItems();
    }
}