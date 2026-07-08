package com.autojob.modules.jobcrawler.camel;

import com.autojob.modules.jobcrawler.parser.JobSourceParserRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListPageProcessor implements Processor {

    private final JobSourceParserRegistry parserRegistry;

    @Override
    public void process(Exchange exchange) {
        String html = exchange.getMessage().getBody(String.class);
        String sourceCode = exchange.getProperty("sourceCode", String.class);
        String baseUrl = exchange.getProperty("baseUrl", String.class);

        List<String> detailUrls = parserRegistry
                .getListParser(sourceCode)
                .parseDetailUrls(baseUrl, html);

        exchange.getMessage().setBody(detailUrls);
    }
}