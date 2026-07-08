package com.autojob.modules.jobcrawler.parser.mockjob;

import com.autojob.modules.jobcrawler.parser.JobListPageParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockJobListPageParser implements JobListPageParser {

    @Override
    public String sourceCode() {
        return "MOCK";
    }

    @Override
    public List<String> parseDetailUrls(String baseUrl, String html) {
        Document doc = Jsoup.parse(html, baseUrl);

        return doc.select("[data-job-card] a.job-link[href], a.job-link[href]")
                .stream()
                .map(element -> element.absUrl("href"))
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .toList();
    }
}