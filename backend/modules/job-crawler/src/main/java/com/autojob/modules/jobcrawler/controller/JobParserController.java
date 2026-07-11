package com.autojob.modules.jobcrawler.controller;

import com.autojob.common.dtos.ApplyType;
import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.parser.JobSourceParserRegistry;
import com.autojob.modules.jobcrawler.parser.ParsedRawJob;
import com.autojob.modules.jobcrawler.service.RawJobService;
import com.autojob.modules.jobcrawler.util.FingerprintUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/parsers")
@RequiredArgsConstructor
public class JobParserController {

    private final JobSourceParserRegistry parserRegistry;
    private final RawJobService rawJobService;

    @PostMapping("/{sourceCode}/list-file")
    public ListFileParseResponse parseListFile(
            @PathVariable("sourceCode") String sourceCode,
            @RequestBody ListFileParseRequest request
    ) throws Exception {
        String normalizedSourceCode = normalizeSourceCode(sourceCode);
        String html = Files.readString(Path.of(request.filePath()), StandardCharsets.UTF_8);

        List<String> detailUrls = parserRegistry
                .getListParser(normalizedSourceCode)
                .parseDetailUrls(request.baseUrl(), html);

        return new ListFileParseResponse(
                normalizedSourceCode,
                detailUrls.size(),
                detailUrls
        );
    }

    @PostMapping("/{sourceCode}/detail-file")
    public DetailFileParseResponse parseDetailFile(
            @PathVariable("sourceCode") String sourceCode,
            @RequestBody DetailFileParseRequest request
    ) throws Exception {
        String normalizedSourceCode = normalizeSourceCode(sourceCode);
        String html = Files.readString(Path.of(request.filePath()), StandardCharsets.UTF_8);

        ParsedRawJob parsed = parserRegistry
                .getDetailParser(normalizedSourceCode)
                .parseDetail(request.detailUrl(), html);

        RawJob saved = saveParsedRawJob(
                normalizedSourceCode,
                parsed,
                request.detailUrl(),
                request.listUrl(),
                request.rawRetentionDays()
        );

        return DetailFileParseResponse.from(saved);
    }

    private RawJob saveParsedRawJob(
            String sourceCode,
            ParsedRawJob parsed,
            String detailUrl,
            String listUrl,
            Integer rawRetentionDaysValue
    ) {
        Instant now = Instant.now();

        String fingerprint = buildFingerprint(
                sourceCode,
                parsed.sourceJobId(),
                detailUrl,
                parsed.title(),
                parsed.companyName()
        );

        int rawRetentionDays = rawRetentionDaysValue != null
                ? rawRetentionDaysValue
                : 7;

        RawJob rawJob = RawJob.builder()
                .sourceCode(sourceCode)
                .sourceJobId(parsed.sourceJobId())
                .sourceUrl(detailUrl)
                .listUrl(listUrl)
                .detailUrl(detailUrl)
                .applyUrl(valueOrDefault(parsed.applyUrl(), detailUrl))
                .applyType(parsed.applyType() != null ? parsed.applyType() : ApplyType.DETAIL_PAGE)

                .title(parsed.title())
                .companyName(parsed.companyName())
                .salaryText(parsed.salaryText())
                .locationText(parsed.locationText())
                .experienceText(parsed.experienceText())
                .seniorityText(parsed.seniorityText())
                .jobTypeText(parsed.jobTypeText())
                .deadlineText(parsed.deadlineText())
                .postedText(parsed.postedText())
                .skills(parsed.skills())
                .descriptionText(parsed.descriptionText())
                .requirementsText(parsed.requirementsText())
                .benefitsText(parsed.benefitsText())

                .rawHtml(null)
                .rawText(null)

                .fingerprint(fingerprint)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .collectedAt(now)
                .expiresAt(now.plus(rawRetentionDays, ChronoUnit.DAYS))
                .build();

        return rawJobService.upsertSeen(rawJob);
    }

    private String buildFingerprint(
            String sourceCode,
            String sourceJobId,
            String detailUrl,
            String title,
            String companyName
    ) {
        if (sourceJobId != null && !sourceJobId.isBlank()) {
            return sourceCode + ":" + sourceJobId;
        }

        return sourceCode + ":URL:" + FingerprintUtil.sha256(
                sourceCode + "|" + detailUrl + "|" + title + "|" + companyName
        );
    }

    private String normalizeSourceCode(String sourceCode) {
        return sourceCode == null
                ? ""
                : sourceCode.trim().toUpperCase(Locale.ROOT);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public record ListFileParseRequest(
            String filePath,
            String baseUrl
    ) {
    }

    public record ListFileParseResponse(
            String sourceCode,
            int detailUrlCount,
            List<String> detailUrls
    ) {
    }

    public record DetailFileParseRequest(
            String detailUrl,
            String filePath,
            String listUrl,
            Integer rawRetentionDays
    ) {
    }

    public record DetailFileParseResponse(
            String id,
            String sourceCode,
            String sourceJobId,
            String fingerprint,
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
            String detailUrl,
            String applyUrl,
            ApplyType applyType,
            boolean rawHtmlStored,
            boolean rawTextStored
    ) {
        static DetailFileParseResponse from(RawJob rawJob) {
            return new DetailFileParseResponse(
                    rawJob.getId(),
                    rawJob.getSourceCode(),
                    rawJob.getSourceJobId(),
                    rawJob.getFingerprint(),
                    rawJob.getTitle(),
                    rawJob.getCompanyName(),
                    rawJob.getSalaryText(),
                    rawJob.getLocationText(),
                    rawJob.getExperienceText(),
                    rawJob.getSeniorityText(),
                    rawJob.getJobTypeText(),
                    rawJob.getDeadlineText(),
                    rawJob.getPostedText(),
                    rawJob.getSkills(),
                    rawJob.getDetailUrl(),
                    rawJob.getApplyUrl(),
                    rawJob.getApplyType(),
                    rawJob.getRawHtml() != null,
                    rawJob.getRawText() != null
            );
        }
    }
}