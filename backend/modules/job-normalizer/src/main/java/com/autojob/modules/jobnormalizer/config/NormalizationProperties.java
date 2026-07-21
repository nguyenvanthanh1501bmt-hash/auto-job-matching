package com.autojob.modules.jobnormalizer.config;

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
@ConfigurationProperties(prefix = "autojob.normalization")
public class NormalizationProperties {

    /**
     * Mỗi phiên bản rule tạo một normalized document riêng cho raw job.
     *
     * Logical key:
     * rawJobId + normalizationVersion
     */
    @NotBlank
    private String version = "rule-v1";

    /**
     * Timezone dùng khi parse các ngày tương đối như:
     * - Hôm nay
     * - Hôm qua
     * - 2 ngày trước
     */
    @NotBlank
    private String timezone = "Asia/Ho_Chi_Minh";

    /**
     * Giới hạn tổng số ký tự của embeddingText sau khi đã ghép các field.
     *
     * Giới hạn này giúp normalizer chủ động ưu tiên nội dung nghiệp vụ
     * quan trọng thay vì để embedding model tự truncate ở cuối input.
     */
    @Min(500)
    private int embeddingTextMaxChars = 2_400;

    /**
     * Giới hạn description khi tạo embeddingText.
     */
    @Min(100)
    private int embeddingDescriptionMaxChars = 6_000;

    /**
     * Giới hạn requirements khi tạo embeddingText.
     */
    @Min(100)
    private int embeddingRequirementsMaxChars = 4_000;

    /**
     * Giới hạn benefits khi tạo embeddingText.
     */
    @Min(100)
    private int embeddingBenefitsMaxChars = 2_000;
}