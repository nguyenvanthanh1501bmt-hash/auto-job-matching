package com.autojob.modules.jobcrawler.parser;

import com.autojob.modules.jobcrawler.domain.ParsedRawJob;

public interface JobDetailPageParser {

    String sourceCode();

    ParsedRawJob parseDetail(String detailUrl, String html);
}