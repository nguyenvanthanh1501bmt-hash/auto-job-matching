package com.autojob.modules.jobnormalizer.normalization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocationNormalizerTest {

    private LocationNormalizer locationNormalizer;

    @BeforeEach
    void setUp() {
        locationNormalizer = new LocationNormalizer(
                new TextNormalizer()
        );
    }

    @Test
    void shouldNormalizeHoChiMinhAliases() {
        assertThat(locationNormalizer.normalize("TP.HCM"))
                .containsExactly("Ho Chi Minh");

        assertThat(locationNormalizer.normalize("HCM"))
                .containsExactly("Ho Chi Minh");

        assertThat(locationNormalizer.normalize("Hồ Chí Minh"))
                .containsExactly("Ho Chi Minh");

        assertThat(locationNormalizer.normalize("Ho Chi Minh City"))
                .containsExactly("Ho Chi Minh");
    }

    @Test
    void shouldNormalizeHaNoiAliases() {
        assertThat(locationNormalizer.normalize("Hà Nội"))
                .containsExactly("Ha Noi");

        assertThat(locationNormalizer.normalize("HN"))
                .containsExactly("Ha Noi");

        assertThat(locationNormalizer.normalize("Hanoi"))
                .containsExactly("Ha Noi");
    }

    @Test
    void shouldNormalizeDaNangAliases() {
        assertThat(locationNormalizer.normalize("Đà Nẵng"))
                .containsExactly("Da Nang");

        assertThat(locationNormalizer.normalize("Da Nang"))
                .containsExactly("Da Nang");

        assertThat(locationNormalizer.normalize("Danang"))
                .containsExactly("Da Nang");
    }

    @Test
    void shouldNormalizeRemoteAliases() {
        assertThat(locationNormalizer.normalize("Remote"))
                .containsExactly("Remote");

        assertThat(locationNormalizer.normalize("WFH"))
                .containsExactly("Remote");

        assertThat(locationNormalizer.normalize("Work from home"))
                .containsExactly("Remote");

        assertThat(locationNormalizer.normalize("Làm việc từ xa"))
                .containsExactly("Remote");
    }

    @Test
    void shouldNormalizeMultipleLocationsSeparatedBySlash() {
        List<String> result =
                locationNormalizer.normalize("TP.HCM / Remote");

        assertThat(result).containsExactly(
                "Ho Chi Minh",
                "Remote"
        );
    }

    @Test
    void shouldNormalizeMultipleLocationsSeparatedByComma() {
        List<String> result =
                locationNormalizer.normalize(
                        "Hà Nội, Hồ Chí Minh, Đà Nẵng"
                );

        assertThat(result).containsExactly(
                "Ha Noi",
                "Ho Chi Minh",
                "Da Nang"
        );
    }

    @Test
    void shouldNormalizeMultipleLocationsSeparatedByPipeAndNewline() {
        List<String> result =
                locationNormalizer.normalize(
                        "HCM | Hà Nội\nRemote"
                );

        assertThat(result).containsExactly(
                "Ho Chi Minh",
                "Ha Noi",
                "Remote"
        );
    }

    @Test
    void shouldRemoveDuplicateLocations() {
        List<String> result =
                locationNormalizer.normalize(
                        "TP.HCM, HCM, Hồ Chí Minh, Remote, WFH"
                );

        assertThat(result).containsExactly(
                "Ho Chi Minh",
                "Remote"
        );
    }

    @Test
    void shouldKeepUnknownProvinceWithoutGuessing() {
        List<String> result =
                locationNormalizer.normalize(
                        "Bắc Giang, Ninh Bình"
                );

        assertThat(result).containsExactly(
                "Bắc Giang",
                "Ninh Bình"
        );
    }

    @Test
    void shouldIgnoreDetailedDistrictOrStreetSegments() {
        List<String> result =
                locationNormalizer.normalize(
                        "TP.HCM, Quận 1, Đường Nguyễn Huệ"
                );

        assertThat(result).containsExactly(
                "Ho Chi Minh"
        );
    }

    @Test
    void shouldTrimAndCollapseWhitespace() {
        List<String> result =
                locationNormalizer.normalize(
                        "  Hồ   Chí Minh  /   Remote "
                );

        assertThat(result).containsExactly(
                "Ho Chi Minh",
                "Remote"
        );
    }

    @Test
    void shouldReturnEmptyListForNullOrBlankInput() {
        assertThat(locationNormalizer.normalize(null))
                .isEmpty();

        assertThat(locationNormalizer.normalize(""))
                .isEmpty();

        assertThat(locationNormalizer.normalize("   "))
                .isEmpty();
    }
}