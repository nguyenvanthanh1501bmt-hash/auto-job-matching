package com.autojob.modules.jobcrawler.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class SchemaOrgJobPostingParserSupport {

    protected final ObjectMapper objectMapper;

    protected SchemaOrgJobPostingParserSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected JsonNode findJobPostingJsonLd(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            String json = script.data();

            if (json == null || json.isBlank()) {
                json = script.html();
            }

            if (json == null || json.isBlank()) {
                continue;
            }

            try {
                JsonNode root = objectMapper.readTree(json);
                JsonNode found = findJobPostingNode(root);

                if (found != null) {
                    return found;
                }
            } catch (Exception ignored) {
                // Ignore invalid JSON-LD block.
            }
        }

        return objectMapper.createObjectNode();
    }

    private JsonNode findJobPostingNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        if (isJobPosting(node)) {
            return node;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode found = findJobPostingNode(item);

                if (found != null) {
                    return found;
                }
            }
        }

        JsonNode graph = node.path("@graph");

        if (graph.isArray()) {
            for (JsonNode item : graph) {
                JsonNode found = findJobPostingNode(item);

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private boolean isJobPosting(JsonNode node) {
        JsonNode type = node.path("@type");

        if (type.isTextual()) {
            return "JobPosting".equalsIgnoreCase(type.asText());
        }

        if (type.isArray()) {
            for (JsonNode item : type) {
                if ("JobPosting".equalsIgnoreCase(item.asText())) {
                    return true;
                }
            }
        }

        return false;
    }

    protected String parseSalaryText(JsonNode baseSalary) {
        if (baseSalary == null || baseSalary.isMissingNode() || baseSalary.isNull()) {
            return null;
        }

        String currency = text(baseSalary, "currency");
        JsonNode valueNode = baseSalary.path("value");

        String value = null;
        String unitText = null;

        if (valueNode.isObject()) {
            String minValue = text(valueNode, "minValue");
            String maxValue = text(valueNode, "maxValue");
            String rawValue = text(valueNode, "value");

            if (minValue != null && maxValue != null) {
                value = minValue + " - " + maxValue;
            } else {
                value = rawValue;
            }

            unitText = text(valueNode, "unitText");
        } else {
            value = jsonNodeToText(valueNode);
        }

        return joinNonBlank(value, currency, unitText);
    }

    protected String parseLocations(JsonNode jobLocation) {
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

        String region = text(address, "addressRegion");
        String locality = text(address, "addressLocality");
        String street = text(address, "streetAddress");

        String value;

        if (region != null && locality != null
                && !containsIgnoreCase(region, locality)
                && !containsIgnoreCase(locality, region)) {
            value = region + " - " + locality;
        } else {
            value = firstNonBlank(region, locality, street);
        }

        if (value != null && !value.isBlank()) {
            locations.add(value);
        }
    }

    protected String parseExperienceText(JsonNode experienceRequirements) {
        if (experienceRequirements == null
                || experienceRequirements.isMissingNode()
                || experienceRequirements.isNull()) {
            return null;
        }

        String months = text(experienceRequirements, "monthsOfExperience");

        if (months != null && !months.isBlank()) {
            return months + " months";
        }

        return jsonNodeToText(experienceRequirements);
    }

    protected List<String> parseCsvTags(String csvText) {
        List<String> values = new ArrayList<>();

        String cleanedText = clean(csvText);

        if (cleanedText == null) {
            return values;
        }

        for (String item : cleanedText.split("[,;]")) {
            String cleaned = clean(item);

            if (cleaned != null && !cleaned.isBlank()) {
                values.add(cleaned);
            }
        }

        return values.stream()
                .distinct()
                .toList();
    }

    protected String sectionFromHtmlText(String html, String heading, String... stopHeadings) {
        String text = htmlToText(html);

        if (text == null || heading == null || heading.isBlank()) {
            return null;
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerHeading = heading.toLowerCase(Locale.ROOT);

        int headingIndex = lowerText.indexOf(lowerHeading);

        if (headingIndex < 0) {
            return null;
        }

        int start = headingIndex + heading.length();
        int end = text.length();

        for (String stopHeading : stopHeadings) {
            if (stopHeading == null || stopHeading.isBlank()) {
                continue;
            }

            int stopIndex = lowerText.indexOf(stopHeading.toLowerCase(Locale.ROOT), start);

            if (stopIndex >= 0 && stopIndex < end) {
                end = stopIndex;
            }
        }

        return clean(text.substring(start, end));
    }

    protected String textBeforeHeading(String html, String heading) {
        String text = htmlToText(html);

        if (text == null || heading == null || heading.isBlank()) {
            return null;
        }

        int headingIndex = text.toLowerCase(Locale.ROOT)
                .indexOf(heading.toLowerCase(Locale.ROOT));

        if (headingIndex < 0) {
            return null;
        }

        return clean(text.substring(0, headingIndex));
    }

    protected String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        return clean(Jsoup.parse(html).text());
    }

    protected String metaContent(Document doc, String selector) {
        Element element = doc.selectFirst(selector);

        if (element == null) {
            return null;
        }

        return clean(element.attr("content"));
    }

    protected String text(Document doc, String selector) {
        Element element = doc.selectFirst(selector);

        if (element == null) {
            return null;
        }

        return clean(element.text());
    }

    protected String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        JsonNode value = node.path(fieldName);

        return jsonNodeToText(value);
    }

    protected String jsonNodeToText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        if (node.isArray()) {
            List<String> values = new ArrayList<>();

            for (JsonNode item : node) {
                String value = jsonNodeToText(item);

                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }

            return values.isEmpty() ? null : String.join(", ", values);
        }

        if (node.isValueNode()) {
            return clean(node.asText());
        }

        return null;
    }

    protected String joinNonBlank(String... values) {
        List<String> result = new ArrayList<>();

        for (String value : values) {
            String cleaned = clean(value);

            if (cleaned != null && !cleaned.isBlank()) {
                result.add(cleaned);
            }
        }

        return result.isEmpty() ? null : String.join(" ", result);
    }

    protected String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    protected String clean(String value) {
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

    private boolean containsIgnoreCase(String value, String part) {
        if (value == null || part == null) {
            return false;
        }

        return value.toLowerCase(Locale.ROOT)
                .contains(part.toLowerCase(Locale.ROOT));
    }
}