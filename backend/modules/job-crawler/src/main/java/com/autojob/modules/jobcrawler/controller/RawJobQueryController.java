package com.autojob.modules.jobcrawler.controller;

import com.autojob.common.dtos.ApplyType;
import com.autojob.modules.jobcrawler.controller.RawJobPipelineStatusProvider.PipelineStageStatus;
import com.autojob.modules.jobcrawler.controller.RawJobPipelineStatusProvider.RawJobPipelineStatus;
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
import java.util.Map;

@RestController
@RequestMapping("/api/raw-jobs")
@RequiredArgsConstructor
public class RawJobQueryController {

    private final RawJobRepository rawJobRepository;
    private final RawJobPipelineStatusProvider pipelineStatusProvider;

    @GetMapping
    public List<RawJobSummaryResponse> list(
            @RequestParam(
                    name = "limit",
                    defaultValue = "20"
            )
            int limit
    ) {
        int safeLimit =
                Math.min(
                        Math.max(limit, 1),
                        100
                );

        List<RawJob> rawJobs = rawJobRepository
                .findAll(
                        PageRequest.of(
                                0,
                                safeLimit,
                                Sort.by(
                                        Sort.Direction.DESC,
                                        "collectedAt"
                                )
                        )
                )
                .getContent();

        /*
         * Trạng thái normalized/embedding được resolve theo batch thay vì
         * query từng row. Với limit 100, cách này giữ số query cố định
         * khi dashboard admin mở danh sách jobs.
         */
        Map<String, RawJobPipelineStatus> statuses =
                pipelineStatusProvider.getStatuses(
                        rawJobs
                                .stream()
                                .map(RawJob::getId)
                                .toList()
                );

        return rawJobs
                .stream()
                .map(
                        rawJob -> RawJobSummaryResponse.from(
                                rawJob,
                                statuses.getOrDefault(
                                        rawJob.getId(),
                                        RawJobPipelineStatus.empty()
                                )
                        )
                )
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
            Instant rawPayloadPurgedAt,

            PipelineStageStatus normalizationStatus,
            String normalizedJobId,
            String normalizationVersion,
            Instant normalizedAt,

            PipelineStageStatus embeddingStatus,
            String embeddingJobId,
            String embeddingVersion,
            Instant embeddedAt,
            String embeddingLastError
    ) {

        static RawJobSummaryResponse from(
                RawJob rawJob,
                RawJobPipelineStatus pipelineStatus
        ) {
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
                    rawJob.getRawPayloadPurgedAt(),

                    pipelineStatus.normalizationStatus(),
                    pipelineStatus.normalizedJobId(),
                    pipelineStatus.normalizationVersion(),
                    pipelineStatus.normalizedAt(),

                    pipelineStatus.embeddingStatus(),
                    pipelineStatus.embeddingJobId(),
                    pipelineStatus.embeddingVersion(),
                    pipelineStatus.embeddedAt(),
                    pipelineStatus.embeddingLastError()
            );
        }
    }
}