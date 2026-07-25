package com.autojob.modules.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "autojob.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /**
     * Chỉ bật khi app đứng sau Nginx/reverse proxy do bạn kiểm soát.
     */
    private boolean trustForwardedHeaders = false;

    @Valid
    private Rule general = new Rule(
            120,
            Duration.ofMinutes(1)
    );

    @Valid
    private Rule login = new Rule(
            10,
            Duration.ofMinutes(1)
    );

    @Valid
    private Rule register = new Rule(
            5,
            Duration.ofHours(1)
    );

    @Valid
    private Rule refresh = new Rule(
            30,
            Duration.ofMinutes(1)
    );

    @Valid
    private Rule cvUpload = new Rule(
            10,
            Duration.ofHours(1)
    );

    public record Rule(
            @Min(1) int capacity,
            @NotNull Duration refillPeriod
    ) {
    }
}