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

    public static final String TEXT_VERSION_V1 =
            "candidate-text-v1";

    public static final String TEXT_VERSION_V2 =
            "candidate-text-v2";

    /*
     * v2 adds active license and language evidence to the
     * candidate embedding text contract.
     */
    @NotBlank
    private String textVersion =
            TEXT_VERSION_V2;

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

    @Min(0)
    private int licensesMaxItems = 5;

    @Min(0)
    private int languagesMaxItems = 8;
}