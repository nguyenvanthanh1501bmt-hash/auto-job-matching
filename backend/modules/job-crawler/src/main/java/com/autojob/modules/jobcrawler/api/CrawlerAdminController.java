package com.autojob.modules.jobcrawler.api;

import com.autojob.modules.jobcrawler.repository.RawJobRepository;
import lombok.RequiredArgsConstructor;
import org.apache.camel.ProducerTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/crawlers")
@RequiredArgsConstructor
public class CrawlerAdminController {

    private final ProducerTemplate producerTemplate;
    private final RawJobRepository rawJobRepository;

    @PostMapping("/mock/run")
    public CrawlRunResponse runMockCrawler() {
        long before = rawJobRepository.count();

        producerTemplate.requestBody("direct:crawl-mock-jobs", (Object) null);

        long after = rawJobRepository.count();

        return new CrawlRunResponse("MOCK", after - before, after);
    }

    public record CrawlRunResponse(
            String sourceCode,
            long insertedCount,
            long totalRawJobs
    ) {
    }
}