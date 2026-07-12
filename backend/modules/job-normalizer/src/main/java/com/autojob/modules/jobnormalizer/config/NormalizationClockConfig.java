package com.autojob.modules.jobnormalizer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class NormalizationClockConfig {

    @Bean
    public Clock normalizationClock(
            NormalizationProperties normalizationProperties
    ) {
        ZoneId zoneId = ZoneId.of(
                normalizationProperties.getTimezone()
        );

        return Clock.system(zoneId);
    }
}