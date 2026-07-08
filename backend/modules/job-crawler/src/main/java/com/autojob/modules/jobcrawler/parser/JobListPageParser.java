package com.autojob.modules.jobcrawler.parser;

import java.util.List;

public interface JobListPageParser {

    String sourceCode();

    List<String> parseDetailUrls(String baseUrl, String html);
}