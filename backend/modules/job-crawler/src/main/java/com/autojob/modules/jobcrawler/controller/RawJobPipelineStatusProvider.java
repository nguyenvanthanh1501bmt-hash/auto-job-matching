package com.autojob.modules.jobcrawler.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Port đọc trạng thái xử lý downstream của raw job.
 *
 * Crawler chỉ sở hữu raw_jobs nên không được phụ thuộc ngược vào
 * normalizer/embedding. Implementation thực tế được đặt ở app layer,
 * nơi đã có quyền nhìn thấy toàn bộ module của pipeline.
 */
public interface RawJobPipelineStatusProvider {

    Map<String, RawJobPipelineStatus> getStatuses(
            List<String> rawJobIds
    );

    enum PipelineStageStatus {
        NOT_CREATED,
        OUTDATED,
        PROCESSING,
        READY,
        FAILED
    }

    record RawJobPipelineStatus(
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
        public static RawJobPipelineStatus empty() {
            return new RawJobPipelineStatus(
                    PipelineStageStatus.NOT_CREATED,
                    null,
                    null,
                    null,
                    PipelineStageStatus.NOT_CREATED,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}