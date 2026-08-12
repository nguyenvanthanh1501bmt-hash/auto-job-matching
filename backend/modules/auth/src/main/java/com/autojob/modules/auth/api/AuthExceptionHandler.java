package com.autojob.modules.auth.api;

import com.autojob.modules.auth.service.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice(
        basePackages = "com.autojob.modules.auth"
)
public class AuthExceptionHandler {

    // Xử lý các exception nghiệp vụ riêng của module auth.
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(
            AuthException exception,
            HttpServletRequest request
    ) {
        return build(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI(),
                List.of()
        );
    }

    // Xử lý trường hợp đăng nhập sai email hoặc password.
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            BadCredentialsException exception,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password",
                request.getRequestURI(),
                List.of()
        );
    }

    // Xử lý các lỗi authentication khác không thuộc BadCredentialsException.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse>
    handleAuthenticationFailure(
            AuthenticationException exception,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_FAILED",
                "Authentication failed",
                request.getRequestURI(),
                List.of()
        );
    }

    // Xử lý lỗi validation từ @Valid / @Validated trên request body.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        // Lấy danh sách lỗi theo từng field để trả về cho client.
        List<String> details = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error ->
                        error.getField()
                                + ": "
                                + error.getDefaultMessage()
                )
                .toList();

        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                request.getRequestURI(),
                details
        );
    }

    // Tạo response lỗi thống nhất cho toàn bộ exception handler.
    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            String path,
            List<String> details
    ) {
        return ResponseEntity
                .status(status)
                .body(new ApiErrorResponse(
                        Instant.now(),
                        status.value(),
                        code,
                        message,
                        path,
                        details
                ));
    }

    // DTO dùng để chuẩn hóa format error response của module auth.
    public record ApiErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            List<String> details
    ) {
    }
}