package com.autojob.modules.jobembedding.api;

import com.autojob.modules.jobembedding.service.JobEmbeddingNotFoundException;
import com.autojob.modules.jobembedding.service.JobEmbeddingProcessingException;
import com.autojob.modules.jobnormalizer.exception.NormalizedJobNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice(
        basePackages = "com.autojob.modules.jobembedding"
)
public class JobEmbeddingExceptionHandler {

    @ExceptionHandler({
            JobEmbeddingNotFoundException.class,
            NormalizedJobNotFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "JOB_EMBEDDING_NOT_FOUND",
                exception.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(JobEmbeddingProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handleProcessingFailure(
            JobEmbeddingProcessingException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "JOB_EMBEDDING_FAILED",
                exception.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                exception.getMessage(),
                request.getRequestURI()
        );
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String error,
            String message,
            String path
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
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