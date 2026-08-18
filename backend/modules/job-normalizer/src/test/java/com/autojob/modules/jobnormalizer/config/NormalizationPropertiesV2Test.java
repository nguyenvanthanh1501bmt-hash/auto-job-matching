package com.autojob.modules.jobnormalizer.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizationPropertiesV2Test {

    @Test
    void shouldDefaultToRuleV4() {
        NormalizationProperties properties =
                new NormalizationProperties();

        assertThat(
                properties.getVersion()
        ).isEqualTo(
                "rule-v4"
        );
    }
}