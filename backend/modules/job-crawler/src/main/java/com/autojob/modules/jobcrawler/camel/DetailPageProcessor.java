package com.autojob.modules.jobcrawler.camel;

import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.parser.JobSourceParserRegistry;
import com.autojob.modules.jobcrawler.parser.ParsedRawJob;
import com.autojob.modules.jobcrawler.service.RawJobService;
import com.autojob.modules.jobcrawler.util.FingerprintUtil;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class DetailPageProcessor implements Processor {

    private final RawJobService rawJobService;
    private final JobSourceParserRegistry parserRegistry;

    @Override
    public void process(Exchange exchange) {
        String html = exchange.getMessage().getBody(String.class);

        String detailUrl = exchange.getProperty("detailUrl", String.class);
        String listUrl = exchange.getProperty("listUrl", String.class);
        String sourceCode = exchange.getProperty("sourceCode", String.class);

        boolean storeRawHtml = Boolean.TRUE.equals(exchange.getProperty("storeRawHtml", Boolean.class));
        boolean storeRawText = Boolean.TRUE.equals(exchange.getProperty("storeRawText", Boolean.class));

        Integer rawTextMaxCharsValue = exchange.getProperty("rawTextMaxChars", Integer.class);
        Integer rawRetentionDaysValue = exchange.getProperty("rawRetentionDays", Integer.class);

        int rawTextMaxChars = rawTextMaxCharsValue != null ? rawTextMaxCharsValue : 20000;
        int rawRetentionDays = rawRetentionDaysValue != null ? rawRetentionDaysValue : 3;

        ParsedRawJob parsed = parserRegistry
                .getDetailParser(sourceCode)
                .parseDetail(detailUrl, html);

        Instant now = Instant.now();

        String fingerprint = buildFingerprint(
                sourceCode,
                parsed.sourceJobId(),
                detailUrl,
                parsed.title(),
                parsed.companyName()
        );

        RawJob rawJob = RawJob.builder()
                .sourceCode(sourceCode)
                .sourceJobId(parsed.sourceJobId())
                .sourceUrl(detailUrl)
                .listUrl(listUrl)
                .detailUrl(detailUrl)
                .applyUrl(parsed.applyUrl() != null ? parsed.applyUrl() : detailUrl)
                .applyType(parsed.applyType())
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

                // Config false thì Mongo không lưu HTML/text.
                .rawHtml(storeRawHtml ? html : null)
                .rawText(storeRawText ? truncate(Jsoup.parse(html, detailUrl).text(), rawTextMaxChars) : null)

                .fingerprint(fingerprint)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .collectedAt(now)
                .expiresAt(now.plus(rawRetentionDays, ChronoUnit.DAYS))
                .build();

        RawJob saved = rawJobService.upsertSeen(rawJob);

        // Method kết thúc thì html chỉ còn là local variable, không save vào DB nếu config false.
        exchange.getMessage().setBody(saved.getId());
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

    private String truncate(String value, int maxChars) {
        if (value == null || maxChars <= 0 || value.length() <= maxChars) {
            return value;
        }

        return value.substring(0, maxChars);
    }
}