package com.autojob.modules.cv.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "autojob.cv.storage")
public class CvStorageProperties {

    @NotBlank
    private String endpoint =
            "http://localhost:9000";

    @NotBlank
    private String accessKey =
            "minioadmin";

    @NotBlank
    private String secretKey =
            "minioadmin";

    @NotBlank
    private String bucket =
            "autojob-cvs";

    @Min(1)
    @Max(50)
    private int maxFileSizeMb = 10;
}