package com.autojob.modules.jobnormalizer.repository;

import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NormalizedJobRepository
        extends MongoRepository<NormalizedJob, String> {

    Optional<NormalizedJob> findByRawJobIdAndNormalizationVersion(
            String rawJobId,
            String normalizationVersion
    );

    Optional<NormalizedJob> findFirstByRawJobIdOrderByNormalizedAtDesc(
            String rawJobId
    );

    /**
     * Dùng cho màn hình admin cần ghép trạng thái pipeline theo batch.
     * Query một lần cho cả trang raw jobs để tránh N+1 query MongoDB.
     */
    List<NormalizedJob> findAllByRawJobIdIn(
            Collection<String> rawJobIds
    );

    Page<NormalizedJob> findBySourceCode(
            String sourceCode,
            Pageable pageable
    );

    Page<NormalizedJob> findByNormalizationVersion(
            String normalizationVersion,
            Pageable pageable
    );

    Page<NormalizedJob> findBySourceCodeAndNormalizationVersion(
            String sourceCode,
            String normalizationVersion,
            Pageable pageable
    );

    boolean existsByRawJobIdAndNormalizationVersion(
            String rawJobId,
            String normalizationVersion
    );
}