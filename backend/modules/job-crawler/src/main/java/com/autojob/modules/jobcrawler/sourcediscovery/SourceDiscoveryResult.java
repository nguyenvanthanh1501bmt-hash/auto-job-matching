package com.autojob.modules.jobcrawler.sourcediscovery;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("source_discovery_results")
public class SourceDiscoveryResult {

    @Id
    private String id;

    private String websiteSourceId;
    private String sourceCode;
    private String candidateUrl;
    private String detectionType;
    private SourceDiscoveryResultStatus status;
    private Instant discoveredAt;
}