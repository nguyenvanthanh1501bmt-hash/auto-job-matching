package com.autojob.modules.jobcrawler.parser.vieclam24h;

import com.autojob.common.dtos.ApplyType;
import com.autojob.modules.jobcrawler.parser.JobDetailPageParser;
import com.autojob.modules.jobcrawler.domain.ParsedRawJob;
import com.autojob.modules.jobcrawler.parser.SchemaOrgJobPostingParserSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Vieclam24hJobDetailPageParser extends SchemaOrgJobPostingParserSupport implements JobDetailPageParser {

    private static final Pattern SOURCE_JOB_ID_PATTERN = Pattern.compile("id(\\d+)\\.html$");

    public Vieclam24hJobDetailPageParser(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String sourceCode() {
        return "VIECLAM24H";
    }

    @Override
    public ParsedRawJob parseDetail(String detailUrl, String html) {
        Document doc = Jsoup.parse(html, detailUrl);
        JsonNode jobPosting = findJobPostingJsonLd(doc);

        String descriptionHtml = text(jobPosting, "description");
        String jobBenefitsHtml = text(jobPosting, "jobBenefits");

        String descriptionText = firstNonBlank(
                sectionFromHtmlText(
                        descriptionHtml,
                        "Mô tả công việc:",
                        "Yêu cầu công việc:"
                ),
                htmlToText(descriptionHtml)
        );

        String requirementsText = firstNonBlank(
                sectionFromHtmlText(descriptionHtml, "Yêu cầu công việc:"),
                htmlToText(text(jobPosting, "qualifications"))
        );

        String benefitsText = htmlToText(jobBenefitsHtml);

        return ParsedRawJob.builder()
                .sourceJobId(extractSourceJobId(detailUrl))
                .title(clean(firstNonBlank(
                        text(jobPosting, "title"),
                        text(doc, "h1")
                )))
                .companyName(clean(firstNonBlank(
                        text(jobPosting.path("hiringOrganization"), "name"),
                        text(jobPosting.path("identifier"), "name")
                )))
                .salaryText(clean(parseSalaryText(jobPosting.path("baseSalary"))))
                .locationText(clean(parseLocations(jobPosting.path("jobLocation"))))
                .experienceText(clean(parseExperienceText(jobPosting.path("experienceRequirements"))))
                .seniorityText(clean(text(jobPosting, "occupationalCategory")))
                .jobTypeText(clean(jsonNodeToText(jobPosting.path("employmentType"))))
                .deadlineText(clean(text(jobPosting, "validThrough")))
                .postedText(clean(text(jobPosting, "datePosted")))
                .skills(parseSkills(jobPosting))
                .descriptionText(clean(descriptionText))
                .requirementsText(clean(requirementsText))
                .benefitsText(clean(benefitsText))
                .applyUrl(detailUrl)
                .applyType(ApplyType.DETAIL_PAGE)
                .build();
    }

    private List<String> parseSkills(JsonNode jobPosting) {
        return parseCsvTags(text(jobPosting, "industry"));
    }

    private String extractSourceJobId(String detailUrl) {
        try {
            String path = URI.create(detailUrl).getPath();

            if (path == null || path.isBlank()) {
                return null;
            }

            String lastSegment = path.substring(path.lastIndexOf('/') + 1);
            Matcher matcher = SOURCE_JOB_ID_PATTERN.matcher(lastSegment);

            if (matcher.find()) {
                return matcher.group(1);
            }

            return clean(lastSegment);
        } catch (Exception e) {
            return null;
        }
    }
}