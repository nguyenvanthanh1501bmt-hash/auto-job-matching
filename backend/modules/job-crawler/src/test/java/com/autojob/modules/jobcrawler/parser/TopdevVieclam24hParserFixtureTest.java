package com.autojob.modules.jobcrawler.parser;

import com.autojob.modules.jobcrawler.domain.ParsedRawJob;
import com.autojob.modules.jobcrawler.parser.topdev.TopdevJobDetailPageParser;
import com.autojob.modules.jobcrawler.parser.topdev.TopdevJobListPageParser;
import com.autojob.modules.jobcrawler.parser.vieclam24h.Vieclam24hJobDetailPageParser;
import com.autojob.modules.jobcrawler.parser.vieclam24h.Vieclam24hJobListPageParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopdevVieclam24hParserFixtureTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void parseTopdevListPage_shouldExtractJobDetailUrls()
            throws Exception {

        String html =
                readFixture(
                        "fixtures/topdev/list_page_1.html"
                );

        TopdevJobListPageParser parser =
                new TopdevJobListPageParser();

        List<String> urls =
                parser.parseDetailUrls(
                        "https://topdev.vn",
                        html
                );

        assertThat(urls)
                .isNotEmpty()
                .contains(
                        "https://topdev.vn/detail-jobs/game-live-ops-game-ui-ux-artist-supercent-vietnam-2115969"
                );

        assertThat(urls)
                .allMatch(
                        url ->
                                url.startsWith(
                                        "https://topdev.vn/detail-jobs/"
                                )
                );

        assertThat(urls)
                .allMatch(
                        url ->
                                url.matches(
                                        "^https://topdev\\.vn/detail-jobs/.+-\\d+$"
                                )
                );
    }

    @Test
    void parseTopdevDetailPage_shouldExtractRawJobFields()
            throws Exception {

        String detailUrl =
                "https://topdev.vn/detail-jobs/game-live-ops-game-ui-ux-artist-supercent-vietnam-2115969";

        String html =
                readFixture(
                        "fixtures/topdev/detail_1_2115969.html"
                );

        TopdevJobDetailPageParser parser =
                new TopdevJobDetailPageParser(
                        objectMapper
                );

        ParsedRawJob job =
                parser.parseDetail(
                        detailUrl,
                        html
                );

        assertThat(
                job.sourceJobId()
        ).isEqualTo(
                "2115969"
        );

        assertThat(
                job.title()
        ).isEqualTo(
                "[Game Live-Ops]_GAME UI/UX Artist"
        );

        assertThat(
                job.companyName()
        ).isEqualTo(
                "SUPERCENT VIETNAM"
        );

        assertThat(
                job.salaryText()
        )
                .contains(
                        "Negotiable"
                )
                .contains(
                        "VND"
                )
                .contains(
                        "MONTH"
                );

        assertThat(
                job.locationText()
        ).contains(
                "Hồ Chí Minh"
        );

        assertThat(
                job.experienceText()
        ).contains(
                "60"
        );

        assertThat(
                job.seniorityText()
        ).isEqualTo(
                "Senior"
        );

        assertThat(
                job.jobTypeText()
        ).contains(
                "OTHER"
        );

        assertThat(
                job.deadlineText()
        ).isEqualTo(
                "2026-07-29"
        );

        assertThat(
                job.postedText()
        ).isEqualTo(
                "2026-06-29"
        );

        /*
         * `industry=Information Technology` không được trộn
         * vào danh sách skill thật của TopDev.
         */
        assertThat(
                job.skills()
        )
                .containsExactly(
                        "Photoshop",
                        "Illustrator",
                        "UI/UX"
                )
                .doesNotContain(
                        "Information Technology"
                );

        assertThat(
                job.descriptionText()
        )
                .isNotBlank()
                .contains(
                        "Supercent"
                );

        assertThat(
                job.benefitsText()
        )
                .isNotBlank()
                .contains(
                        "13th Salary"
                );

        assertThat(
                job.applyUrl()
        ).isEqualTo(
                detailUrl
        );

        assertThat(
                job.applyType()
        ).isNotNull();
    }

    @Test
    void parseVieclam24hListPage_shouldExtractJobDetailUrls()
            throws Exception {

        String html =
                readFixture(
                        "fixtures/vieclam24h/list_page_1.html"
                );

        Vieclam24hJobListPageParser parser =
                new Vieclam24hJobListPageParser();

        List<String> urls =
                parser.parseDetailUrls(
                        "https://vieclam24h.vn",
                        html
                );

        assertThat(urls)
                .isNotEmpty()
                .contains(
                        "https://vieclam24h.vn/it-phan-cung-mang/ky-thuat-vien-sua-chua-may-tinh-may-in-thu-nhap-den-20-trieu-thang-c7p73id200876404.html"
                );

        assertThat(urls)
                .allMatch(
                        url ->
                                url.startsWith(
                                        "https://vieclam24h.vn/"
                                )
                );

        assertThat(urls)
                .allMatch(
                        url ->
                                url.matches(
                                        "^https://vieclam24h\\.vn/.+id\\d+\\.html$"
                                )
                );
    }

    @Test
    void parseVieclam24hDetailPage_shouldExtractRawJobFields()
            throws Exception {

        String detailUrl =
                "https://vieclam24h.vn/it-phan-cung-mang/ky-thuat-vien-sua-chua-may-tinh-may-in-thu-nhap-den-20-trieu-thang-c7p73id200876404.html";

        String html =
                readFixture(
                        "fixtures/vieclam24h/detail_1_200876404.html"
                );

        Vieclam24hJobDetailPageParser parser =
                new Vieclam24hJobDetailPageParser(
                        objectMapper
                );

        ParsedRawJob job =
                parser.parseDetail(
                        detailUrl,
                        html
                );

        assertThat(
                job.sourceJobId()
        ).isEqualTo(
                "200876404"
        );

        assertThat(
                job.title()
        ).isEqualTo(
                "Kỹ Thuật Viên Sửa Chữa Máy Tính - Máy In (Thu Nhập Đến 20 Triệu / Tháng)"
        );

        assertThat(
                job.companyName()
        ).isEqualTo(
                "Công Ty TNHH Công Nghệ Cao Ntp"
        );

        assertThat(
                job.salaryText()
        )
                .contains(
                        "8000000"
                )
                .contains(
                        "20000000"
                )
                .contains(
                        "VND"
                )
                .contains(
                        "MONTH"
                );

        assertThat(
                job.locationText()
        ).contains(
                "Ha Noi"
        );

        assertThat(
                job.experienceText()
        ).contains(
                "24"
        );

        assertThat(
                job.seniorityText()
        ).isEqualTo(
                "Nhân viên"
        );

        assertThat(
                job.jobTypeText()
        ).contains(
                "FULL_TIME"
        );

        assertThat(
                job.deadlineText()
        ).startsWith(
                "2026-08-03"
        );

        assertThat(
                job.postedText()
        ).isEqualTo(
                "2026-07-07"
        );

        /*
         * Fixture chỉ có `industry`.
         * Industry là nhóm nghề, không được giả làm skill.
         */
        assertThat(
                job.skills()
        ).isEmpty();

        assertThat(
                job.descriptionText()
        )
                .isNotBlank()
                .contains(
                        "Máy in"
                );

        assertThat(
                job.requirementsText()
        )
                .isNotBlank()
                .contains(
                        "Có kinh nghiệm"
                );

        assertThat(
                job.benefitsText()
        )
                .isNotBlank()
                .contains(
                        "BHXH"
                );

        assertThat(
                job.applyUrl()
        ).isEqualTo(
                detailUrl
        );

        assertThat(
                job.applyType()
        ).isNotNull();
    }

    private String readFixture(
            String classpathLocation
    ) throws Exception {

        URL resource =
                getClass()
                        .getClassLoader()
                        .getResource(
                                classpathLocation
                        );

        assertThat(resource)
                .as(
                        "Fixture not found: "
                                + classpathLocation
                )
                .isNotNull();

        return Files.readString(
                Path.of(
                        resource.toURI()
                ),
                StandardCharsets.UTF_8
        );
    }
}