package com.autojob.modules.matching.config;

import com.autojob.modules.jobnormalizer.config.SharedSkillTaxonomyProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecondarySkillTaxonomyValidatorTest {

    @Test
    void knownSecondarySkillIdsShouldBeAccepted() {
        assertThatCode(
                () -> SecondarySkillTaxonomyValidator.validate(
                        List.of(
                                definition(
                                        "communication",
                                        "Communication"
                                ),
                                definition(
                                        "teamwork",
                                        "Teamwork"
                                )
                        ),
                        Set.of(
                                "communication",
                                "teamwork"
                        )
                )
        ).doesNotThrowAnyException();
    }

    @Test
    void secondarySkillIdsShouldBeCaseAndWhitespaceTolerant() {
        assertThatCode(
                () -> SecondarySkillTaxonomyValidator.validate(
                        List.of(
                                definition(
                                        "communication",
                                        "Communication"
                                )
                        ),
                        Set.of(
                                "  Communication  "
                        )
                )
        ).doesNotThrowAnyException();
    }

    @Test
    void unknownSecondarySkillIdShouldFailFast() {
        assertThatThrownBy(
                () -> SecondarySkillTaxonomyValidator.validate(
                        List.of(
                                definition(
                                        "communication",
                                        "Communication"
                                )
                        ),
                        Set.of(
                                "communciation"
                        )
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessageContaining(
                        "communciation"
                )
                .hasMessageContaining(
                        "skills.yml"
                );
    }

    private static SharedSkillTaxonomyProperties.SkillDefinition
    definition(
            String id,
            String canonical
    ) {
        SharedSkillTaxonomyProperties.SkillDefinition definition =
                new SharedSkillTaxonomyProperties.SkillDefinition();

        definition.setId(id);
        definition.setCanonical(canonical);
        definition.setCategory("OTHER");
        definition.setAliases(List.of());

        return definition;
    }
}