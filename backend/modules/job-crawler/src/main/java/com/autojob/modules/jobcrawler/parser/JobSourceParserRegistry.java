package com.autojob.modules.jobcrawler.parser;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JobSourceParserRegistry {

    private final Map<String, JobListPageParser> listParsers;
    private final Map<String, JobDetailPageParser> detailParsers;

    public JobSourceParserRegistry(
            List<JobListPageParser> listParserList,
            List<JobDetailPageParser> detailParserList
    ) {
        this.listParsers = listParserList.stream()
                .collect(Collectors.toMap(
                        parser -> normalize(parser.sourceCode()),
                        Function.identity()
                ));

        this.detailParsers = detailParserList.stream()
                .collect(Collectors.toMap(
                        parser -> normalize(parser.sourceCode()),
                        Function.identity()
                ));
    }

    public JobListPageParser getListParser(String sourceCode) {
        JobListPageParser parser = listParsers.get(normalize(sourceCode));

        if (parser == null) {
            throw new IllegalArgumentException("No JobListPageParser found for sourceCode=" + sourceCode);
        }

        return parser;
    }

    public JobDetailPageParser getDetailParser(String sourceCode) {
        JobDetailPageParser parser = detailParsers.get(normalize(sourceCode));

        if (parser == null) {
            throw new IllegalArgumentException("No JobDetailPageParser found for sourceCode=" + sourceCode);
        }

        return parser;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}