package com.autojob.modules.cv.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "autojob.cv.parser")
public class CvParserProperties {

    @NotBlank
    private String baseUrl = "http://localhost:8003";

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(3);

    @NotNull
    private Duration responseTimeout = Duration.ofSeconds(60);

    @NotBlank
    private String expectedVersion = "rule-v1";

    @Min(100)
    @Max(20_000)
    private int maxErrorLength = 1_000;

    @Min(1_048_576)
    @Max(67_108_864)
    private int maxResponseSizeBytes = 16_777_216;

    public String normalizedBaseUrl() {
        String normalized = baseUrl.trim();

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - 1
            );
        }

        return normalized;
    }
}