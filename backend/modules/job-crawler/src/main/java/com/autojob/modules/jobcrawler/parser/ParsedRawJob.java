package com.autojob.modules.jobcrawler.parser;

import com.autojob.common.dtos.ApplyType;
import lombok.Builder;

import java.util.List;

@Builder
public record ParsedRawJob(
        String sourceJobId,
        String title,
        String companyName,
        String salaryText,
        String locationText,
        String experienceText,
        String seniorityText,
        String jobTypeText,
        String deadlineText,
        String postedText,
        List<String> skills,
        String descriptionText,
        String requirementsText,
        String benefitsText,
        String applyUrl,
        ApplyType applyType
) {
}