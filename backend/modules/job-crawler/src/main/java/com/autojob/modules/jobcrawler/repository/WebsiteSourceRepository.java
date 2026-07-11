package com.autojob.modules.jobcrawler.repository;

import com.autojob.modules.jobcrawler.sourcediscovery.WebsiteSource;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WebsiteSourceRepository extends MongoRepository<WebsiteSource, String> {
}