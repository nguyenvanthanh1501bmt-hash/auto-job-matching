package com.autojob.modules.jobcrawler.controller;

import com.autojob.common.dtos.ApplyType;
import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.repository.RawJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/raw-jobs")
@RequiredArgsConstructor
public class RawJobQueryController {

    private final RawJobRepository rawJobRepository;

    @GetMapping
    public List<RawJobSummaryResponse> list(
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);

        return rawJobRepository
                .findAll(PageRequest.of(
                        0,
                        safeLimit,
                        Sort.by(Sort.Direction.DESC, "collectedAt")
                ))
                .getContent()
                .stream()
                .map(RawJobSummaryResponse::from)
                .toList();
    }

    public record RawJobSummaryResponse(
            String id,
            String sourceCode,
            String sourceJobId,
            String fingerprint,
            String title,
            String companyName,
            String salaryText,
            String locationText,
            String experienceText,
            List<String> skills,
            String detailUrl,
            String applyUrl,
            ApplyType applyType,
            Instant firstSeenAt,
            Instant lastSeenAt,
            Instant collectedAt,
            Instant expiresAt
    ) {
        static RawJobSummaryResponse from(RawJob rawJob) {
            return new RawJobSummaryResponse(
                    rawJob.getId(),
                    rawJob.getSourceCode(),
                    rawJob.getSourceJobId(),
                    rawJob.getFingerprint(),
                    rawJob.getTitle(),
                    rawJob.getCompanyName(),
                    rawJob.getSalaryText(),
                    rawJob.getLocationText(),
                    rawJob.getExperienceText(),
                    rawJob.getSkills(),
                    rawJob.getDetailUrl(),
                    rawJob.getApplyUrl(),
                    rawJob.getApplyType(),
                    rawJob.getFirstSeenAt(),
                    rawJob.getLastSeenAt(),
                    rawJob.getCollectedAt(),
                    rawJob.getExpiresAt()
            );
        }
    }
}