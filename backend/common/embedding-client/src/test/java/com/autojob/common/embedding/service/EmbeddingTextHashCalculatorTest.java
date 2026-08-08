package com.autojob.common.embedding.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingTextHashCalculatorTest {

    private final EmbeddingTextHashCalculator calculator =
            new EmbeddingTextHashCalculator();

    @Test
    void shouldCalculateStableSha256ForExactUtf8Text() {
        String text =
                "query: Kỹ sư Java\nLocations: Hồ Chí Minh";

        assertThat(
                calculator.calculate(text)
        ).isEqualTo(
                "667d5b7cd2245bd9fad0b0f93b0001aa"
                        + "a1c3d58ed79aa20438a12a5b88651384"
        );
    }

    @Test
    void shouldReturnSameHashForSameInput() {
        String text =
                "query: Java Engineer";

        assertThat(
                calculator.calculate(text)
        ).isEqualTo(
                calculator.calculate(text)
        );
    }

    @Test
    void shouldChangeHashWhenWhitespaceChanges() {
        String first =
                "query: Java Engineer";

        String second =
                "query: Java Engineer ";

        assertThat(
                calculator.calculate(first)
        ).isNotEqualTo(
                calculator.calculate(second)
        );
    }

    @Test
    void shouldRejectNullText() {
        assertThatThrownBy(
                () -> calculator.calculate(null)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "must not be null"
                );
    }
}