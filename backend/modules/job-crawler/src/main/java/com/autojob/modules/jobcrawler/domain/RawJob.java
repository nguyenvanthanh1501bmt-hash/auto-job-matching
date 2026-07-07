package com.autojob.modules.jobcrawler.domain;

import com.autojob.common.dtos.ApplyType;
import lombok.*;
import org.springframework.data.annotation.Id;
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
public class RawJob {

    @Id
    private String id;

    private String sourceCode;
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

    private String rawHtml;
    private String rawText;

    @Indexed(unique = true)
    private String fingerprint;

    private Instant collectedAt;
}