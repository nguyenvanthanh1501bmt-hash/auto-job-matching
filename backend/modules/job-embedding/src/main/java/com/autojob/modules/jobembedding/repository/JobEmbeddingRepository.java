package com.autojob.modules.jobembedding.repository;

import com.autojob.modules.jobembedding.domain.JobEmbedding;
import com.autojob.modules.jobembedding.domain.JobEmbeddingStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobEmbeddingRepository
        extends MongoRepository<JobEmbedding, String> {

    Optional<JobEmbedding>
    findByNormalizedJobIdAndEmbeddingVersionAndTextVersion(
            String normalizedJobId,
            String embeddingVersion,
            String textVersion
    );

    Optional<JobEmbedding>
    findFirstByNormalizedJobIdOrderByUpdatedAtDesc(
            String normalizedJobId
    );

    List<JobEmbedding>
    findAllByNormalizedJobIdOrderByUpdatedAtDesc(
            String normalizedJobId
    );

    /**
     * Admin jobs chỉ cần trạng thái embedding mới nhất của mỗi normalized job.
     * Lấy toàn bộ record liên quan trong một query rồi chọn latest ở app layer
     * giúp tránh gọi repository riêng cho từng row.
     */
    List<JobEmbedding> findAllByNormalizedJobIdIn(
            Collection<String> normalizedJobIds
    );

    List<JobEmbedding>
    findByStatusOrderByUpdatedAtAsc(
            JobEmbeddingStatus status
    );

    boolean
    existsByNormalizedJobIdAndEmbeddingVersionAndTextVersion(
            String normalizedJobId,
            String embeddingVersion,
            String textVersion
    );
}