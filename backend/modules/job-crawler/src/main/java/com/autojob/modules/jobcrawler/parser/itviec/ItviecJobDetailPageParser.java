package com.autojob.modules.jobcrawler.parser.itviec;

import com.autojob.common.dtos.ApplyType;
import com.autojob.modules.jobcrawler.parser.JobDetailPageParser;
import com.autojob.modules.jobcrawler.domain.ParsedRawJob;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ItviecJobDetailPageParser implements JobDetailPageParser {

    private final ObjectMapper objectMapper;

    @Override
    public String sourceCode() {
        return "ITVIEC";
    }

    @Override
    public ParsedRawJob parseDetail(String detailUrl, String html) {
        Document doc = Jsoup.parse(html, detailUrl);
        JsonNode jobPosting = findJobPostingJsonLd(doc);

        String title = firstNonBlank(
                text(jobPosting, "title"),
                text(doc, "h1")
        );

        String companyName = firstNonBlank(
                text(jobPosting.path("hiringOrganization"), "name"),
                text(doc, "h3")
        );

        String descriptionText = firstNonBlank(
                sectionText(doc, "Job description"),
                htmlToText(text(jobPosting, "description"))
        );

        String requirementsText = firstNonBlank(
                sectionText(doc, "Your skills and experience"),
                extractBetweenFromText(
                        htmlToText(text(jobPosting, "description")),
                        "Your Skills and Experience",
                        "Why You'll Love Working Here"
                )
        );

        String benefitsText = firstNonBlank(
                sectionText(doc, "Why you'll love working here"),
                htmlToText(text(jobPosting, "jobBenefits"))
        );

        String skillsText = text(jobPosting, "skills");
        String locationText = parseLocations(jobPosting.path("jobLocation"));
        String salaryText = parseSalaryText(jobPosting.path("baseSalary"));
        String experienceText = parseExperienceText(jobPosting.path("experienceRequirements"));

        String postedText = text(jobPosting, "datePosted");
        String deadlineText = text(jobPosting, "validThrough");
        String jobTypeText = text(jobPosting, "employmentType");

        return ParsedRawJob.builder()
                .sourceJobId(extractSourceJobId(detailUrl))
                .title(clean(title))
                .companyName(clean(companyName))
                .salaryText(clean(salaryText))
                .locationText(clean(locationText))
                .experienceText(clean(experienceText))
                .seniorityText(inferSeniority(title))
                .jobTypeText(clean(jobTypeText))
                .deadlineText(clean(deadlineText))
                .postedText(clean(postedText))
                .skills(parseSkills(skillsText))
                .descriptionText(clean(descriptionText))
                .requirementsText(clean(requirementsText))
                .benefitsText(clean(benefitsText))

                // Không gọi apply endpoint riêng vì có thể cần session/login.
                // Lưu detailUrl để user mở trang và tự bấm Apply.
                .applyUrl(detailUrl)
                .applyType(ApplyType.DETAIL_PAGE)
                .build();
    }

    private JsonNode findJobPostingJsonLd(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            String json = script.data();

            if (json == null || json.isBlank()) {
                json = script.html();
            }

            if (json == null || json.isBlank()) {
                continue;
            }

            try {
                JsonNode node = objectMapper.readTree(json);

                if ("JobPosting".equalsIgnoreCase(text(node, "@type"))) {
                    return node;
                }
            } catch (Exception ignored) {
                // Ignore invalid JSON-LD block.
            }
        }

        return objectMapper.createObjectNode();
    }

    private String parseSalaryText(JsonNode baseSalary) {
        if (baseSalary == null || baseSalary.isMissingNode() || baseSalary.isNull()) {
            return null;
        }

        String currency = text(baseSalary, "currency");
        JsonNode valueNode = baseSalary.path("value");

        String value = null;
        String unitText = null;

        if (valueNode.isObject()) {
            value = text(valueNode, "value");
            unitText = text(valueNode, "unitText");
        } else if (!valueNode.isMissingNode() && !valueNode.isNull()) {
            value = valueNode.asText();
        }

        String result = String.join(" ",
                safe(value),
                safe(currency),
                safe(unitText)
        ).trim();

        return result.isBlank() ? null : result;
    }

    private String parseExperienceText(JsonNode experienceRequirements) {
        if (experienceRequirements == null
                || experienceRequirements.isMissingNode()
                || experienceRequirements.isNull()) {
            return null;
        }

        JsonNode monthsNode = experienceRequirements.path("monthsOfExperience");

        if (!monthsNode.isMissingNode() && !monthsNode.isNull()) {
            int months = monthsNode.asInt();

            if (months <= 0) {
                return null;
            }

            if (months % 12 == 0) {
                return (months / 12) + "+ years";
            }

            return months + " months";
        }

        return experienceRequirements.asText(null);
    }

    private String parseLocations(JsonNode jobLocation) {
        List<String> locations = new ArrayList<>();

        if (jobLocation == null || jobLocation.isMissingNode() || jobLocation.isNull()) {
            return null;
        }

        if (jobLocation.isArray()) {
            for (JsonNode location : jobLocation) {
                addLocation(locations, location);
            }
        } else {
            addLocation(locations, jobLocation);
        }

        return locations.isEmpty()
                ? null
                : String.join(", ", locations.stream().distinct().toList());
    }

    private void addLocation(List<String> locations, JsonNode location) {
        JsonNode address = location.path("address");

        String region = firstNonBlank(
                text(address, "addressRegion"),
                text(address, "addressLocality"),
                text(address, "streetAddress")
        );

        if (region != null && !region.isBlank()) {
            locations.add(normalizeVietnamLocation(region));
        }
    }

    private String normalizeVietnamLocation(String value) {
        String cleaned = clean(value);

        if (cleaned == null) {
            return null;
        }

        String lower = cleaned.toLowerCase(Locale.ROOT);

        if (lower.contains("hồ chí minh") || lower.contains("ho chi minh")) {
            return "Ho Chi Minh";
        }

        if (lower.contains("hà nội") || lower.contains("ha noi")) {
            return "Ha Noi";
        }

        if (lower.contains("đà nẵng") || lower.contains("da nang")) {
            return "Da Nang";
        }

        return cleaned;
    }

    private List<String> parseSkills(String skillsText) {
        if (skillsText == null || skillsText.isBlank()) {
            return List.of();
        }

        return Arrays.stream(skillsText.split(","))
                .map(this::clean)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private String sectionText(Document doc, String heading) {
        for (Element h2 : doc.select("h2")) {
            String h2Text = clean(h2.text());

            if (h2Text == null || !h2Text.equalsIgnoreCase(heading)) {
                continue;
            }

            Element container = h2.parent();

            if (container == null) {
                return null;
            }

            String text = clean(container.text());

            if (text == null) {
                return null;
            }

            return clean(text.replaceFirst("(?i)^" + java.util.regex.Pattern.quote(heading), ""));
        }

        return null;
    }

    private String extractBetweenFromText(String text, String startMarker, String endMarker) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        String startLower = startMarker.toLowerCase(Locale.ROOT);
        String endLower = endMarker.toLowerCase(Locale.ROOT);

        int start = lower.indexOf(startLower);

        if (start < 0) {
            return null;
        }

        start = start + startMarker.length();

        int end = lower.indexOf(endLower, start);

        if (end < 0) {
            return clean(text.substring(start));
        }

        return clean(text.substring(start, end));
    }

    private String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        return clean(Jsoup.parse(html).text());
    }

    private String extractSourceJobId(String detailUrl) {
        try {
            String path = URI.create(detailUrl).getPath();

            if (path == null || path.isBlank()) {
                return null;
            }

            String lastSegment = path.substring(path.lastIndexOf('/') + 1);

            return clean(lastSegment);
        } catch (Exception e) {
            return null;
        }
    }

    private String inferSeniority(String title) {
        if (title == null) {
            return null;
        }

        String lower = title.toLowerCase(Locale.ROOT);

        if (lower.contains("principal")) {
            return "PRINCIPAL";
        }

        if (lower.contains("lead") || lower.contains("leader")) {
            return "LEAD";
        }

        if (lower.contains("senior")) {
            return "SENIOR";
        }

        if (lower.contains("junior")) {
            return "JUNIOR";
        }

        if (lower.contains("intern") || lower.contains("fresher")) {
            return "INTERN";
        }

        return null;
    }

    private String text(Document doc, String selector) {
        Element element = doc.selectFirst(selector);

        if (element == null) {
            return null;
        }

        return clean(element.text());
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        JsonNode value = node.path(fieldName);

        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        return clean(value.asText());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value
                .replace('\u00A0', ' ')
                .replace("&nbsp;", " ")
                .trim()
                .replaceAll("\\s+", " ");

        return cleaned.isBlank() ? null : cleaned;
    }
}