package com.autojob.modules.jobcrawler.parser.joboko;

import com.autojob.modules.jobcrawler.parser.JobListPageParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class JobokoJobListPageParser implements JobListPageParser {

    private static final Pattern JOB_DETAIL_PATH_PATTERN =
            Pattern.compile("^/viec-lam-.+-xvi\\d+$");

    @Override
    public String sourceCode() {
        return "JOBOKO";
    }

    @Override
    public List<String> parseDetailUrls(String baseUrl, String html) {
        Document doc = Jsoup.parse(html, baseUrl);

        return doc.select("a[href]")
                .stream()
                .map(element -> element.absUrl("href"))
                .filter(url -> url != null && !url.isBlank())
                .map(this::removeQueryAndFragment)
                .filter(this::isJobokoJobDetailUrl)
                .distinct()
                .toList();
    }

    private boolean isJobokoJobDetailUrl(String url) {
        try {
            URI uri = URI.create(url);

            String host = uri.getHost();
            String path = uri.getPath();

            return host != null
                    && host.equalsIgnoreCase("vn.joboko.com")
                    && path != null
                    && JOB_DETAIL_PATH_PATTERN.matcher(path).matches();
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
}