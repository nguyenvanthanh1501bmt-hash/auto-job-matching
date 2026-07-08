package com.autojob.modules.jobcrawler.parser;

public interface JobDetailPageParser {

    String sourceCode();

    ParsedRawJob parseDetail(String detailUrl, String html);
}