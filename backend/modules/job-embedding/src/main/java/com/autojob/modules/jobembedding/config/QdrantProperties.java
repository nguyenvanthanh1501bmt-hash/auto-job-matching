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
@ConfigurationProperties(prefix = "autojob.qdrant")
public class QdrantProperties {

    @NotBlank
    private String baseUrl = "http://localhost:6333";

    @NotBlank
    private String collection = "job_vectors_v1";

    @Min(1)
    private int dimension = 384;

    @NotBlank
    private String distance = "Cosine";

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(3);

    @NotNull
    private Duration responseTimeout = Duration.ofSeconds(10);

    public String normalizedBaseUrl() {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl;
    }
}