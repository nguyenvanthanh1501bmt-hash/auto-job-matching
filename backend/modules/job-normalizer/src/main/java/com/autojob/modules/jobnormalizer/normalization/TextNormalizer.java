package com.autojob.modules.jobnormalizer.normalization;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class TextNormalizer {

    private static final Pattern ZERO_WIDTH_CHARACTERS =
            Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF]");

    private static final Pattern INLINE_WHITESPACE =
            Pattern.compile("[\\s\\p{Z}]+");

    private static final Pattern HORIZONTAL_WHITESPACE =
            Pattern.compile("[\\t\\f\\p{Zs}]+");

    private static final Pattern EXCESSIVE_NEWLINES =
            Pattern.compile("\\n{3,}");

    /**
     * Dùng cho title, company, salaryText, locationText...
     *
     * Tất cả whitespace được collapse thành một dấu cách.
     */
    public String normalizeInline(String value) {
        String prepared = prepare(value);

        if (prepared == null) {
            return null;
        }

        String result = INLINE_WHITESPACE.matcher(prepared)
                .replaceAll(" ")
                .trim();

        return result.isBlank() ? null : result;
    }

    /**
     * Dùng cho description, requirements và benefits.
     *
     * Giữ tối đa một dòng trống giữa các đoạn.
     */
    public String normalizeMultiline(String value) {
        String prepared = prepare(value);

        if (prepared == null) {
            return null;
        }

        String normalizedLines = Arrays.stream(prepared.split("\\n", -1))
                .map(line -> HORIZONTAL_WHITESPACE.matcher(line)
                        .replaceAll(" ")
                        .trim())
                .collect(Collectors.joining("\n"))
                .strip();

        normalizedLines = EXCESSIVE_NEWLINES.matcher(normalizedLines)
                .replaceAll("\n\n");

        return normalizedLines.isBlank() ? null : normalizedLines;
    }

    private String prepare(String value) {
        if (value == null) {
            return null;
        }

        String normalized = Normalizer.normalize(
                value,
                Normalizer.Form.NFC
        );

        normalized = ZERO_WIDTH_CHARACTERS.matcher(normalized)
                .replaceAll("");

        normalized = normalized
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        return normalized.isBlank() ? null : normalized;
    }
}