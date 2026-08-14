package com.autojob.modules.jobnormalizer.config;

import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SharedSkillTaxonomyPropertiesTest {

    @Test
    void shouldLoadSharedSkillTaxonomy() {
        SharedSkillTaxonomyProperties properties =
                TaxonomyTestLoader
                        .loadSharedSkills();

        assertThat(
                properties.getItems()
        ).isNotEmpty();

        assertThat(
                properties.getRichRawSkillCount()
        ).isEqualTo(
                2
        );
    }

    @Test
    void shouldHaveUniqueStableIds() {
        SharedSkillTaxonomyProperties properties =
                TaxonomyTestLoader
                        .loadSharedSkills();

        Set<String> ids =
                new HashSet<>();

        for (SharedSkillTaxonomyProperties.SkillDefinition definition
                : properties.getItems()) {

            assertThat(
                    definition.getId()
            ).isNotBlank();

            assertThat(
                    definition.getCanonical()
            ).isNotBlank();

            assertThat(
                    definition.getCategory()
            ).isNotBlank();

            assertThat(
                    ids.add(
                            definition.getId()
                    )
            )
                    .as(
                            "duplicate shared skill id: %s",
                            definition.getId()
                    )
                    .isTrue();
        }
    }

    @Test
    void shouldMergeAwsJobAndCvNamesIntoOneConcept() {
        SharedSkillTaxonomyProperties.SkillDefinition aws =
                findById(
                        "aws"
                );

        assertThat(
                aws.getCanonical()
        ).isEqualTo(
                "AWS"
        );

        assertThat(
                aws.getAliases()
        ).anyMatch(
                value ->
                        value.equalsIgnoreCase(
                                "Amazon Web Services"
                        )
        );
    }

    @Test
    void shouldMergeTaxAccountingJobAndCvNamesIntoOneConcept() {
        SharedSkillTaxonomyProperties.SkillDefinition taxAccounting =
                findById(
                        "tax-accounting"
                );

        assertThat(
                taxAccounting.getCanonical()
        ).isEqualTo(
                "Kế toán thuế"
        );

        assertThat(
                taxAccounting.getAliases()
        ).anyMatch(
                value ->
                        value.equalsIgnoreCase(
                                "Tax Accounting"
                        )
        );
    }

    private SharedSkillTaxonomyProperties.SkillDefinition findById(
            String id
    ) {
        List<SharedSkillTaxonomyProperties.SkillDefinition> items =
                TaxonomyTestLoader
                        .loadSharedSkills()
                        .getItems();

        return items.stream()
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
                                        "Missing shared skill id: "
                                                + id
                                )
                );
    }
}