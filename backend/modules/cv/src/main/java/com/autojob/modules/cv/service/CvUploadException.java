package com.autojob.modules.cv.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CvUploadException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public CvUploadException(
            HttpStatus status,
            String code,
            String message
    ) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public CvUploadException(
            HttpStatus status,
            String code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }
}