package com.autojob.modules.jobcrawler.controller;

import com.autojob.modules.jobcrawler.repository.SourceDiscoveryResultRepository;
import com.autojob.modules.jobcrawler.service.SourceDiscoveryService;
import com.autojob.modules.jobcrawler.sourcediscovery.SourceDiscoveryResult;
import com.autojob.modules.jobcrawler.sourcediscovery.WebsiteSource;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/source-discovery")
@RequiredArgsConstructor
public class SourceDiscoveryController {

    private final SourceDiscoveryService sourceDiscoveryService;
    private final SourceDiscoveryResultRepository resultRepository;

    @PostMapping("/website-sources")
    public WebsiteSource create(@RequestBody CreateWebsiteSourceRequest request) {
        return sourceDiscoveryService.createWebsiteSource(request.sourceCode(), request.domain());
    }

    @PostMapping("/website-sources/{id}/run")
    public List<SourceDiscoveryResult> run(@PathVariable String id) {
        return sourceDiscoveryService.runDiscovery(id);
    }

    @GetMapping("/website-sources/{id}/results")
    public List<SourceDiscoveryResult> results(@PathVariable String id) {
        return resultRepository.findByWebsiteSourceId(id);
    }

    public record CreateWebsiteSourceRequest(String sourceCode, String domain) {
    }
}