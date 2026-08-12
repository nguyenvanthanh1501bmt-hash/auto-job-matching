package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.support.TaxonomyTestLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryNormalizerV2Test {

    private SalaryNormalizer normalizer;

    @BeforeEach
    void setUp() {
        NormalizationTaxonomyProperties taxonomy =
                TaxonomyTestLoader.load();

        normalizer =
                new SalaryNormalizer(
                        new TextNormalizer(),
                        taxonomy
                );
    }

    @Test
    void shouldParseVietnameseMillionShorthand() {
        assertSalary(
                "20tr",
                20_000_000L,
                20_000_000L,
                "VND"
        );

        assertSalary(
                "20 tr",
                20_000_000L,
                20_000_000L,
                "VND"
        );

        assertSalary(
                "20 triệu",
                20_000_000L,
                20_000_000L,
                "VND"
        );

        assertSalary(
                "20 triệu/tháng",
                20_000_000L,
                20_000_000L,
                "VND"
        );
    }

    @Test
    void shouldApplyMultiplierPerRangeComponent() {
        assertSalary(
                "20tr - 30tr",
                20_000_000L,
                30_000_000L,
                "VND"
        );

        assertSalary(
                "15.5tr - 20.5tr",
                15_500_000L,
                20_500_000L,
                "VND"
        );

        assertSalary(
                "1k - 2k USD",
                1_000L,
                2_000L,
                "USD"
        );
    }

    @Test
    void shouldApplySharedVietnameseMillionUnitAtEndOfRange() {
        assertSalary(
                "15 - 25 triệu",
                15_000_000L,
                25_000_000L,
                "VND"
        );

        assertSalary(
                "15 đến 25 triệu",
                15_000_000L,
                25_000_000L,
                "VND"
        );

        assertSalary(
                "15,5 - 20,5 triệu",
                15_500_000L,
                20_500_000L,
                "VND"
        );
    }

    @Test
    void shouldParseGroupedVndAmounts() {
        assertSalary(
                "20,000,000 VND",
                20_000_000L,
                20_000_000L,
                "VND"
        );

        assertSalary(
                "20.000.000 VNĐ",
                20_000_000L,
                20_000_000L,
                "VND"
        );

        assertSalary(
                "20 triệu VND",
                20_000_000L,
                20_000_000L,
                "VND"
        );
    }

    @Test
    void shouldParseUsdFormatsAndKMultiplier() {
        assertSalary(
                "1000 USD",
                1_000L,
                1_000L,
                "USD"
        );

        assertSalary(
                "1,000 USD",
                1_000L,
                1_000L,
                "USD"
        );

        assertSalary(
                "$1000",
                1_000L,
                1_000L,
                "USD"
        );

        assertSalary(
                "$1,500",
                1_500L,
                1_500L,
                "USD"
        );

        assertSalary(
                "$1k",
                1_000L,
                1_000L,
                "USD"
        );

        assertSalary(
                "1k USD",
                1_000L,
                1_000L,
                "USD"
        );

        assertSalary(
                "$1.5k",
                1_500L,
                1_500L,
                "USD"
        );
    }

    @Test
    void shouldParseLowerAndUpperBounds() {
        SalaryNormalizationResult plus =
                normalizer.normalize(
                        "$1500+"
                );

        SalaryNormalizationResult from =
                normalizer.normalize(
                        "from $1000"
                );

        SalaryNormalizationResult upTo =
                normalizer.normalize(
                        "up to $2000"
                );

        SalaryNormalizationResult vietnameseFrom =
                normalizer.normalize(
                        "Từ 20 triệu"
                );

        SalaryNormalizationResult vietnameseUpTo =
                normalizer.normalize(
                        "Lên đến 30 triệu"
                );

        assertThat(plus.min())
                .isEqualTo(1_500L);

        assertThat(plus.max())
                .isNull();

        assertThat(plus.currency())
                .isEqualTo("USD");

        assertThat(from.min())
                .isEqualTo(1_000L);

        assertThat(from.max())
                .isNull();

        assertThat(upTo.min())
                .isNull();

        assertThat(upTo.max())
                .isEqualTo(2_000L);

        assertThat(vietnameseFrom.min())
                .isEqualTo(20_000_000L);

        assertThat(vietnameseFrom.max())
                .isNull();

        assertThat(vietnameseUpTo.min())
                .isNull();

        assertThat(vietnameseUpTo.max())
                .isEqualTo(30_000_000L);
    }

    @Test
    void shouldKeepNegotiableAmountsEmpty() {
        assertNegotiable(
                "Negotiable"
        );

        assertNegotiable(
                "Thỏa thuận"
        );

        assertNegotiable(
                "Cạnh tranh"
        );

        assertNegotiable(
                "Competitive"
        );
    }

    @Test
    void shouldNotConvertHourlyAmountToMonthlySalary() {
        SalaryNormalizationResult result =
                normalizer.normalize(
                        "$20/hour"
                );

        assertThat(result.min())
                .isEqualTo(20L);

        assertThat(result.max())
                .isEqualTo(20L);

        assertThat(result.currency())
                .isEqualTo("USD");
    }

    @Test
    void shouldNotGuessRangeWhenMultipleAmountsHaveNoRangeMarker() {
        SalaryNormalizationResult result =
                normalizer.normalize(
                        "20 triệu, thưởng 5 triệu"
                );

        assertThat(result.min())
                .isNull();

        assertThat(result.max())
                .isNull();

        assertThat(result.currency())
                .isEqualTo("VND");
    }

    private void assertSalary(
            String input,
            Long expectedMin,
            Long expectedMax,
            String expectedCurrency
    ) {
        SalaryNormalizationResult result =
                normalizer.normalize(
                        input
                );

        assertThat(result.min())
                .isEqualTo(expectedMin);

        assertThat(result.max())
                .isEqualTo(expectedMax);

        assertThat(result.currency())
                .isEqualTo(expectedCurrency);
    }

    private void assertNegotiable(
            String input
    ) {
        SalaryNormalizationResult result =
                normalizer.normalize(
                        input
                );

        assertThat(result.min())
                .isNull();

        assertThat(result.max())
                .isNull();
    }
}