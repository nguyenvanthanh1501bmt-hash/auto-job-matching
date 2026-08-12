package com.autojob.modules.jobnormalizer.normalization;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class NormalizationTextSupport {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern ZERO_WIDTH_CHARACTERS =
            Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF]");
    private static final Pattern MULTIPLE_WHITESPACE =
            Pattern.compile("[\\s\\p{Z}]+");
    private static final Pattern NON_COMPACT_KEY_CHARACTERS =
            Pattern.compile("[^a-z0-9+#]+");

    private NormalizationTextSupport() {
    }

    /**
     * Chuyển text thành dạng không dấu để so khớp rule.
     *
     * Không dùng kết quả này để lưu vào MongoDB.
     */
    static String fold(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String withoutZeroWidth = ZERO_WIDTH_CHARACTERS.matcher(value)
                .replaceAll("");

        String decomposed = Normalizer.normalize(
                withoutZeroWidth,
                Normalizer.Form.NFD
        );

        String withoutDiacritics = DIACRITICS.matcher(decomposed)
                .replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'D');

        return MULTIPLE_WHITESPACE.matcher(
                        withoutDiacritics.toLowerCase(Locale.ROOT).trim()
                )
                .replaceAll(" ");
    }

    /**
     * Dùng cho alias key:
     *
     * "Spring Boot" → "springboot"
     * "spring-boot" → "springboot"
     * "Node.js"     → "nodejs"
     */
    static String compactKey(String value) {
        return NON_COMPACT_KEY_CHARACTERS.matcher(fold(value))
                .replaceAll("");
    }
}