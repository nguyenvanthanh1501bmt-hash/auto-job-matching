package com.autojob.modules.jobcrawler.sourcediscovery;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface WebsiteSourceRepository extends MongoRepository<WebsiteSource, String> {
}