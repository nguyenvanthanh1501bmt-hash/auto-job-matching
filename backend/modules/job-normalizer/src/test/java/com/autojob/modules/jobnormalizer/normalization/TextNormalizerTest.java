package com.autojob.modules.jobnormalizer.normalization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    private TextNormalizer textNormalizer;

    @BeforeEach
    void setUp() {
        textNormalizer = new TextNormalizer();
    }

    @Test
    void shouldTrimAndCollapseWhitespace() {
        String result = textNormalizer.normalizeInline(
                "   Senior   Java\tBackend\r\nDeveloper   "
        );

        assertThat(result)
                .isEqualTo("Senior Java Backend Developer");
    }

    @Test
    void shouldRemoveZeroWidthCharacters() {
        String result = textNormalizer.normalizeInline(
                "Spring\u200BBoot"
        );

        assertThat(result)
                .isEqualTo("SpringBoot");
    }

    @Test
    void shouldPreserveVietnameseCharacters() {
        String result = textNormalizer.normalizeInline(
                "   Kỹ   sư phần mềm   "
        );

        assertThat(result)
                .isEqualTo("Kỹ sư phần mềm");
    }

    @Test
    void shouldReturnNullForNullOrBlankInlineText() {
        assertThat(textNormalizer.normalizeInline(null))
                .isNull();

        assertThat(textNormalizer.normalizeInline(""))
                .isNull();

        assertThat(textNormalizer.normalizeInline("   \t\r\n   "))
                .isNull();
    }

    @Test
    void shouldNormalizeMultilineText() {
        String input =
                "  Dòng thứ nhất  \r\n"
                        + "\r\n"
                        + "\r\n"
                        + "  Dòng thứ hai\t  ";

        String result = textNormalizer.normalizeMultiline(input);

        assertThat(result)
                .isEqualTo("Dòng thứ nhất\n\nDòng thứ hai");
    }

    @Test
    void shouldReturnNullForBlankMultilineText() {
        assertThat(textNormalizer.normalizeMultiline(null))
                .isNull();

        assertThat(textNormalizer.normalizeMultiline("   \r\n\t   "))
                .isNull();
    }
}