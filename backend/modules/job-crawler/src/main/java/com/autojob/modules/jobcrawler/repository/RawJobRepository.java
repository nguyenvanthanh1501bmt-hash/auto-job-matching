package com.autojob.modules.jobcrawler.repository;

import com.autojob.modules.jobcrawler.domain.RawJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RawJobRepository extends MongoRepository<RawJob, String> {

    boolean existsByFingerprint(String fingerprint);

    Optional<RawJob> findByFingerprint(String fingerprint);

    Optional<RawJob> findBySourceCodeAndSourceJobId(
            String sourceCode,
            String sourceJobId
    );

    Page<RawJob> findBySourceCode(
            String sourceCode,
            Pageable pageable
    );
}