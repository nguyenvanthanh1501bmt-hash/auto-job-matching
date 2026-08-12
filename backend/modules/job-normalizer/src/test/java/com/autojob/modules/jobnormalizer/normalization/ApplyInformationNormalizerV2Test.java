package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.common.dtos.ApplyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyInformationNormalizerV2Test {

    private ApplyInformationNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new ApplyInformationNormalizer(
                new TextNormalizer()
        );
    }

    @Test
    void shouldRejectDangerousApplySchemesAndFallbackToSafeDetailPage() {
        String detail =
                "https://jobs.example.com/jobs/123";

        assertFallbackToDetail(
                "javascript:alert(1)",
                detail
        );

        assertFallbackToDetail(
                "data:text/html,hello",
                detail
        );

        assertFallbackToDetail(
                "file:///etc/passwd",
                detail
        );

        assertFallbackToDetail(
                "ftp://example.com/apply",
                detail
        );
    }

    @Test
    void shouldRejectDangerousDetailUrlWhenNoSafeApplyTargetExists() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                normalizer.normalize(
                        null,
                        ApplyType.UNKNOWN,
                        "javascript:alert(1)"
                );

        assertThat(result.applyUrl()).isNull();

        assertThat(result.applyType())
                .isEqualTo(ApplyType.UNKNOWN);
    }

    @Test
    void shouldKeepValidMailtoAsEmail() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                normalizer.normalize(
                        "mailto:recruitment@example.com",
                        ApplyType.UNKNOWN,
                        "https://jobs.example.com/jobs/123"
                );

        assertThat(result.applyUrl())
                .isEqualTo(
                        "mailto:recruitment@example.com"
                );

        assertThat(result.applyType())
                .isEqualTo(ApplyType.EMAIL);
    }

    @Test
    void shouldRejectMalformedMailtoAndFallbackSafely() {
        String detail =
                "https://jobs.example.com/jobs/123";

        ApplyInformationNormalizer.ApplyInformationResult result =
                normalizer.normalize(
                        "mailto:not-an-email",
                        ApplyType.EMAIL,
                        detail
                );

        assertThat(result.applyUrl()).isEqualTo(detail);

        assertThat(result.applyType())
                .isEqualTo(ApplyType.DETAIL_PAGE);
    }

    @Test
    void shouldPreserveExternalHttpsUrlWithoutRewritingDomainOrPath() {
        String apply =
                "https://careers.company.com/jobs/ABC-123?source=portal";

        ApplyInformationNormalizer.ApplyInformationResult result =
                normalizer.normalize(
                        apply,
                        ApplyType.UNKNOWN,
                        "https://jobs.example.com/jobs/123"
                );

        assertThat(result.applyUrl()).isEqualTo(apply);

        assertThat(result.applyType())
                .isEqualTo(ApplyType.EXTERNAL_COMPANY_SITE);
    }

    private void assertFallbackToDetail(
            String apply,
            String detail
    ) {
        ApplyInformationNormalizer.ApplyInformationResult result =
                normalizer.normalize(
                        apply,
                        ApplyType.UNKNOWN,
                        detail
                );

        assertThat(result.applyUrl()).isEqualTo(detail);

        assertThat(result.applyType())
                .isEqualTo(ApplyType.DETAIL_PAGE);
    }
}