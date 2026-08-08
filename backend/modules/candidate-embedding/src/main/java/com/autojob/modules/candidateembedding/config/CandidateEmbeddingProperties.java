package com.autojob.modules.candidateembedding.config;

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
@ConfigurationProperties(prefix = "autojob.candidate-embedding")
public class CandidateEmbeddingProperties {

    @NotBlank
    private String textVersion = "candidate-text-v1";

    @Min(1)
    private int textMaxChars = 2_400;

    @Min(1)
    private int summaryMaxChars = 500;

    @Min(0)
    private int workExperienceMaxItems = 3;

    @Min(1)
    private int workExperienceItemMaxChars = 450;

    @Min(0)
    private int projectMaxItems = 3;

    @Min(1)
    private int projectItemMaxChars = 350;

    @Min(0)
    private int certificationsMaxItems = 5;
}