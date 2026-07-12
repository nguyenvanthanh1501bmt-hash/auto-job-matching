package com.autojob.modules.jobnormalizer.domain;

import com.autojob.common.dtos.ApplyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "normalized_jobs")
@CompoundIndexes({
        @CompoundIndex(
                name = "uk_raw_job_normalization_version",
                def = "{'rawJobId': 1, 'normalizationVersion': 1}",
                unique = true
        ),
        @CompoundIndex(
                name = "idx_normalized_jobs_source_version",
                def = "{'sourceCode': 1, 'normalizationVersion': 1}"
        ),
        @CompoundIndex(
                name = "idx_normalized_jobs_normalized_at",
                def = "{'normalizedAt': -1}"
        )
})
public class NormalizedJob {

    @Id
    private String id;

    /**
     * ID của document trong raw_jobs.
     */
    private String rawJobId;

    private String sourceCode;
    private String sourceJobId;

    /**
     * Fingerprint nhận diện job bên nguồn.
     *
     * Lưu để trace từ normalized_jobs về crawler data,
     * không dùng fingerprint này thay thế rawJobId.
     */
    private String sourceFingerprint;

    /**
     * Hash của các field nghiệp vụ tại thời điểm normalize.
     *
     * Sau này dùng để nhận biết raw job có thực sự thay đổi
     * hay chỉ thay đổi lastSeenAt/collectedAt.
     */
    private String rawContentHash;

    private String title;
    private String companyName;

    @Builder.Default
    private List<String> skills = List.of();

    @Builder.Default
    private List<String> locations = List.of();

    /**
     * Giữ nguyên text location từ raw job.
     */
    private String locationText;

    /**
     * Giữ nguyên text salary từ raw job.
     */
    private String salaryText;

    private Long salaryMin;
    private Long salaryMax;
    private String currency;

    private Double experienceMin;
    private Double experienceMax;

    @Builder.Default
    private SeniorityLevel seniority = SeniorityLevel.UNKNOWN;

    @Builder.Default
    private NormalizedJobType jobType = NormalizedJobType.UNKNOWN;

    private String descriptionText;
    private String requirementsText;
    private String benefitsText;

    private String detailUrl;
    private String applyUrl;

    @Builder.Default
    private ApplyType applyType = ApplyType.UNKNOWN;

    /**
     * Text đã chuẩn bị sẵn cho embedding service.
     *
     * Task hiện tại chỉ tạo text, chưa gọi embedding service.
     */
    private String embeddingText;

    /**
     * Ví dụ:
     * rule-v1
     * rule-v2
     */
    private String normalizationVersion;

    private Instant postedAt;
    private Instant deadlineAt;

    /**
     * Thời điểm document normalized được tạo hoặc cập nhật.
     */
    private Instant normalizedAt;
}