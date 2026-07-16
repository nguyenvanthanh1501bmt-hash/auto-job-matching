package com.autojob.modules.jobcrawler.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawlerRetentionPropertiesTest {

    @Test
    void shouldDefaultEverySourceToThirtyDays() {
        assertThat(new MockCrawlerProperties().getRawRetentionDays())
                .isEqualTo(30);

        assertThat(new ItviecCrawlerProperties().getRawRetentionDays())
                .isEqualTo(30);

        assertThat(new JobokoCrawlerProperties().getRawRetentionDays())
                .isEqualTo(30);

        assertThat(new TopdevCrawlerProperties().getRawRetentionDays())
                .isEqualTo(30);

        assertThat(
                new Vieclam24hCrawlerProperties()
                        .getRawRetentionDays()
        ).isEqualTo(30);
    }

    @Test
    void shouldRejectRetentionBelowOneDay() {
        MockCrawlerProperties properties =
                new MockCrawlerProperties();

        assertThatThrownBy(
                () -> properties.setRawRetentionDays(0)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "rawRetentionDays must be greater than or equal to 1"
                );
    }
}