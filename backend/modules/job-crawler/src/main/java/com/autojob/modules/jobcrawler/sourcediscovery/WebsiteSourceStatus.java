package com.autojob.modules.jobcrawler.sourcediscovery;

public enum WebsiteSourceStatus {
    PENDING_DISCOVERY,
    DISCOVERING,
    DISCOVERED,
    NO_CANDIDATE_FOUND,
    FAILED
}