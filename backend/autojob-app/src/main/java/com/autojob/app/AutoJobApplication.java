package com.autojob.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = "com.autojob")
@ConfigurationPropertiesScan(basePackages = "com.autojob")
@EnableMongoRepositories(basePackages = "com.autojob")
public class AutoJobApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoJobApplication.class, args);
    }
}