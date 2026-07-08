package com.autojob.modules.jobcrawler.parser.itviec;

import com.autojob.modules.jobcrawler.parser.JobListPageParser;
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
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ItviecJobListPageParser implements JobListPageParser {

    private static final Pattern JOB_DETAIL_PATH_PATTERN =
            Pattern.compile("^/it-jobs/[^?#]+-\\d{4}$");

    private final ObjectMapper objectMapper;

    @Override
    public String sourceCode() {
        return "ITVIEC";
    }

    @Override
    public List<String> parseDetailUrls(String baseUrl, String html) {
        Document doc = Jsoup.parse(html, baseUrl);

        List<String> urls = new ArrayList<>();

        urls.addAll(parseFromItemListJsonLd(doc));
        urls.addAll(parseFromLinks(doc));

        return urls.stream()
                .map(this::removeQueryAndFragment)
                .filter(this::isItviecJobDetailUrl)
                .distinct()
                .toList();
    }

    private List<String> parseFromItemListJsonLd(Document doc) {
        List<String> urls = new ArrayList<>();

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

                if (!"ItemList".equalsIgnoreCase(text(node, "@type"))) {
                    continue;
                }

                JsonNode itemList = node.path("itemListElement");
                if (!itemList.isArray()) {
                    continue;
                }

                for (JsonNode item : itemList) {
                    String url = text(item, "url");
                    if (url != null && !url.isBlank()) {
                        urls.add(url);
                    }
                }
            } catch (Exception ignored) {
                // Ignore invalid JSON-LD.
            }
        }

        return urls;
    }

    private List<String> parseFromLinks(Document doc) {
        return doc.select("a[href]")
                .stream()
                .map(element -> element.absUrl("href"))
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private boolean isItviecJobDetailUrl(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();

            return path != null && JOB_DETAIL_PATH_PATTERN.matcher(path).matches();
        } catch (Exception e) {
            return false;
        }
    }

    private String removeQueryAndFragment(String url) {
        try {
            URI uri = URI.create(url);

            return new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    null,
                    null
            ).toString();
        } catch (Exception e) {
            return url;
        }
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        JsonNode value = node.path(fieldName);

        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        return value.asText();
    }
}