package com.autojob.modules.jobcrawler.camel;

import com.autojob.modules.jobcrawler.config.MockCrawlerProperties;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class MockJobCrawlerRoute extends RouteBuilder {

    private final MockCrawlerProperties properties;
    private final ListPageProcessor listPageProcessor;
    private final DetailPageProcessor detailPageProcessor;

    public MockJobCrawlerRoute(
            MockCrawlerProperties properties,
            ListPageProcessor listPageProcessor,
            DetailPageProcessor detailPageProcessor
    ) {
        this.properties = properties;
        this.listPageProcessor = listPageProcessor;
        this.detailPageProcessor = detailPageProcessor;
    }

    @Override
    public void configure() {
        onException(Exception.class)
                .handled(true)
                .log("Mock crawler failed: ${exception.message}");

        from("direct:crawl-mock-jobs")
                .routeId("mock-job-list-crawler")
                .removeHeaders("*")
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .toD(properties.getListUrl())
                .process(listPageProcessor)
                .split(body())
                .setProperty("detailUrl", body())
                .delay(properties.getRequestDelayMs())
                .removeHeaders("*")
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .toD("${exchangeProperty.detailUrl}")
                .process(detailPageProcessor)
                .end();
    }
}