package com.autojob.modules.jobcrawler.parser.mockjob;

import com.autojob.common.dtos.ApplyType;
import com.autojob.modules.jobcrawler.parser.JobDetailPageParser;
import com.autojob.modules.jobcrawler.parser.ParsedRawJob;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class MockJobDetailPageParser implements JobDetailPageParser {

    @Override
    public String sourceCode() {
        return "MOCK";
    }

    @Override
    public ParsedRawJob parseDetail(String detailUrl, String html) {
        Document doc = Jsoup.parse(html, detailUrl);

        String applyUrl = doc.selectFirst("a.apply-url[href]") != null
                ? doc.selectFirst("a.apply-url[href]").absUrl("href")
                : detailUrl;

        return ParsedRawJob.builder()
                .sourceJobId(extractSourceJobId(detailUrl))
                .title(text(doc, ".job-title"))
                .companyName(text(doc, ".company"))
                .salaryText(text(doc, ".salary"))
                .locationText(text(doc, ".location"))
                .experienceText(text(doc, ".experience"))
                .seniorityText(text(doc, ".seniority"))
                .jobTypeText(text(doc, ".job-type"))
                .deadlineText(text(doc, ".deadline"))
                .postedText(text(doc, ".posted-at"))
                .skills(doc.select(".skills .skill").eachText())
                .descriptionText(text(doc, ".description"))
                .requirementsText(text(doc, ".requirements"))
                .benefitsText(text(doc, ".benefits"))
                .applyUrl(applyUrl)
                .applyType(ApplyType.DETAIL_PAGE)
                .build();
    }

    private String text(Document doc, String selector) {
        return doc.selectFirst(selector) != null
                ? doc.selectFirst(selector).text().trim()
                : null;
    }

    private String extractSourceJobId(String detailUrl) {
        try {
            String path = URI.create(detailUrl).getPath();

            if (path == null || path.isBlank()) {
                return null;
            }

            String lastSegment = path.substring(path.lastIndexOf('/') + 1);

            return lastSegment
                    .replace(".html", "")
                    .replace(".htm", "")
                    .trim();
        } catch (Exception e) {
            return null;
        }
    }
}