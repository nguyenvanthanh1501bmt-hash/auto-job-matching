package com.autojob.modules.jobcrawler.parser;

import com.autojob.modules.jobcrawler.parser.joboko.JobokoJobDetailPageParser;
import com.autojob.modules.jobcrawler.parser.joboko.JobokoJobListPageParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobokoParserFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseJobokoListPage_shouldExtractJobDetailUrls() throws Exception {
        String html = readFixture("fixtures/joboko/list_page_1.html");

        JobokoJobListPageParser parser = new JobokoJobListPageParser();

        List<String> urls = parser.parseDetailUrls("https://vn.joboko.com", html);

        assertThat(urls)
                .isNotEmpty()
                .contains("https://vn.joboko.com/viec-lam-ky-su-xay-dung-xvi6563129");

        assertThat(urls)
                .allMatch(url -> url.startsWith("https://vn.joboko.com/viec-lam-"));

        assertThat(urls)
                .allMatch(url -> url.matches("^https://vn\\.joboko\\.com/viec-lam-.+-xvi\\d+$"));
    }

    @Test
    void parseJobokoDetailPage_shouldExtractRawJobFields() throws Exception {
        String detailUrl = "https://vn.joboko.com/viec-lam-ky-su-xay-dung-xvi6563129";
        String html = readFixture("fixtures/joboko/detail_1_6563129.html");

        JobokoJobDetailPageParser parser = new JobokoJobDetailPageParser(objectMapper);

        ParsedRawJob job = parser.parseDetail(detailUrl, html);

        assertThat(job.sourceJobId())
                .isEqualTo("6563129");

        assertThat(job.title())
                .isEqualTo("Kỹ Sư Xây Dựng");

        assertThat(job.companyName())
                .containsIgnoringCase("Xanh Toàn Cầu");

        assertThat(job.salaryText())
                .contains("20 - 30 triệu")
                .contains("VND")
                .contains("MONTH");

        assertThat(job.locationText())
                .contains("Hồ Chí Minh")
                .contains("Tây Ninh");

        assertThat(job.experienceText())
                .contains("2");

        assertThat(job.seniorityText())
                .isEqualTo("Nhân viên");

        assertThat(job.jobTypeText())
                .contains("FULL_TIME");

        assertThat(job.deadlineText())
                .startsWith("2026-07-30");

        assertThat(job.postedText())
                .isEqualTo("2026-07-07");

        assertThat(job.skills())
                .isNotEmpty()
                .anyMatch(skill -> skill.equalsIgnoreCase("xây dựng"));

        assertThat(job.descriptionText())
                .isNotBlank()
                .contains("Triển khai");

        assertThat(job.requirementsText())
                .isNotBlank()
                .contains("02 năm kinh nghiệm");

        assertThat(job.benefitsText())
                .isNotBlank()
                .contains("20 - 30");

        assertThat(job.applyUrl())
                .isEqualTo(detailUrl);

        assertThat(job.applyType())
                .isNotNull();
    }

    private String readFixture(String classpathLocation) throws Exception {
        URL resource = getClass().getClassLoader().getResource(classpathLocation);

        assertThat(resource)
                .as("Fixture not found: " + classpathLocation)
                .isNotNull();

        return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
    }
}