package com.autojob.modules.jobcrawler.parser.joboko;

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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class JobokoJobDetailPageParser implements JobDetailPageParser {

    private static final Pattern SOURCE_JOB_ID_PATTERN = Pattern.compile("xvi(\\d+)$");

    private final ObjectMapper objectMapper;

    @Override
    public String sourceCode() {
        return "JOBOKO";
    }

    @Override
    public ParsedRawJob parseDetail(String detailUrl, String html) {
        Document doc = Jsoup.parse(html, detailUrl);
        JsonNode jobPosting = findJobPostingJsonLd(doc);

        String descriptionHtml = text(jobPosting, "description");

        String title = firstNonBlank(
                text(jobPosting, "title"),
                inputValue(doc, "input#jname"),
                text(doc, "h1")
        );

        String companyName = firstNonBlank(
                text(jobPosting.path("hiringOrganization"), "name"),
                inputValue(doc, "input#cname"),
                text(doc, ".nw-company-hero__text")
        );

        String descriptionText = firstNonBlank(
                sectionFromDescriptionHtml(descriptionHtml, "Mô tả công việc"),
                htmlToText(descriptionHtml)
        );

        String requirementsText = firstNonBlank(
                sectionFromDescriptionHtml(descriptionHtml, "Yêu cầu"),
                text(doc, ".job-requirement")
        );

        String benefitsText = firstNonBlank(
                sectionFromDescriptionHtml(descriptionHtml, "Quyền lợi"),
                text(doc, ".job-benefit")
        );

        return ParsedRawJob.builder()
                .sourceJobId(extractSourceJobId(detailUrl))
                .title(clean(title))
                .companyName(clean(companyName))
                .salaryText(clean(parseSalaryText(jobPosting.path("baseSalary"))))
                .locationText(clean(parseLocations(jobPosting.path("jobLocation"))))
                .experienceText(clean(firstNonBlank(
                        itemContentValue(doc, "Kinh nghiệm"),
                        parseExperienceFromRequirements(requirementsText)
                )))
                .seniorityText(clean(itemContentValue(doc, "Chức vụ")))
                .jobTypeText(clean(firstNonBlank(
                        jsonNodeToText(jobPosting.path("employmentType")),
                        itemContentValue(doc, "Loại hình")
                )))
                .deadlineText(clean(text(jobPosting, "validThrough")))
                .postedText(clean(text(jobPosting, "datePosted")))
                .skills(parseSkills(doc, text(jobPosting, "industry")))
                .descriptionText(clean(descriptionText))
                .requirementsText(clean(requirementsText))
                .benefitsText(clean(benefitsText))
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
            value = clean(valueNode.asText());
        }

        return joinNonBlank(value, currency, unitText);
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

        String value = firstNonBlank(
                text(address, "addressRegion"),
                text(address, "addressLocality"),
                text(address, "streetAddress")
        );

        if (value != null && !value.isBlank()) {
            locations.add(value);
        }
    }

    private String sectionFromDescriptionHtml(String descriptionHtml, String heading) {
        if (descriptionHtml == null || descriptionHtml.isBlank()) {
            return null;
        }

        Document descriptionDoc = Jsoup.parseBodyFragment(descriptionHtml);

        for (Element h3 : descriptionDoc.select("h3")) {
            String h3Text = clean(h3.text());

            if (h3Text == null || !h3Text.equalsIgnoreCase(heading)) {
                continue;
            }

            List<String> parts = new ArrayList<>();
            Element current = h3.nextElementSibling();

            while (current != null && !"h3".equalsIgnoreCase(current.tagName())) {
                String text = clean(current.text());

                if (text != null && !text.isBlank()) {
                    parts.add(text);
                }

                current = current.nextElementSibling();
            }

            return parts.isEmpty() ? null : clean(String.join(" ", parts));
        }

        return null;
    }

    private String parseExperienceFromRequirements(String requirementsText) {
        if (requirementsText == null || requirementsText.isBlank()) {
            return null;
        }

        Matcher matcher = Pattern.compile("(?iu)(\\d+)\\s*năm\\s+kinh nghiệm").matcher(requirementsText);

        if (matcher.find()) {
            return matcher.group(1) + " năm";
        }

        return null;
    }

    private String itemContentValue(Document doc, String label) {
        for (Element element : doc.select(".block-entry .item-content")) {
            String text = clean(element.text());

            if (text == null) {
                continue;
            }

            String normalizedPrefix = label.toLowerCase() + ":";
            String lower = text.toLowerCase();

            if (!lower.startsWith(normalizedPrefix)) {
                continue;
            }

            return clean(text.substring(normalizedPrefix.length()));
        }

        return null;
    }

    private List<String> parseSkills(Document doc, String industry) {
        List<String> skills = new ArrayList<>();

        for (String tag : doc.select(".block-tags a").eachText()) {
            String cleaned = clean(tag);

            if (cleaned != null && !cleaned.isBlank()) {
                skills.add(cleaned);
            }
        }

        String cleanedIndustry = clean(industry);

        if (cleanedIndustry != null && !cleanedIndustry.isBlank()) {
            skills.add(cleanedIndustry);
        }

        return skills.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
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

    private String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        return clean(Jsoup.parse(html).text());
    }

    private String text(Document doc, String selector) {
        Element element = doc.selectFirst(selector);

        if (element == null) {
            return null;
        }

        return clean(element.text());
    }

    private String inputValue(Document doc, String selector) {
        Element element = doc.selectFirst(selector);

        if (element == null) {
            return null;
        }

        return clean(element.attr("value"));
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

    private String jsonNodeToText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        if (node.isArray()) {
            List<String> values = new ArrayList<>();

            for (JsonNode item : node) {
                String value = clean(item.asText());

                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }

            return values.isEmpty() ? null : String.join(", ", values);
        }

        return clean(node.asText());
    }

    private String joinNonBlank(String... values) {
        List<String> result = new ArrayList<>();

        for (String value : values) {
            String cleaned = clean(value);

            if (cleaned != null && !cleaned.isBlank()) {
                result.add(cleaned);
            }
        }

        return result.isEmpty() ? null : String.join(" ", result);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
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