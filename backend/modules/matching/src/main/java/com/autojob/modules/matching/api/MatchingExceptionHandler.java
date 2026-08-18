package com.autojob.modules.matching.api;

import com.autojob.modules.jobembedding.vectorstore.JobVectorStoreException;
import com.autojob.modules.matching.service.MatchingPreconditionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice(
        basePackages =
                "com.autojob.modules.matching"
)
public class MatchingExceptionHandler {

    @ExceptionHandler(
            MatchingPreconditionException.class
    )
    public ResponseEntity<ApiErrorResponse>
    handleMatchingPrecondition(
            MatchingPreconditionException exception,
            HttpServletRequest request
    ) {
        return switch (
                exception.getReason()
                ) {

            case AUTHENTICATION_REQUIRED ->
                    build(
                            HttpStatus.UNAUTHORIZED,
                            "MATCHING_AUTHENTICATION_REQUIRED",
                            exception.getMessage(),
                            request.getRequestURI()
                    );

            case CANDIDATE_PROFILE_NOT_FOUND ->
                    build(
                            HttpStatus.NOT_FOUND,
                            "MATCHING_CANDIDATE_PROFILE_NOT_FOUND",
                            exception.getMessage(),
                            request.getRequestURI()
                    );

            case READY_CANDIDATE_EMBEDDING_NOT_FOUND ->
                    build(
                            HttpStatus.CONFLICT,
                            "MATCHING_CANDIDATE_EMBEDDING_NOT_READY",
                            exception.getMessage(),
                            request.getRequestURI()
                    );

            case CANDIDATE_EMBEDDING_STALE ->
                    build(
                            HttpStatus.CONFLICT,
                            "MATCHING_CANDIDATE_EMBEDDING_STALE",
                            exception.getMessage(),
                            request.getRequestURI()
                    );

            case CANDIDATE_EMBEDDING_INVALID ->
                    build(
                            HttpStatus.CONFLICT,
                            "MATCHING_CANDIDATE_EMBEDDING_INVALID",
                            exception.getMessage(),
                            request.getRequestURI()
                    );

            case MATCH_RESULT_NOT_FOUND ->
                    build(
                            HttpStatus.NOT_FOUND,
                            "MATCHING_RESULT_NOT_FOUND",
                            exception.getMessage(),
                            request.getRequestURI()
                    );
        };
    }

    /**
     * Ví dụ:
     *
     * - Qdrant down
     * - Qdrant timeout
     * - collection lỗi
     * - vector search request lỗi
     */
    @ExceptionHandler(
            JobVectorStoreException.class
    )
    public ResponseEntity<ApiErrorResponse>
    handleVectorStoreFailure(
            JobVectorStoreException exception,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.SERVICE_UNAVAILABLE,
                "MATCHING_VECTOR_STORE_UNAVAILABLE",
                "Job vector search is temporarily unavailable",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(
            IllegalArgumentException.class
    )
    public ResponseEntity<ApiErrorResponse>
    handleInvalidRequest(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                "MATCHING_INVALID_REQUEST",
                exception.getMessage(),
                request.getRequestURI()
        );
    }

    private ResponseEntity<ApiErrorResponse>
    build(
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