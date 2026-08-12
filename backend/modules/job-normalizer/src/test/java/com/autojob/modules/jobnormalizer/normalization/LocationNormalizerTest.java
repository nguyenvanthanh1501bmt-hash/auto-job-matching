package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocationNormalizerTest {

    private LocationNormalizer locationNormalizer;

    @BeforeEach
    void setUp() {
        NormalizationTaxonomyProperties taxonomy =
                TaxonomyTestLoader.load();

        locationNormalizer = new LocationNormalizer(
                new TextNormalizer(),
                taxonomy
        );
    }

    @Test
    void shouldNormalizeHoChiMinhAliases() {
        assertThat(locationNormalizer.normalize("TP.HCM"))
                .containsExactly("Hồ Chí Minh");

        assertThat(locationNormalizer.normalize("HCM"))
                .containsExactly("Hồ Chí Minh");

        assertThat(locationNormalizer.normalize("HCMC"))
                .containsExactly("Hồ Chí Minh");

        assertThat(locationNormalizer.normalize("Hồ Chí Minh"))
                .containsExactly("Hồ Chí Minh");

        assertThat(locationNormalizer.normalize("Ho Chi Minh City"))
                .containsExactly("Hồ Chí Minh");
    }

    @Test
    void shouldNormalizeHaNoiAliases() {
        assertThat(locationNormalizer.normalize("Hà Nội"))
                .containsExactly("Hà Nội");

        assertThat(locationNormalizer.normalize("HN"))
                .containsExactly("Hà Nội");

        assertThat(locationNormalizer.normalize("Hanoi"))
                .containsExactly("Hà Nội");
    }

    @Test
    void shouldNormalizeDaNangAliases() {
        assertThat(locationNormalizer.normalize("Đà Nẵng"))
                .containsExactly("Đà Nẵng");

        assertThat(locationNormalizer.normalize("Da Nang"))
                .containsExactly("Đà Nẵng");

        assertThat(locationNormalizer.normalize("Danang"))
                .containsExactly("Đà Nẵng");
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
                "Hồ Chí Minh",
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
                "Hà Nội",
                "Hồ Chí Minh",
                "Đà Nẵng"
        );
    }

    @Test
    void shouldNormalizeMultipleLocationsSeparatedByPipeAndNewline() {
        List<String> result =
                locationNormalizer.normalize(
                        "HCM | Hà Nội\nRemote"
                );

        assertThat(result).containsExactly(
                "Hồ Chí Minh",
                "Hà Nội",
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
                "Hồ Chí Minh",
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
    void shouldKeepBaRiaVungTauAsSingleLocation() {
        assertThat(
                locationNormalizer.normalize(
                        "Bà Rịa - Vũng Tàu"
                )
        ).containsExactly(
                "Bà Rịa - Vũng Tàu"
        );
    }

    @Test
    void shouldKeepRemoteVietnamAsSingleUnknownDisplayLocation() {
        assertThat(
                locationNormalizer.normalize(
                        "Remote - Vietnam"
                )
        ).containsExactly(
                "Remote - Vietnam"
        );
    }

    @Test
    void shouldCanonicalizeAdditionalVietnamLocations() {
        assertThat(
                locationNormalizer.normalize(
                        "Binh Duong, Bac Ninh, Hai Phong, "
                                + "Quang Ninh, Dong Nai, Can Tho"
                )
        ).containsExactly(
                "Bình Dương",
                "Bắc Ninh",
                "Hải Phòng",
                "Quảng Ninh",
                "Đồng Nai",
                "Cần Thơ"
        );
    }

    @Test
    void shouldDeduplicateAliasesIgnoringCaseAndDiacritics() {
        List<String> result =
                locationNormalizer.normalize(
                        "ha noi, HÀ NỘI, Hanoi, "
                                + "Binh Duong, BÌNH DƯƠNG"
                );

        assertThat(result).containsExactly(
                "Hà Nội",
                "Bình Dương"
        );
    }

    @Test
    void shouldKeepUnknownDetailedLocationSegments() {
        List<String> result =
                locationNormalizer.normalize(
                        "TP.HCM, Quận 1, Đường Nguyễn Huệ"
                );

        assertThat(result).containsExactly(
                "Hồ Chí Minh",
                "Quận 1",
                "Đường Nguyễn Huệ"
        );
    }

    @Test
    void shouldTrimAndCollapseWhitespace() {
        List<String> result =
                locationNormalizer.normalize(
                        "  Hồ   Chí Minh  /   Remote "
                );

        assertThat(result).containsExactly(
                "Hồ Chí Minh",
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