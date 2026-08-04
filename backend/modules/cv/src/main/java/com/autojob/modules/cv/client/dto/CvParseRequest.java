package com.autojob.modules.cv.client.dto;

public record CvParseRequest(
        String rawCvId,
        String bucket,
        String objectKey,
        String originalFilename,
        String contentType
) {
}