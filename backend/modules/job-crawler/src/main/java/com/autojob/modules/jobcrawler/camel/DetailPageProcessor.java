package com.autojob.modules.jobcrawler.camel;

import com.autojob.common.dtos.ApplyType;
import com.autojob.modules.jobcrawler.config.MockCrawlerProperties;
import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.service.RawJobService;
import com.autojob.modules.jobcrawler.util.FingerprintUtil;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class DetailPageProcessor implements Processor {

    private final MockCrawlerProperties properties;
    private final RawJobService rawJobService;

    @Override
    public void process(Exchange exchange) {
        String detailUrl = exchange.getProperty("detailUrl", String.class);
        String html = exchange.getMessage().getBody(String.class);

        Document doc = Jsoup.parse(html, detailUrl);

        String title = text(doc, ".job-title");
        String company = text(doc, ".company");

        String applyUrl = doc.selectFirst("a.apply-url[href]") != null
                ? doc.selectFirst("a.apply-url[href]").absUrl("href")
                : detailUrl;

        String fingerprint = FingerprintUtil.sha256(
                properties.getSourceCode() + "|" + title + "|" + company + "|" + detailUrl
        );

        RawJob rawJob = RawJob.builder()
                .sourceCode(properties.getSourceCode())
                .sourceUrl(detailUrl)
                .listUrl(properties.getListUrl())
                .detailUrl(detailUrl)
                .applyUrl(applyUrl)
                .applyType(ApplyType.DETAIL_PAGE)
                .title(title)
                .companyName(company)
                .salaryText(text(doc, ".salary"))
                .locationText(text(doc, ".location"))
                .experienceText(text(doc, ".experience"))
                .skills(doc.select(".skills .skill").eachText())
                .descriptionText(text(doc, ".description"))
                .requirementsText(text(doc, ".requirements"))
                .benefitsText(text(doc, ".benefits"))
                .rawHtml(html)
                .rawText(doc.text())
                .fingerprint(fingerprint)
                .collectedAt(Instant.now())
                .build();

        RawJob saved = rawJobService.saveIfNew(rawJob);
        exchange.getMessage().setBody(saved.getId());
    }

    private String text(Document doc, String selector) {
        return doc.selectFirst(selector) != null
                ? doc.selectFirst(selector).text()
                : null;
    }
}