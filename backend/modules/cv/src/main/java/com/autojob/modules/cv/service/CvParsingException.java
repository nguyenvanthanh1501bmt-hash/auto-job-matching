package com.autojob.modules.cv.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CvParsingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String rawCvId;

    public CvParsingException(
            HttpStatus status,
            String code,
            String message,
            String rawCvId
    ) {
        super(message);
        this.status = status;
        this.code = code;
        this.rawCvId = rawCvId;
    }

    public CvParsingException(
            HttpStatus status,
            String code,
            String message,
            String rawCvId,
            Throwable cause
    ) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.rawCvId = rawCvId;
    }
}