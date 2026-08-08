package com.autojob.modules.candidateembedding.api;

import com.autojob.modules.candidateembedding.service.CandidateEmbeddingNotFoundException;
import com.autojob.modules.candidateembedding.service.CandidateEmbeddingProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice(
        basePackages = "com.autojob.modules.candidateembedding"
)
public class CandidateEmbeddingExceptionHandler {

    @ExceptionHandler(CandidateEmbeddingNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            CandidateEmbeddingNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "CANDIDATE_EMBEDDING_NOT_FOUND",
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

    @ExceptionHandler(CandidateEmbeddingProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handleProcessingFailure(
            CandidateEmbeddingProcessingException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CANDIDATE_EMBEDDING_FAILED",
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
        return ResponseEntity
                .status(status)
                .body(
                        new ApiErrorResponse(
                                Instant.now(),
                                status.value(),
                                error,
                                message,
                                path
                        )
                );
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