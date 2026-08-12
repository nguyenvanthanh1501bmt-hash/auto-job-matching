package com.autojob.modules.auth.ratelimit;

import com.autojob.modules.auth.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    // Tên các HTTP header dùng để trả thông tin rate limit cho client.
    private static final String LIMIT_HEADER =
            "X-RateLimit-Limit";

    private static final String REMAINING_HEADER =
            "X-RateLimit-Remaining";

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    // Lưu TokenBucket tương ứng với từng client và từng policy.
    private final Map<String, TokenBucket> buckets =
            new ConcurrentHashMap<>();

    // Xác định những request không cần áp dụng rate limit.
    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {
        return !properties.isEnabled()
                || HttpMethod.OPTIONS.matches(
                request.getMethod()
        )
                || request.getRequestURI()
                .startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Xác định policy rate limit áp dụng cho request.
        RateLimitPolicy policy = resolvePolicy(request);

        // Xác định client dựa trên user hoặc IP.
        String clientKey = resolveClientKey(
                request,
                policy.ipOnly()
        );

        // Kết hợp policy + client để mỗi loại rate limit có bucket riêng.
        String bucketKey =
                policy.name() + ":" + clientKey;

        // Lấy bucket hiện tại hoặc tạo bucket mới cho client.
        TokenBucket bucket = buckets.computeIfAbsent(
                bucketKey,
                ignored -> new TokenBucket(
                        policy.rule().capacity(),
                        policy.rule().refillPeriod()
                )
        );

        // Thử tiêu thụ 1 token cho request hiện tại.
        TokenBucket.ConsumptionResult result =
                bucket.tryConsume();

        // Trả thông tin rate limit trong response header.
        response.setHeader(
                LIMIT_HEADER,
                String.valueOf(
                        policy.rule().capacity()
                )
        );

        response.setHeader(
                REMAINING_HEADER,
                String.valueOf(result.remaining())
        );

        // Nếu còn token thì cho request đi tiếp vào filter chain.
        if (result.allowed()) {
            filterChain.doFilter(
                    request,
                    response
            );
            return;
        }

        // Không còn token -> trả HTTP 429 Too Many Requests.
        response.setStatus(
                HttpStatus.TOO_MANY_REQUESTS.value()
        );

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding("UTF-8");

        // Cho client biết cần chờ bao lâu trước khi retry.
        response.setHeader(
                HttpHeaders.RETRY_AFTER,
                String.valueOf(
                        result.retryAfterSeconds()
                )
        );

        // Trả lỗi rate limit dưới dạng JSON.
        objectMapper.writeValue(
                response.getWriter(),
                new RateLimitError(
                        Instant.now(),
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "RATE_LIMIT_EXCEEDED",
                        "Too many requests",
                        request.getRequestURI(),
                        result.retryAfterSeconds()
                )
        );
    }

    // Xác định rule rate limit dựa trên HTTP method và request path.
    private RateLimitPolicy resolvePolicy(
            HttpServletRequest request
    ) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Login: rate limit theo IP để chống brute-force.
        if (HttpMethod.POST.matches(method)
                && "/api/auth/login".equals(path)) {
            return new RateLimitPolicy(
                    "auth-login",
                    properties.getLogin(),
                    true
            );
        }

        // Register: rate limit theo IP.
        if (HttpMethod.POST.matches(method)
                && "/api/auth/register".equals(path)) {
            return new RateLimitPolicy(
                    "auth-register",
                    properties.getRegister(),
                    true
            );
        }

        // Refresh token: rate limit theo IP.
        if (HttpMethod.POST.matches(method)
                && "/api/auth/refresh".equals(path)) {
            return new RateLimitPolicy(
                    "auth-refresh",
                    properties.getRefresh(),
                    true
            );
        }

        // Upload CV: nếu đã đăng nhập thì rate limit theo user.
        if (HttpMethod.POST.matches(method)
                && (
                "/api/cvs".equals(path)
                        || "/api/cvs/".equals(path)
        )) {
            return new RateLimitPolicy(
                    "cv-upload",
                    properties.getCvUpload(),
                    false
            );
        }

        // Những endpoint còn lại sử dụng rule general.
        return new RateLimitPolicy(
                "general",
                properties.getGeneral(),
                false
        );
    }

    // Xác định client key dùng để tìm TokenBucket.
    private String resolveClientKey(
            HttpServletRequest request,
            boolean ipOnly
    ) {
        if (!ipOnly) {
            Authentication authentication =
                    SecurityContextHolder
                            .getContext()
                            .getAuthentication();

            // Nếu đã đăng nhập thì ưu tiên định danh bằng user.
            if (authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication
                    instanceof AnonymousAuthenticationToken)) {
                return "user:"
                        + authentication.getName();
            }
        }

        // Chưa đăng nhập hoặc policy yêu cầu IP -> dùng IP.
        return "ip:" + resolveIp(request);
    }

    // Lấy IP của client từ request.
    private String resolveIp(
            HttpServletRequest request
    ) {
        if (properties.isTrustForwardedHeaders()) {
            String forwardedFor =
                    request.getHeader(
                            "X-Forwarded-For"
                    );

            // Lấy IP đầu tiên trong X-Forwarded-For.
            if (forwardedFor != null
                    && !forwardedFor.isBlank()) {
                return forwardedFor
                        .split(",", 2)[0]
                        .trim();
            }
        }

        // Nếu không trust proxy header thì lấy IP trực tiếp từ connection.
        return request.getRemoteAddr();
    }

    // Định kỳ xóa các bucket không hoạt động trong 2 giờ.
    @Scheduled(fixedDelay = 600_000L)
    public void evictInactiveBuckets() {
        long cutoff = System.nanoTime()
                - TimeUnit.HOURS.toNanos(2);

        buckets.entrySet().removeIf(
                entry ->
                        entry.getValue()
                                .lastAccessNanos()
                                < cutoff
        );
    }

    // Policy xác định rule và cách định danh client.
    private record RateLimitPolicy(
            String name,
            RateLimitProperties.Rule rule,
            boolean ipOnly
    ) {
    }

    // Trả về khi request bị rate limit.
    private record RateLimitError(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            long retryAfterSeconds
    ) {
    }
}