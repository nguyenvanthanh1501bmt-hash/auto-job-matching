package com.autojob.modules.jobembedding.repository;

import com.autojob.modules.jobembedding.domain.JobEmbedding;
import com.autojob.modules.jobembedding.domain.JobEmbeddingStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface JobEmbeddingRepository
        extends MongoRepository<JobEmbedding, String> {

    Optional<JobEmbedding> findByNormalizedJobIdAndEmbeddingVersion(
            String normalizedJobId,
            String embeddingVersion
    );

    Optional<JobEmbedding> findFirstByNormalizedJobIdOrderByUpdatedAtDesc(
            String normalizedJobId
    );

    List<JobEmbedding> findAllByNormalizedJobIdOrderByUpdatedAtDesc(
            String normalizedJobId
    );

    List<JobEmbedding> findByStatusOrderByUpdatedAtAsc(
            JobEmbeddingStatus status
    );

    boolean existsByNormalizedJobIdAndEmbeddingVersion(
            String normalizedJobId,
            String embeddingVersion
    );
}