package com.autojob.modules.jobcrawler.parser;

import com.autojob.modules.jobcrawler.domain.ParsedRawJob;
import com.autojob.modules.jobcrawler.parser.itviec.ItviecJobDetailPageParser;
import com.autojob.modules.jobcrawler.parser.itviec.ItviecJobListPageParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItviecParserFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseItviecListPage_shouldExtractJobDetailUrls() throws Exception {
        String html = readFixture("fixtures/itviec/list_page_1.html");

        ItviecJobListPageParser parser = new ItviecJobListPageParser(objectMapper);

        List<String> urls = parser.parseDetailUrls("https://itviec.com/it-jobs", html);

        assertThat(urls)
                .isNotEmpty()
                .contains("https://itviec.com/it-jobs/lead-java-engineer-itviec-recruitment-consulting-3714");

        assertThat(urls)
                .allMatch(url -> url.startsWith("https://itviec.com/it-jobs/"));

        assertThat(urls)
                .allMatch(url -> url.matches("^https://itviec\\.com/it-jobs/.+-\\d{4}$"));
    }

    @Test
    void parseItviecDetailPage_shouldExtractRawJobFields() throws Exception {
        String detailUrl = "https://itviec.com/it-jobs/lead-java-engineer-itviec-recruitment-consulting-3714";
        String html = readFixture("fixtures/itviec/detail_1_lead-java-engineer-itviec-recruitment-consulting-3714.html");

        ItviecJobDetailPageParser parser = new ItviecJobDetailPageParser(objectMapper);

        ParsedRawJob job = parser.parseDetail(detailUrl, html);

        assertThat(job.sourceJobId())
                .isEqualTo("lead-java-engineer-itviec-recruitment-consulting-3714");

        assertThat(job.title())
                .isNotBlank()
                .containsIgnoringCase("Java");

        assertThat(job.companyName())
                .isNotBlank()
                .containsIgnoringCase("ITviec");

        assertThat(job.skills())
                .isNotEmpty()
                .contains("Java");

        assertThat(job.locationText())
                .isNotBlank();

        assertThat(job.postedText())
                .isNotBlank();

        assertThat(job.deadlineText())
                .isNotBlank();

        assertThat(job.descriptionText())
                .isNotBlank();

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