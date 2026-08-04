package com.autojob.modules.cv.client;

import lombok.Getter;

@Getter
public class CvParserResponseValidationException
        extends RuntimeException {

    private final String field;

    public CvParserResponseValidationException(
            String field,
            String reason
    ) {
        super("Invalid CV parser response field '"
                + field
                + "': "
                + reason);
        this.field = field;
    }
}