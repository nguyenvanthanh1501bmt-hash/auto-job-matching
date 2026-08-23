package com.autojob.modules.cv.api;

import com.autojob.modules.cv.service.CvParsingException;
import com.autojob.modules.cv.service.CvUploadException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;

@RestControllerAdvice(
        basePackages = "com.autojob.modules.cv"
)
public class CvExceptionHandler {

    private static final String CV_ACCESS_DENIED =
            "CV_ACCESS_DENIED";

    private static final String RAW_CV_NOT_FOUND =
            "RAW_CV_NOT_FOUND";

    @ExceptionHandler(CvUploadException.class)
    public ResponseEntity<ApiErrorResponse>
    handleCvUploadException(
            CvUploadException exception,
            HttpServletRequest request
    ) {
        /*
         * Không tiết lộ CV ID có tồn tại nhưng thuộc user khác.
         *
         * CV không tồn tại
         * và
         * CV thuộc user khác
         *
         * đều nhìn giống nhau từ phía client.
         */
        if (CV_ACCESS_DENIED.equals(
                exception.getCode()
        )) {
            return build(
                    HttpStatus.NOT_FOUND,
                    RAW_CV_NOT_FOUND,
                    "Raw CV was not found",
                    request.getRequestURI()
            );
        }

        return build(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(CvParsingException.class)
    public ResponseEntity<ApiErrorResponse>
    handleCvParsingException(
            CvParsingException exception,
            HttpServletRequest request
    ) {
        /*
         * Parse/profile của CV thuộc user khác cũng trả 404,
         * tránh resource enumeration.
         */
        if (CV_ACCESS_DENIED.equals(
                exception.getCode()
        )) {
            return build(
                    HttpStatus.NOT_FOUND,
                    RAW_CV_NOT_FOUND,
                    "Raw CV was not found",
                    request.getRequestURI()
            );
        }

        return build(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(
            MaxUploadSizeExceededException.class
    )
    public ResponseEntity<ApiErrorResponse>
    handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "CV_FILE_TOO_LARGE",
                "Uploaded file exceeds server multipart limit",
                request.getRequestURI()
        );
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String error,
            String message,
            String path
    ) {
        ApiErrorResponse response =
                new ApiErrorResponse(
                        Instant.now(),
                        status.value(),
                        error,
                        message,
                        path
                );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    public record ApiErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path
    ) {
    }
}