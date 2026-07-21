package com.autojob.modules.jobembedding.config;

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
@ConfigurationProperties(prefix = "autojob.embedding")
public class EmbeddingProperties {

    @NotBlank
    private String baseUrl = "http://localhost:8002";

    @Min(1)
    private int expectedDimension = 384;

    private String expectedVersion =
            "intfloat/multilingual-e5-small"
                    + "@c007d7ef6fd86656326059b28395a7a03a7c5846"
                    + "|prep-v1|l2";

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(3);

    @NotNull
    private Duration responseTimeout = Duration.ofSeconds(30);

    @Min(100)
    private int maxErrorLength = 1_000;

    public boolean hasExpectedVersion() {
        return expectedVersion != null
                && !expectedVersion.isBlank();
    }

    public String normalizedBaseUrl() {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl;
    }
}