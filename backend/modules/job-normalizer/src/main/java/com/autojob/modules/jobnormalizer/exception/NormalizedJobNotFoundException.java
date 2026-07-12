package com.autojob.modules.jobnormalizer.exception;

public class NormalizedJobNotFoundException extends RuntimeException {

    private final String normalizedJobId;

    public NormalizedJobNotFoundException(String normalizedJobId) {
        super("Normalized job not found: " + normalizedJobId);
        this.normalizedJobId = normalizedJobId;
    }

    public String getNormalizedJobId() {
        return normalizedJobId;
    }
}