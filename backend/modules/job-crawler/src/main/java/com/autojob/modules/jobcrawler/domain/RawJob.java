package com.autojob.modules.jobcrawler.domain;

import com.autojob.common.dtos.ApplyType;
import lombok.*;
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
     * Ví dụ Vieclam24h:
     * detailUrl = "...id200847455.html"
     * sourceJobId = "200847455"
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
     * Free DB policy:
     * - rawHtml nên để null ở production/free DB
     * - chỉ bật khi debug crawler
     */
    private String rawHtml;

    /**
     * Text gốc đã strip từ HTML/detail page.
     * Có thể truncate 10k-30k chars nếu cần tiết kiệm DB.
     */
    private String rawText;

    /**
     * Unique key chống duplicate.
     * Với source có sourceJobId:
     * fingerprint = "VIECLAM24H:200847455"
     */
    @Indexed(unique = true)
    private String fingerprint;

    private Instant firstSeenAt;
    private Instant lastSeenAt;

    /**
     * Raw retention.
     * Ví dụ raw giữ 10 ngày:
     * expiresAt = collectedAt + 10 days
     */
    @Indexed
    private Instant expiresAt;

    private Instant collectedAt;
}