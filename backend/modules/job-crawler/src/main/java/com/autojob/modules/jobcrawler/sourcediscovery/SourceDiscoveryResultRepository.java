package com.autojob.modules.jobcrawler.sourcediscovery;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SourceDiscoveryResultRepository extends MongoRepository<SourceDiscoveryResult, String> {
    List<SourceDiscoveryResult> findByWebsiteSourceId(String websiteSourceId);
}