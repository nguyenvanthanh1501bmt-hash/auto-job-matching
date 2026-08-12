package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryNormalizerTest {

    private SalaryNormalizer salaryNormalizer;

    @BeforeEach
    void setUp() {
        NormalizationTaxonomyProperties taxonomy =
                TaxonomyTestLoader.load();

        salaryNormalizer =
                new SalaryNormalizer(
                        new TextNormalizer(),
                        taxonomy
                );
    }

    @Test
    void shouldNormalizeVietnameseMillionRange() {
        SalaryNormalizationResult result =
                salaryNormalizer.normalize(
                        "15 - 25 triệu"
                );

        assertThat(result.min())
                .isEqualTo(15_000_000L);

        assertThat(result.max())
                .isEqualTo(25_000_000L);

        assertThat(result.currency())
                .isEqualTo("VND");
    }

    @Test
    void shouldNormalizeVietnameseRangeUsingDenKeyword() {
        SalaryNormalizationResult result =
                salaryNormalizer.normalize(
                        "15 đến 25 triệu"
                );

        assertThat(result.min())
                .isEqualTo(15_000_000L);

        assertThat(result.max())
                .isEqualTo(25_000_000L);

        assertThat(result.currency())
                .isEqualTo("VND");
    }

    @Test
    void shouldNormalizeDecimalMillionSalary() {
        SalaryNormalizationResult result =
                salaryNormalizer.normalize(
                        "15,5 - 20,5 triệu"
                );

        assertThat(result.min())
                .isEqualTo(15_500_000L);

        assertThat(result.max())
                .isEqualTo(20_500_000L);

        assertThat(result.currency())
                .isEqualTo("VND");
    }

    @Test
    void shouldNormalizeLowerBoundSalary() {
        SalaryNormalizationResult result =
                salaryNormalizer.normalize(
                        "Từ 20 triệu"
                );

        assertThat(result.min())
                .isEqualTo(20_000_000L);

        assertThat(result.max())
                .isNull();

        assertThat(result.currency())
                .isEqualTo("VND");
    }

    @Test
    void shouldNormalizeUpperBoundSalary() {
        SalaryNormalizationResult result =
                salaryNormalizer.normalize(
                        "Lên đến 30 triệu"
                );

        assertThat(result.min())
                .isNull();

        assertThat(result.max())
                .isEqualTo(30_000_000L);

        assertThat(result.currency())
                .isEqualTo("VND");
    }

    @Test
    void shouldNormalizeFixedSalary() {
        SalaryNormalizationResult result =
                salaryNormalizer.normalize(
                        "20 triệu"
                );

        assertThat(result.min())
                .isEqualTo(20_000_000L);

        assertThat(result.max())
                .isEqualTo(20_000_000L);

        assertThat(result.currency())
                .isEqualTo("VND");
    }

    @Test
    void shouldNormalizeGroupedUsdRange() {
        SalaryNormalizationResult result =
                salaryNormalizer.normalize(
                        "$1,000 - $2,000"
                );

        assertThat(result.min())
                .isEqualTo(1_000L);

        assertThat(result.max())
                .isEqualTo(2_000L);

        assertThat(result.currency())
                .isEqualTo("USD");
    }

    @Test
    void shouldNormalizePlainUsdRange() {
        SalaryNormalizationResult result =
                salaryNormalizer.normalize(
                        "1000 - 2000 USD"
                );

        assertThat(result.min())
                .isEqualTo(1_000L);

        assertThat(result.max())
                .isEqualTo(2_000L);

        assertThat(result.currency())
                .isEqualTo("USD");
    }

    @Test
    void shouldNormalizeUsdUpperBound() {
        SalaryNormalizationResult result =
                salaryNormalizer.normalize(
                        "Up to $2,000"
                );

        assertThat(result.min())
                .isNull();

        assertThat(result.max())
                .isEqualTo(2_000L);

        assertThat(result.currency())
                .isEqualTo("USD");
    }

    @Test
    void shouldReturnNullAmountsForNegotiableSalary() {
        SalaryNormalizationResult vietnamese =
                salaryNormalizer.normalize(
                        "Thỏa thuận"
                );

        SalaryNormalizationResult competitive =
                salaryNormalizer.normalize(
                        "Cạnh tranh"
                );

        SalaryNormalizationResult english =
                salaryNormalizer.normalize(
                        "Negotiable"
                );

        assertThat(vietnamese.min())
                .isNull();

        assertThat(vietnamese.max())
                .isNull();

        assertThat(competitive.min())
                .isNull();

        assertThat(competitive.max())
                .isNull();

        assertThat(english.min())
                .isNull();

        assertThat(english.max())
                .isNull();
    }

    @Test
    void shouldReturnUnknownForNullOrBlankSalary() {
        SalaryNormalizationResult nullResult =
                salaryNormalizer.normalize(
                        null
                );

        SalaryNormalizationResult blankResult =
                salaryNormalizer.normalize(
                        "   "
                );

        assertThat(nullResult.min())
                .isNull();

        assertThat(nullResult.max())
                .isNull();

        assertThat(nullResult.currency())
                .isNull();

        assertThat(blankResult.min())
                .isNull();

        assertThat(blankResult.max())
                .isNull();

        assertThat(blankResult.currency())
                .isNull();
    }
}