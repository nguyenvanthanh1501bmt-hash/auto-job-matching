package com.autojob.modules.auth.service;

public record RequestMetadata(
        String ipAddress,
        String userAgent
) {
}