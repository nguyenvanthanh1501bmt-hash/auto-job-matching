package com.autojob.modules.jobcrawler.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RawJobRepository extends MongoRepository<RawJob, String> {
    Optional<RawJob> findByFingerprint(String fingerprint);
}