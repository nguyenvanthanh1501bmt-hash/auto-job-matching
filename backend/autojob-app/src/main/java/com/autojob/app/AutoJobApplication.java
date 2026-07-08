package com.autojob.app;

import com.autojob.modules.jobcrawler.config.ItviecCrawlerProperties;
import com.autojob.modules.jobcrawler.config.JobokoCrawlerProperties;
import com.autojob.modules.jobcrawler.config.MockCrawlerProperties;
import com.autojob.modules.jobcrawler.config.TopdevCrawlerProperties;
import com.autojob.modules.jobcrawler.config.Vieclam24hCrawlerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = "com.autojob")
@EnableMongoRepositories(basePackages = "com.autojob")
@EnableConfigurationProperties({
        MockCrawlerProperties.class,
        ItviecCrawlerProperties.class,
        JobokoCrawlerProperties.class,
        TopdevCrawlerProperties.class,
        Vieclam24hCrawlerProperties.class
})
public class AutoJobApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoJobApplication.class, args);
    }
}