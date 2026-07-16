package com.autojob.modules.jobcrawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autojob.crawler.joboko")
public class JobokoCrawlerProperties {

    private String sourceCode;
    private String baseUrl;
    private String listUrl;
    private long requestDelayMs = 3000;

    private boolean storeRawHtml = false;
    private boolean storeRawText = false;
    private int rawTextMaxChars = 20000;
    private int rawRetentionDays = 30;

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getListUrl() {
        return listUrl;
    }

    public void setListUrl(String listUrl) {
        this.listUrl = listUrl;
    }

    public long getRequestDelayMs() {
        return requestDelayMs;
    }

    public void setRequestDelayMs(long requestDelayMs) {
        this.requestDelayMs = requestDelayMs;
    }

    public boolean isStoreRawHtml() {
        return storeRawHtml;
    }

    public void setStoreRawHtml(boolean storeRawHtml) {
        this.storeRawHtml = storeRawHtml;
    }

    public boolean isStoreRawText() {
        return storeRawText;
    }

    public void setStoreRawText(boolean storeRawText) {
        this.storeRawText = storeRawText;
    }

    public int getRawTextMaxChars() {
        return rawTextMaxChars;
    }

    public void setRawTextMaxChars(int rawTextMaxChars) {
        this.rawTextMaxChars = rawTextMaxChars;
    }

    public int getRawRetentionDays() {
        return rawRetentionDays;
    }

    public void setRawRetentionDays(int rawRetentionDays) {
        if (rawRetentionDays < 1) {
            throw new IllegalArgumentException(
                    "rawRetentionDays must be greater than or equal to 1"
            );
        }

        this.rawRetentionDays = rawRetentionDays;
    }
}