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

    private static final String LIMIT_HEADER =
            "X-RateLimit-Limit";

    private static final String REMAINING_HEADER =
            "X-RateLimit-Remaining";

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<String, TokenBucket> buckets =
            new ConcurrentHashMap<>();

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
        RateLimitPolicy policy = resolvePolicy(request);

        String clientKey = resolveClientKey(
                request,
                policy.ipOnly()
        );

        String bucketKey =
                policy.name() + ":" + clientKey;

        TokenBucket bucket = buckets.computeIfAbsent(
                bucketKey,
                ignored -> new TokenBucket(
                        policy.rule().capacity(),
                        policy.rule().refillPeriod()
                )
        );

        TokenBucket.ConsumptionResult result =
                bucket.tryConsume();

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

        if (result.allowed()) {
            filterChain.doFilter(
                    request,
                    response
            );
            return;
        }

        response.setStatus(
                HttpStatus.TOO_MANY_REQUESTS.value()
        );

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding("UTF-8");

        response.setHeader(
                HttpHeaders.RETRY_AFTER,
                String.valueOf(
                        result.retryAfterSeconds()
                )
        );

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

    private RateLimitPolicy resolvePolicy(
            HttpServletRequest request
    ) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (HttpMethod.POST.matches(method)
                && "/api/auth/login".equals(path)) {
            return new RateLimitPolicy(
                    "auth-login",
                    properties.getLogin(),
                    true
            );
        }

        if (HttpMethod.POST.matches(method)
                && "/api/auth/register".equals(path)) {
            return new RateLimitPolicy(
                    "auth-register",
                    properties.getRegister(),
                    true
            );
        }

        if (HttpMethod.POST.matches(method)
                && "/api/auth/refresh".equals(path)) {
            return new RateLimitPolicy(
                    "auth-refresh",
                    properties.getRefresh(),
                    true
            );
        }

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

        return new RateLimitPolicy(
                "general",
                properties.getGeneral(),
                false
        );
    }

    private String resolveClientKey(
            HttpServletRequest request,
            boolean ipOnly
    ) {
        if (!ipOnly) {
            Authentication authentication =
                    SecurityContextHolder
                            .getContext()
                            .getAuthentication();

            if (authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication
                    instanceof AnonymousAuthenticationToken)) {
                return "user:"
                        + authentication.getName();
            }
        }

        return "ip:" + resolveIp(request);
    }

    private String resolveIp(
            HttpServletRequest request
    ) {
        if (properties.isTrustForwardedHeaders()) {
            String forwardedFor =
                    request.getHeader(
                            "X-Forwarded-For"
                    );

            if (forwardedFor != null
                    && !forwardedFor.isBlank()) {
                return forwardedFor
                        .split(",", 2)[0]
                        .trim();
            }
        }

        return request.getRemoteAddr();
    }

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

    private record RateLimitPolicy(
            String name,
            RateLimitProperties.Rule rule,
            boolean ipOnly
    ) {
    }

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