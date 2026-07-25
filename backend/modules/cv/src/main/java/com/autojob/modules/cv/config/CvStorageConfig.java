package com.autojob.modules.cv.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
@EnableConfigurationProperties(
        CvStorageProperties.class
)
public class CvStorageConfig {

    private final CvStorageProperties properties;

    @Bean
    MinioClient cvMinioClient() {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(
                        properties.getAccessKey(),
                        properties.getSecretKey()
                )
                .build();
    }

    @Bean
    ApplicationRunner ensureCvBucketExists(
            MinioClient cvMinioClient
    ) {
        return arguments -> {
            boolean exists =
                    cvMinioClient.bucketExists(
                            BucketExistsArgs.builder()
                                    .bucket(
                                            properties.getBucket()
                                    )
                                    .build()
                    );

            if (!exists) {
                cvMinioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(
                                        properties.getBucket()
                                )
                                .build()
                );

                log.info(
                        "Created private CV bucket: {}",
                        properties.getBucket()
                );
            } else {
                log.info(
                        "CV bucket is ready: {}",
                        properties.getBucket()
                );
            }
        };
    }
}