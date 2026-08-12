package com.autojob.modules.jobnormalizer.normalization;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class LocationNormalizer {

    /**
     * Chỉ tách ở các separator có semantics rõ ràng cho danh sách location.
     *
     * Không tách dấu gạch ngang vì nó có thể là một phần của tên địa danh:
     * "Bà Rịa - Vũng Tàu" hoặc một display value như "Remote - Vietnam".
     */
    private static final Pattern LOCATION_SEPARATOR = Pattern.compile(
            "(?:\\s*[,;|/]\\s*|\\R+)"
    );

    private static final Map<String, String> LOCATION_ALIASES =
            buildAliases();

    private final TextNormalizer textNormalizer;

    public List<String> normalize(String locationText) {
        /*
         * Phải giữ newline cho đến khi tách location.
         * normalizeInline() sẽ làm mất separator này.
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

            String deduplicationKey = NormalizationTextSupport.fold(
                    canonicalLocation
            );

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

        String compactKey = NormalizationTextSupport.compactKey(cleaned);
        String mappedLocation = LOCATION_ALIASES.get(compactKey);

        if (mappedLocation != null) {
            return mappedLocation;
        }

        String folded = NormalizationTextSupport.fold(cleaned);

        if (shouldIgnore(folded)) {
            return null;
        }

        /*
         * Không có alias thì giữ nguyên text đã cleanup.
         * Đây là behavior quan trọng để normalizer dùng được cho tỉnh/thành,
         * địa điểm nước ngoài và location mới chưa có trong alias map.
         */
        return cleaned;
    }

    private boolean shouldIgnore(String folded) {
        return folded.isBlank()
                || folded.equals("not available")
                || folded.equals("n/a")
                || folded.equals("unknown");
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();

        register(
                aliases,
                "Hồ Chí Minh",
                "TP.HCM",
                "TP HCM",
                "TPHCM",
                "HCM",
                "HCMC",
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
                "Hà Nội",
                "HN",
                "Hà Nội",
                "Ha Noi",
                "Hanoi",
                "Thành phố Hà Nội"
        );

        register(
                aliases,
                "Đà Nẵng",
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
                "Bình Dương",
                "Bình Dương",
                "Binh Duong"
        );

        register(
                aliases,
                "Bắc Ninh",
                "Bắc Ninh",
                "Bac Ninh"
        );

        register(
                aliases,
                "Hải Phòng",
                "Hải Phòng",
                "Hai Phong"
        );

        register(
                aliases,
                "Quảng Ninh",
                "Quảng Ninh",
                "Quang Ninh"
        );

        register(
                aliases,
                "Đồng Nai",
                "Đồng Nai",
                "Dong Nai"
        );

        register(
                aliases,
                "Cần Thơ",
                "Cần Thơ",
                "Can Tho"
        );

        register(
                aliases,
                "Long An",
                "Long An"
        );

        register(
                aliases,
                "Bà Rịa - Vũng Tàu",
                "Bà Rịa - Vũng Tàu",
                "Ba Ria - Vung Tau",
                "Bà Rịa Vũng Tàu",
                "Ba Ria Vung Tau"
        );

        register(
                aliases,
                "Tây Ninh",
                "Tây Ninh",
                "Tay Ninh"
        );

        register(
                aliases,
                "Gia Lai",
                "Gia Lai"
        );

        register(
                aliases,
                "Khánh Hòa",
                "Khánh Hòa",
                "Khanh Hoa"
        );

        register(
                aliases,
                "Huế",
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