package com.autojob.modules.jobnormalizer.normalization;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class LocationNormalizer {

    /**
     * Tách theo:
     * - dấu phẩy
     * - dấu chấm phẩy
     * - dấu |
     * - dấu /
     * - xuống dòng
     * - dấu gạch ngang có khoảng trắng hai bên
     *
     * Không tách dấu gạch ngang nằm trong tên riêng.
     */
    private static final Pattern LOCATION_SEPARATOR = Pattern.compile(
            "(?:\\s*[,;|/]\\s*|\\R+|\\s+[-–—]\\s+)"
    );

    private static final Map<String, String> LOCATION_ALIASES =
            buildAliases();

    private final TextNormalizer textNormalizer;

    public List<String> normalize(String locationText) {
        /*
         * Phải giữ newline cho đến khi tách location.
         *
         * Không dùng normalizeInline() tại đây vì nó sẽ biến:
         *
         * "Hà Nội\nRemote"
         *
         * thành:
         *
         * "Hà Nội Remote"
         */
        String cleaned = textNormalizer.normalizeMultiline(
                locationText
        );

        if (cleaned == null) {
            return List.of();
        }

        List<String> normalizedLocations = new ArrayList<>();
        Set<String> seenLocations = new LinkedHashSet<>();

        String[] locationParts = LOCATION_SEPARATOR.split(cleaned);

        for (String locationPart : locationParts) {
            String canonicalLocation = canonicalize(locationPart);

            if (canonicalLocation == null) {
                continue;
            }

            String deduplicationKey = canonicalLocation
                    .toLowerCase(Locale.ROOT);

            if (seenLocations.add(deduplicationKey)) {
                normalizedLocations.add(canonicalLocation);
            }
        }

        return List.copyOf(normalizedLocations);
    }

    private String canonicalize(String value) {
        String cleaned = textNormalizer.normalizeInline(value);

        if (cleaned == null) {
            return null;
        }

        String compactKey =
                NormalizationTextSupport.compactKey(cleaned);

        String mappedLocation =
                LOCATION_ALIASES.get(compactKey);

        if (mappedLocation != null) {
            return mappedLocation;
        }

        String folded =
                NormalizationTextSupport.fold(cleaned);

        if (shouldIgnore(folded)) {
            return null;
        }

        /*
         * Không có alias thì giữ nguyên địa điểm đã cleanup.
         *
         * Ví dụ:
         * "Bắc Giang" → "Bắc Giang"
         */
        return cleaned;
    }

    private boolean shouldIgnore(String folded) {
        return folded.isBlank()
                || folded.equals("not available")
                || folded.equals("n/a")
                || folded.equals("unknown")
                || folded.startsWith("quan ")
                || folded.startsWith("district ")
                || folded.startsWith("phuong ")
                || folded.startsWith("ward ")
                || folded.startsWith("duong ")
                || folded.startsWith("street ")
                || folded.startsWith("so ");
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();

        register(
                aliases,
                "Ho Chi Minh",
                "TP.HCM",
                "TP HCM",
                "TPHCM",
                "HCM",
                "Hồ Chí Minh",
                "Thành phố Hồ Chí Minh",
                "Ho Chi Minh",
                "Ho Chi Minh City",
                "Sai Gon",
                "Sài Gòn",
                "Saigon"
        );

        register(
                aliases,
                "Ha Noi",
                "HN",
                "Hà Nội",
                "Ha Noi",
                "Hanoi",
                "Thành phố Hà Nội"
        );

        register(
                aliases,
                "Da Nang",
                "Đà Nẵng",
                "Da Nang",
                "Danang"
        );

        register(
                aliases,
                "Remote",
                "Remote",
                "Work from home",
                "WFH",
                "Từ xa",
                "Làm việc từ xa"
        );

        register(
                aliases,
                "Tay Ninh",
                "Tây Ninh",
                "Tay Ninh"
        );

        register(
                aliases,
                "Can Tho",
                "Cần Thơ",
                "Can Tho"
        );

        register(
                aliases,
                "Hai Phong",
                "Hải Phòng",
                "Hai Phong"
        );

        register(
                aliases,
                "Binh Duong",
                "Bình Dương",
                "Binh Duong"
        );

        register(
                aliases,
                "Dong Nai",
                "Đồng Nai",
                "Dong Nai"
        );

        register(
                aliases,
                "Gia Lai",
                "Gia Lai"
        );

        register(
                aliases,
                "Khanh Hoa",
                "Khánh Hòa",
                "Khanh Hoa"
        );

        register(
                aliases,
                "Hue",
                "Huế",
                "Hue",
                "Thừa Thiên Huế",
                "Thua Thien Hue"
        );

        return Map.copyOf(aliases);
    }

    private static void register(
            Map<String, String> aliases,
            String canonical,
            String... values
    ) {
        for (String value : values) {
            aliases.put(
                    NormalizationTextSupport.compactKey(value),
                    canonical
            );
        }
    }
}