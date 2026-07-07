package com.autojob.modules.jobcrawler.repository;

import com.autojob.modules.jobcrawler.domain.RawJob;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface RawJobRepository extends MongoRepository<RawJob, String> {

    boolean existsByFingerprint(String fingerprint);

    Optional<RawJob> findByFingerprint(String fingerprint);

    Optional<RawJob> findBySourceCodeAndSourceJobId(String sourceCode, String sourceJobId);

    long deleteByExpiresAtBefore(Instant now);
}