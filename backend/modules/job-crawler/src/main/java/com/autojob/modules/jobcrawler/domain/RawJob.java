package com.autojob.modules.jobcrawler.domain;

import com.autojob.common.dtos.ApplyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("raw_jobs")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_raw_jobs_source_source_job_id",
                def = "{'sourceCode': 1, 'sourceJobId': 1}"
        ),
        @CompoundIndex(
                name = "idx_raw_jobs_source_collected_at",
                def = "{'sourceCode': 1, 'collectedAt': -1}"
        )
})
public class RawJob {

    @Id
    private String id;

    private String sourceCode;

    /**
     * ID gốc từ website.
     */
    private String sourceJobId;

    private String sourceUrl;
    private String listUrl;
    private String detailUrl;
    private String applyUrl;
    private ApplyType applyType;

    private String title;
    private String companyName;
    private String salaryText;
    private String locationText;
    private String experienceText;
    private String seniorityText;
    private String jobTypeText;
    private String deadlineText;
    private String postedText;

    private List<String> skills;

    private String descriptionText;
    private String requirementsText;
    private String benefitsText;

    /**
     * Chỉ lưu tạm khi bật debug crawler.
     * Raw payload có thể được purge sau normalization,
     * nhưng document RawJob vẫn được giữ.
     */
    private String rawHtml;

    /**
     * Text gốc đã strip từ HTML/detail page.
     * Có thể được purge sau normalization.
     */
    private String rawText;

    /**
     * Thời điểm rawHtml/rawText được purge gần nhất.
     */
    private Instant rawPayloadPurgedAt;

    /**
     * Unique key chống duplicate.
     */
    @Indexed(unique = true)
    private String fingerprint;

    private Instant firstSeenAt;
    private Instant lastSeenAt;

    /**
     * Legacy metadata field.
     *
     * Quan trọng:
     * field này KHÔNG còn @Indexed TTL.
     *
     * MongoDB sẽ không dùng field này để tự xóa RawJob.
     *
     * RawJobService cũng sẽ clear field này về null.
     *
     * Giữ field tạm thời để tương thích document/test cũ.
     */
    private Instant expiresAt;

    private Instant collectedAt;
}