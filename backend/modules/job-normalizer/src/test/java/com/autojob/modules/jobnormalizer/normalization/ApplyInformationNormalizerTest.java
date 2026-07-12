package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.common.dtos.ApplyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyInformationNormalizerTest {

    private ApplyInformationNormalizer applyInformationNormalizer;

    @BeforeEach
    void setUp() {
        applyInformationNormalizer =
                new ApplyInformationNormalizer(
                        new TextNormalizer()
                );
    }

    @Test
    void shouldUseDetailUrlWhenApplyUrlIsMissing() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                applyInformationNormalizer.normalize(
                        null,
                        null,
                        "https://jobs.example.com/jobs/java-developer"
                );

        assertThat(result.applyUrl())
                .isEqualTo(
                        "https://jobs.example.com/jobs/java-developer"
                );

        assertThat(result.applyType())
                .isEqualTo(ApplyType.DETAIL_PAGE);
    }

    @Test
    void shouldReturnUnknownWhenBothUrlsAreMissing() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                applyInformationNormalizer.normalize(
                        null,
                        null,
                        null
                );

        assertThat(result.applyUrl()).isNull();
        assertThat(result.applyType())
                .isEqualTo(ApplyType.UNKNOWN);
    }

    @Test
    void shouldNormalizeSameApplyAndDetailUrlAsDetailPage() {
        String url =
                "https://jobs.example.com/jobs/java-developer";

        ApplyInformationNormalizer.ApplyInformationResult result =
                applyInformationNormalizer.normalize(
                        url,
                        ApplyType.UNKNOWN,
                        url
                );

        assertThat(result.applyUrl()).isEqualTo(url);
        assertThat(result.applyType())
                .isEqualTo(ApplyType.DETAIL_PAGE);
    }

    @Test
    void shouldNormalizeSameOriginDifferentPathAsApplyButton() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                applyInformationNormalizer.normalize(
                        "https://jobs.example.com/jobs/java-developer/apply",
                        ApplyType.UNKNOWN,
                        "https://jobs.example.com/jobs/java-developer"
                );

        assertThat(result.applyType())
                .isEqualTo(
                        ApplyType.DETAIL_PAGE_APPLY_BUTTON
                );
    }

    @Test
    void shouldNormalizeRelativeApplyUrlAsApplyButton() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                applyInformationNormalizer.normalize(
                        "/jobs/java-developer/apply",
                        ApplyType.UNKNOWN,
                        "https://jobs.example.com/jobs/java-developer"
                );

        assertThat(result.applyUrl())
                .isEqualTo("/jobs/java-developer/apply");

        assertThat(result.applyType())
                .isEqualTo(
                        ApplyType.DETAIL_PAGE_APPLY_BUTTON
                );
    }

    @Test
    void shouldNormalizeDifferentDomainAsExternalCompanySite() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                applyInformationNormalizer.normalize(
                        "https://company.example.com/careers/apply/123",
                        ApplyType.UNKNOWN,
                        "https://jobs.example.com/jobs/java-developer"
                );

        assertThat(result.applyType())
                .isEqualTo(
                        ApplyType.EXTERNAL_COMPANY_SITE
                );
    }

    @Test
    void shouldCorrectIncorrectDetailPageTypeForExternalUrl() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                applyInformationNormalizer.normalize(
                        "https://example.com/apply/backend-java",
                        ApplyType.DETAIL_PAGE,
                        "http://localhost:18080/jobs/java-backend.html"
                );

        assertThat(result.applyType())
                .isEqualTo(
                        ApplyType.EXTERNAL_COMPANY_SITE
                );
    }

    @Test
    void shouldCorrectIncorrectApplyButtonTypeForExternalUrl() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                applyInformationNormalizer.normalize(
                        "https://external.example.com/apply/123",
                        ApplyType.DETAIL_PAGE_APPLY_BUTTON,
                        "https://jobs.example.com/jobs/123"
                );

        assertThat(result.applyType())
                .isEqualTo(
                        ApplyType.EXTERNAL_COMPANY_SITE
                );
    }

    @Test
    void shouldNormalizeMailtoUrlAsEmail() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                applyInformationNormalizer.normalize(
                        "mailto:recruitment@example.com",
                        ApplyType.UNKNOWN,
                        "https://jobs.example.com/jobs/accountant"
                );

        assertThat(result.applyUrl())
                .isEqualTo(
                        "mailto:recruitment@example.com"
                );

        assertThat(result.applyType())
                .isEqualTo(ApplyType.EMAIL);
    }

    @Test
    void shouldPrioritizeEmailInferenceOverIncorrectRawType() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                applyInformationNormalizer.normalize(
                        "mailto:hr@example.com",
                        ApplyType.DETAIL_PAGE,
                        "https://jobs.example.com/jobs/123"
                );

        assertThat(result.applyType())
                .isEqualTo(ApplyType.EMAIL);
    }

    @Test
    void shouldPreserveExplicitExternalCompanySiteType() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                applyInformationNormalizer.normalize(
                        "https://company.example.com/apply/123",
                        ApplyType.EXTERNAL_COMPANY_SITE,
                        "https://jobs.example.com/jobs/123"
                );

        assertThat(result.applyType())
                .isEqualTo(
                        ApplyType.EXTERNAL_COMPANY_SITE
                );
    }

    @Test
    void shouldTrimApplyAndDetailUrls() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                applyInformationNormalizer.normalize(
                        "  https://company.example.com/apply/123  ",
                        ApplyType.UNKNOWN,
                        "  https://jobs.example.com/jobs/123  "
                );

        assertThat(result.applyUrl())
                .isEqualTo(
                        "https://company.example.com/apply/123"
                );

        assertThat(result.applyType())
                .isEqualTo(
                        ApplyType.EXTERNAL_COMPANY_SITE
                );
    }

    @Test
    void shouldReturnUnknownWhenApplyUrlExistsButDetailUrlIsMissing() {
        ApplyInformationNormalizer.ApplyInformationResult result =
                applyInformationNormalizer.normalize(
                        "https://company.example.com/apply/123",
                        ApplyType.UNKNOWN,
                        null
                );

        assertThat(result.applyUrl())
                .isEqualTo(
                        "https://company.example.com/apply/123"
                );

        assertThat(result.applyType())
                .isEqualTo(ApplyType.UNKNOWN);
    }
}