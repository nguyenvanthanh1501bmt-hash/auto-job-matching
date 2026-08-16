package com.autojob.modules.jobcrawler.controller;

import com.autojob.modules.jobcrawler.repository.RawJobRepository;
import lombok.RequiredArgsConstructor;
import org.apache.camel.ProducerTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/crawlers")
@RequiredArgsConstructor
public class CrawlerAdminController {

    private static final int DEFAULT_LIVE_LIMIT = 15;
    private static final int MAX_LIVE_LIMIT = 50;

    private static final Map<String, String> LIVE_ENDPOINTS = Map.of(
            "ITVIEC", "direct:crawl-live-itviec",
            "JOBOKO", "direct:crawl-live-joboko",
            "TOPDEV", "direct:crawl-live-topdev",
            "VIECLAM24H", "direct:crawl-live-vieclam24h"
    );

    private final ProducerTemplate producerTemplate;
    private final RawJobRepository rawJobRepository;

    @PostMapping("/mock/run")
    public CrawlRunResponse runMockCrawler() {
        long before = rawJobRepository.count();

        producerTemplate.requestBody(
                "direct:crawl-mock-jobs",
                (Object) null
        );

        long after = rawJobRepository.count();

        return new CrawlRunResponse(
                "MOCK",
                null,
                after - before,
                after
        );
    }

    @PostMapping("/live/{sourceCode}/run")
    public CrawlRunResponse runLiveCrawler(
            @PathVariable("sourceCode") String sourceCode,
            @RequestParam(
                    name = "limit",
                    defaultValue = "15"
            )
            int limit
    ) {
        String normalizedSourceCode = sourceCode
                .trim()
                .toUpperCase(Locale.ROOT);

        String endpoint = LIVE_ENDPOINTS.get(
                normalizedSourceCode
        );

        if (endpoint == null) {
            throw new IllegalArgumentException(
                    "Unsupported live crawler sourceCode="
                            + sourceCode
                            + ". Supported: "
                            + LIVE_ENDPOINTS.keySet()
            );
        }

        int safeLimit = normalizeLimit(limit);

        long before = rawJobRepository.count();

        producerTemplate.requestBodyAndHeader(
                endpoint,
                null,
                "maxJobs",
                safeLimit
        );

        long after = rawJobRepository.count();

        return new CrawlRunResponse(
                normalizedSourceCode,
                safeLimit,
                after - before,
                after
        );
    }

    private int normalizeLimit(int limit) {
        if (limit < 1) {
            return DEFAULT_LIVE_LIMIT;
        }

        return Math.min(
                limit,
                MAX_LIVE_LIMIT
        );
    }

    public record CrawlRunResponse(
            String sourceCode,
            Integer requestedLimit,
            long insertedCount,
            long totalRawJobs
    ) {
    }
}