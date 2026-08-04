package com.autojob.modules.cv.client;

import lombok.Getter;

@Getter
public class CvParserClientException extends RuntimeException {

    private final FailureType failureType;
    private final Integer upstreamStatus;
    private final String parserCode;
    private final String rawCvId;

    private CvParserClientException(
            FailureType failureType,
            String message,
            Integer upstreamStatus,
            String parserCode,
            String rawCvId,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = failureType;
        this.upstreamStatus = upstreamStatus;
        this.parserCode = parserCode;
        this.rawCvId = rawCvId;
    }

    public static CvParserClientException connectionRefused(
            String rawCvId,
            Throwable cause
    ) {
        return new CvParserClientException(
                FailureType.CONNECTION_REFUSED,
                "CV parser service connection was refused",
                null,
                null,
                rawCvId,
                cause
        );
    }

    public static CvParserClientException connectionFailure(
            String rawCvId,
            Throwable cause
    ) {
        return new CvParserClientException(
                FailureType.CONNECTION_FAILURE,
                "Unable to connect to CV parser service",
                null,
                null,
                rawCvId,
                cause
        );
    }

    public static CvParserClientException connectTimeout(
            String rawCvId,
            Throwable cause
    ) {
        return new CvParserClientException(
                FailureType.CONNECT_TIMEOUT,
                "CV parser service connection timed out",
                null,
                null,
                rawCvId,
                cause
        );
    }

    public static CvParserClientException responseTimeout(
            String rawCvId,
            Throwable cause
    ) {
        return new CvParserClientException(
                FailureType.RESPONSE_TIMEOUT,
                "CV parser service response timed out",
                null,
                null,
                rawCvId,
                cause
        );
    }

    public static CvParserClientException httpError(
            String rawCvId,
            int upstreamStatus,
            String parserCode
    ) {
        FailureType type = upstreamStatus >= 500
                ? FailureType.HTTP_5XX
                : FailureType.HTTP_4XX;

        return new CvParserClientException(
                type,
                "CV parser service returned HTTP "
                        + upstreamStatus,
                upstreamStatus,
                parserCode,
                rawCvId,
                null
        );
    }

    public static CvParserClientException malformedJson(
            String rawCvId,
            Throwable cause
    ) {
        return new CvParserClientException(
                FailureType.MALFORMED_JSON,
                "CV parser service returned malformed JSON",
                null,
                null,
                rawCvId,
                cause
        );
    }

    public static CvParserClientException emptyResponse(
            String rawCvId
    ) {
        return new CvParserClientException(
                FailureType.EMPTY_RESPONSE,
                "CV parser service returned an empty response",
                null,
                null,
                rawCvId,
                null
        );
    }

    public static CvParserClientException responseTooLarge(
            String rawCvId
    ) {
        return new CvParserClientException(
                FailureType.RESPONSE_TOO_LARGE,
                "CV parser service response exceeded the configured limit",
                null,
                null,
                rawCvId,
                null
        );
    }

    public static CvParserClientException unexpectedStatus(
            String rawCvId,
            int upstreamStatus
    ) {
        return new CvParserClientException(
                FailureType.UNEXPECTED_STATUS,
                "CV parser service returned unexpected HTTP "
                        + upstreamStatus,
                upstreamStatus,
                null,
                rawCvId,
                null
        );
    }

    public enum FailureType {
        CONNECTION_REFUSED,
        CONNECTION_FAILURE,
        CONNECT_TIMEOUT,
        RESPONSE_TIMEOUT,
        HTTP_4XX,
        HTTP_5XX,
        MALFORMED_JSON,
        EMPTY_RESPONSE,
        RESPONSE_TOO_LARGE,
        UNEXPECTED_STATUS
    }
}