package com.autojob.modules.jobnormalizer.normalization;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerV2Test {

    private final TextNormalizer normalizer =
            new TextNormalizer();

    @Test
    void shouldNormalizeNbspAndOtherRepeatedWhitespace() {
        String result = normalizer.normalizeInline(
                "Kế\u00A0toán\t\t tổng   hợp"
        );

        assertThat(result)
                .isEqualTo("Kế toán tổng hợp");
    }

    @Test
    void shouldReplaceControlCharactersWithoutJoiningWords() {
        String result = normalizer.normalizeInline(
                "Customer\u0000Service"
        );

        assertThat(result)
                .isEqualTo("Customer Service");
    }

    @Test
    void shouldNormalizeUnicodeToNfcWithoutRemovingVietnameseDiacritics() {
        String decomposed = Normalizer.normalize(
                "Kỹ sư điện",
                Normalizer.Form.NFD
        );

        String result =
                normalizer.normalizeInline(decomposed);

        assertThat(result)
                .isEqualTo("Kỹ sư điện")
                .isEqualTo(
                        Normalizer.normalize(
                                result,
                                Normalizer.Form.NFC
                        )
                );
    }

    @Test
    void shouldNormalizeMixedLineEndingsAndKeepParagraphBreaks() {
        String result = normalizer.normalizeMultiline(
                "Dòng 1\r\nDòng 2\n\n\nDòng 3"
        );

        assertThat(result).isEqualTo(
                "Dòng 1\nDòng 2\n\nDòng 3"
        );
    }
}