package com.autojob.modules.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "autojob.auth")
public class AuthProperties {

    /**
     * true:
     * - Mọi API nghiệp vụ vẫn public.
     * - JWT vẫn được đọc nếu client gửi Authorization header.
     *
     * false:
     * - Bật authorization rules trong SecurityConfig.
     */
    private boolean publicApiMode = true;

    @NotBlank
    private String issuer = "autojob-app";

    /**
     * Base64 secret, sau khi decode phải dài tối thiểu 32 bytes.
     */
    @NotBlank
    private String jwtSecretBase64;

    @NotNull
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    @NotNull
    private Duration refreshTokenTtl = Duration.ofDays(30);

    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:5173"
    ));
}