package com.autojob.modules.jobcrawler.service;

import com.autojob.modules.jobcrawler.repository.SourceDiscoveryResultRepository;
import com.autojob.modules.jobcrawler.repository.WebsiteSourceRepository;
import com.autojob.modules.jobcrawler.sourcediscovery.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SourceDiscoveryService {

    private final WebsiteSourceRepository websiteSourceRepository;
    private final SourceDiscoveryResultRepository resultRepository;

    public WebsiteSource createWebsiteSource(String sourceCode, String domain) {
        WebsiteSource source = WebsiteSource.builder()
                .sourceCode(sourceCode)
                .domain(normalizeDomain(domain))
                .status(WebsiteSourceStatus.PENDING_DISCOVERY)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return websiteSourceRepository.save(source);
    }

    public List<SourceDiscoveryResult> runDiscovery(String websiteSourceId) {
        WebsiteSource source = websiteSourceRepository.findById(websiteSourceId)
                .orElseThrow(() -> new IllegalArgumentException("Website source not found: " + websiteSourceId));

        source.setStatus(WebsiteSourceStatus.DISCOVERING);
        source.setUpdatedAt(Instant.now());
        websiteSourceRepository.save(source);

        List<String> candidates = List.of(
                "https://" + source.getDomain() + "/careers",
                "https://" + source.getDomain() + "/jobs",
                "https://" + source.getDomain() + "/tuyen-dung",
                "https://" + source.getDomain() + "/viec-lam"
        );

        List<SourceDiscoveryResult> results = candidates.stream()
                .map(url -> SourceDiscoveryResult.builder()
                        .websiteSourceId(source.getId())
                        .sourceCode(source.getSourceCode())
                        .candidateUrl(url)
                        .detectionType("COMMON_PATH")
                        .status(SourceDiscoveryResultStatus.PENDING_REVIEW)
                        .discoveredAt(Instant.now())
                        .build())
                .map(resultRepository::save)
                .toList();

        source.setStatus(results.isEmpty()
                ? WebsiteSourceStatus.NO_CANDIDATE_FOUND
                : WebsiteSourceStatus.DISCOVERED);
        source.setUpdatedAt(Instant.now());
        websiteSourceRepository.save(source);

        return results;
    }

    private String normalizeDomain(String input) {
        return input
                .replace("https://", "")
                .replace("http://", "")
                .replaceAll("/+$", "")
                .trim()
                .toLowerCase();
    }
}