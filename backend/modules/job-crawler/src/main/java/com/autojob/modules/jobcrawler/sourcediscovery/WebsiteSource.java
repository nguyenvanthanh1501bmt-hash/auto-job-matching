package com.autojob.modules.jobcrawler.sourcediscovery;

import com.autojob.modules.jobcrawler.domain.WebsiteSourceStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("website_sources")
public class WebsiteSource {

    @Id
    private String id;

    private String sourceCode;
    private String domain;
    private WebsiteSourceStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}