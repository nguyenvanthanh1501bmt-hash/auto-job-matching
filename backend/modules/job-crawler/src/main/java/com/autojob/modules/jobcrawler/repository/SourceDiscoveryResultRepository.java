package com.autojob.modules.jobcrawler.repository;

import com.autojob.modules.jobcrawler.sourcediscovery.SourceDiscoveryResult;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SourceDiscoveryResultRepository extends MongoRepository<SourceDiscoveryResult, String> {
    List<SourceDiscoveryResult> findByWebsiteSourceId(String websiteSourceId);
}