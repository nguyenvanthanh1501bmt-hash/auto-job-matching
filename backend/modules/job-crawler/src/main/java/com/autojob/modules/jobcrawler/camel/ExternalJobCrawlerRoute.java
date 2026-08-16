package com.autojob.modules.jobcrawler.camel;

import com.autojob.modules.jobcrawler.config.ItviecCrawlerProperties;
import com.autojob.modules.jobcrawler.config.JobokoCrawlerProperties;
import com.autojob.modules.jobcrawler.config.TopdevCrawlerProperties;
import com.autojob.modules.jobcrawler.config.Vieclam24hCrawlerProperties;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class ExternalJobCrawlerRoute extends RouteBuilder {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";

    private final ItviecCrawlerProperties itviec;
    private final JobokoCrawlerProperties joboko;
    private final TopdevCrawlerProperties topdev;
    private final Vieclam24hCrawlerProperties vieclam24h;
    private final ListPageProcessor listPageProcessor;
    private final DetailPageProcessor detailPageProcessor;

    public ExternalJobCrawlerRoute(
            ItviecCrawlerProperties itviec,
            JobokoCrawlerProperties joboko,
            TopdevCrawlerProperties topdev,
            Vieclam24hCrawlerProperties vieclam24h,
            ListPageProcessor listPageProcessor,
            DetailPageProcessor detailPageProcessor
    ) {
        this.itviec = itviec;
        this.joboko = joboko;
        this.topdev = topdev;
        this.vieclam24h = vieclam24h;
        this.listPageProcessor = listPageProcessor;
        this.detailPageProcessor = detailPageProcessor;
    }

    @Override
    public void configure() {
        configureSource(
                "itviec",
                itviec.getSourceCode(),
                itviec.getBaseUrl(),
                itviec.getListUrl(),
                itviec.getRequestDelayMs(),
                itviec.isStoreRawHtml(),
                itviec.isStoreRawText(),
                itviec.getRawTextMaxChars(),
                itviec.getRawRetentionDays()
        );

        configureSource(
                "joboko",
                joboko.getSourceCode(),
                joboko.getBaseUrl(),
                joboko.getListUrl(),
                joboko.getRequestDelayMs(),
                joboko.isStoreRawHtml(),
                joboko.isStoreRawText(),
                joboko.getRawTextMaxChars(),
                joboko.getRawRetentionDays()
        );

        configureSource(
                "topdev",
                topdev.getSourceCode(),
                topdev.getBaseUrl(),
                topdev.getListUrl(),
                topdev.getRequestDelayMs(),
                topdev.isStoreRawHtml(),
                topdev.isStoreRawText(),
                topdev.getRawTextMaxChars(),
                topdev.getRawRetentionDays()
        );

        configureSource(
                "vieclam24h",
                vieclam24h.getSourceCode(),
                vieclam24h.getBaseUrl(),
                vieclam24h.getListUrl(),
                vieclam24h.getRequestDelayMs(),
                vieclam24h.isStoreRawHtml(),
                vieclam24h.isStoreRawText(),
                vieclam24h.getRawTextMaxChars(),
                vieclam24h.getRawRetentionDays()
        );
    }

    private void configureSource(
            String routeKey,
            String sourceCode,
            String baseUrl,
            String listUrl,
            long requestDelayMs,
            boolean storeRawHtml,
            boolean storeRawText,
            int rawTextMaxChars,
            int rawRetentionDays
    ) {
        from("direct:crawl-live-" + routeKey)
                .routeId("live-job-crawler-" + routeKey)

                .setProperty("maxJobs", header("maxJobs"))
                .setProperty("sourceCode", constant(sourceCode))
                .setProperty("baseUrl", constant(baseUrl))
                .setProperty("listUrl", constant(listUrl))
                .setProperty("storeRawHtml", constant(storeRawHtml))
                .setProperty("storeRawText", constant(storeRawText))
                .setProperty("rawTextMaxChars", constant(rawTextMaxChars))
                .setProperty("rawRetentionDays", constant(rawRetentionDays))

                // =========================
                // LIST PAGE
                // =========================
                .removeHeaders("*")
                .setBody(constant(null))
                .setHeader(
                        Exchange.HTTP_METHOD,
                        constant("GET")
                )
                .setHeader(
                        "User-Agent",
                        constant(USER_AGENT)
                )
                .setHeader(
                        "Accept",
                        constant(
                                "text/html,application/xhtml+xml,"
                                        + "application/xml;q=0.9,*/*;q=0.8"
                        )
                )
                .setHeader(
                        "Accept-Language",
                        constant(
                                "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7"
                        )
                )
                .setHeader(
                        "Cache-Control",
                        constant("no-cache")
                )
                .setHeader(
                        "Pragma",
                        constant("no-cache")
                )
                .toD(
                        listUrl
                                + "?httpMethod=GET"
                                + "&followRedirects=true"
                )
                .process(listPageProcessor)

                // =========================
                // DETAIL JOBS
                // =========================
                //
                // Chạy từng job một.
                //
                // job #1:
                // fetch -> parse -> raw -> normalize -> embed
                //
                // rồi mới tới job #2.
                //
                .split(body())
                .streaming()

                .setProperty(
                        "detailUrl",
                        body()
                )

                .delay(requestDelayMs)

                // Một job lỗi không làm chết cả batch.
                .doTry()

                .removeHeaders("*")
                .setBody(constant(null))

                .setHeader(
                        Exchange.HTTP_METHOD,
                        constant("GET")
                )
                .setHeader(
                        "User-Agent",
                        constant(USER_AGENT)
                )
                .setHeader(
                        "Accept",
                        constant(
                                "text/html,application/xhtml+xml,"
                                        + "application/xml;q=0.9,*/*;q=0.8"
                        )
                )
                .setHeader(
                        "Accept-Language",
                        constant(
                                "vi-VN,vi;q=0.9,"
                                        + "en-US;q=0.8,en;q=0.7"
                        )
                )
                .setHeader(
                        "Cache-Control",
                        constant("no-cache")
                )
                .setHeader(
                        "Pragma",
                        constant("no-cache")
                )

                .toD(
                        "${exchangeProperty.detailUrl}"
                                + "?httpMethod=GET"
                                + "&followRedirects=true"
                )

                // Đây là điểm bắt đầu pipeline hiện tại:
                //
                // HTML
                // -> parser
                // -> RawJobService
                // -> JobRawCollectedEvent
                // -> normalize
                // -> JobNormalizedReadyEvent
                // -> embedding
                // -> Qdrant
                //
                .process(detailPageProcessor)

                .doCatch(Exception.class)

                .log(
                        "Live crawler detail failed "
                                + "source=${exchangeProperty.sourceCode}, "
                                + "url=${exchangeProperty.detailUrl}, "
                                + "error=${exception.class}: "
                                + "${exception.message}"
                )

                .end()

                .end();
    }
}