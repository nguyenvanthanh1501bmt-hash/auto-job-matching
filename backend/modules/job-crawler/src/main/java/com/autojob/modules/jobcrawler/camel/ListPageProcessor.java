package com.autojob.modules.jobcrawler.camel;

import com.autojob.modules.jobcrawler.config.MockCrawlerProperties;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListPageProcessor implements Processor {

    private final MockCrawlerProperties properties;

    @Override
    public void process(Exchange exchange) {
        String html = exchange.getMessage().getBody(String.class);

        Document doc = Jsoup.parse(html, properties.getBaseUrl());

        List<String> detailUrls = doc.select("[data-job-card] a.job-link[href]")
                .eachAttr("abs:href")
                .stream()
                .distinct()
                .toList();

        exchange.getMessage().setBody(detailUrls);
    }
}