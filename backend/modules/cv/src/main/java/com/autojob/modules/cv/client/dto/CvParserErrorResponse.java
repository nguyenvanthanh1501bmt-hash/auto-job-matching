package com.autojob.modules.cv.client.dto;

public record CvParserErrorResponse(
        String code,
        String message,
        String rawCvId
) {
}