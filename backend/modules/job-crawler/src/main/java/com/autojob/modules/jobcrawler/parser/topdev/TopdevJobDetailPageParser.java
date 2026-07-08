package com.autojob.modules.jobcrawler.parser.topdev;

import com.autojob.common.dtos.ApplyType;
import com.autojob.modules.jobcrawler.parser.JobDetailPageParser;
import com.autojob.modules.jobcrawler.parser.ParsedRawJob;
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
public class TopdevJobDetailPageParser extends SchemaOrgJobPostingParserSupport implements JobDetailPageParser {

    private static final Pattern SOURCE_JOB_ID_PATTERN = Pattern.compile("-(\\d+)$");
    private static final Pattern SENIORITY_PATTERN = Pattern.compile("(?i)hiring level\\s+([^,]+)");

    public TopdevJobDetailPageParser(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String sourceCode() {
        return "TOPDEV";
    }

    @Override
    public ParsedRawJob parseDetail(String detailUrl, String html) {
        Document doc = Jsoup.parse(html, detailUrl);
        JsonNode jobPosting = findJobPostingJsonLd(doc);

        String descriptionHtml = text(jobPosting, "description");
        String jobBenefitsHtml = text(jobPosting, "jobBenefits");

        String descriptionText = firstNonBlank(
                textBeforeHeading(descriptionHtml, "Your role & responsibilities"),
                htmlToText(descriptionHtml)
        );

        String requirementsText = sectionFromHtmlText(
                descriptionHtml,
                "Your skills & qualifications",
                "Benefits for you"
        );

        String benefitsText = firstNonBlank(
                htmlToText(jobBenefitsHtml),
                sectionFromHtmlText(descriptionHtml, "Benefits for you")
        );

        return ParsedRawJob.builder()
                .sourceJobId(extractSourceJobId(detailUrl))
                .title(clean(text(jobPosting, "title")))
                .companyName(clean(firstNonBlank(
                        text(jobPosting.path("hiringOrganization"), "name"),
                        text(jobPosting.path("identifier"), "name")
                )))
                .salaryText(clean(parseSalaryText(jobPosting.path("baseSalary"))))
                .locationText(clean(parseLocations(jobPosting.path("jobLocation"))))
                .experienceText(clean(parseExperienceText(jobPosting.path("experienceRequirements"))))
                .seniorityText(clean(parseSeniority(doc)))
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
        List<String> skills = parseCsvTags(text(jobPosting, "skills"));

        String industry = clean(text(jobPosting, "industry"));

        if (industry != null && !industry.isBlank() && !skills.contains(industry)) {
            skills = new java.util.ArrayList<>(skills);
            skills.add(industry);
        }

        return skills.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private String parseSeniority(Document doc) {
        String keywords = metaContent(doc, "meta[name=keywords]");

        if (keywords == null || keywords.isBlank()) {
            return null;
        }

        Matcher matcher = SENIORITY_PATTERN.matcher(keywords);

        if (matcher.find()) {
            return clean(matcher.group(1));
        }

        return null;
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