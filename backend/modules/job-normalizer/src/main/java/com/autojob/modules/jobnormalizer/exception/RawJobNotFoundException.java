package com.autojob.modules.jobnormalizer.exception;

public class RawJobNotFoundException extends RuntimeException {

    private final String rawJobId;

    public RawJobNotFoundException(String rawJobId) {
        super("Raw job not found: " + rawJobId);
        this.rawJobId = rawJobId;
    }

    public String getRawJobId() {
        return rawJobId;
    }
}