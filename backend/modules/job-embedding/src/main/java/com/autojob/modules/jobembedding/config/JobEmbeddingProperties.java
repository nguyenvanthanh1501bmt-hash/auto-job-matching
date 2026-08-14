package com.autojob.modules.jobembedding.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "autojob.job-embedding")
public class JobEmbeddingProperties {

    /**
     * Version của cách compose text trước khi gửi sang embedding model.
     *
     * Khi đổi prefix, thứ tự section hoặc semantics của text,
     * bump version này để không trộn vectors khác contract.
     */
    @NotBlank
    private String textVersion = "job-text-v2";

    /**
     * Giới hạn tổng input text, tính cả prefix "passage: ".
     */
    @Min(500)
    private int textMaxChars = 2_400;

    @Min(100)
    private int descriptionMaxChars = 6_000;

    @Min(100)
    private int requirementsMaxChars = 4_000;

    @Min(100)
    private int benefitsMaxChars = 2_000;
}