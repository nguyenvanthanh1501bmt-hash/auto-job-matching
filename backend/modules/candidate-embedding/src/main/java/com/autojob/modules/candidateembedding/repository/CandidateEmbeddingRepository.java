package com.autojob.modules.candidateembedding.repository;

import com.autojob.modules.candidateembedding.domain.CandidateEmbedding;
import com.autojob.modules.candidateembedding.domain.CandidateEmbeddingStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateEmbeddingRepository
        extends MongoRepository<CandidateEmbedding, String> {

    Optional<CandidateEmbedding>
    findByCandidateProfileIdAndEmbeddingVersionAndTextVersion(
            String candidateProfileId,
            String embeddingVersion,
            String textVersion
    );

    Optional<CandidateEmbedding>
    findFirstByCandidateProfileIdOrderByUpdatedAtDesc(
            String candidateProfileId
    );

    List<CandidateEmbedding>
    findAllByCandidateProfileIdOrderByUpdatedAtDesc(
            String candidateProfileId
    );

    List<CandidateEmbedding> findByStatusOrderByUpdatedAtAsc(
            CandidateEmbeddingStatus status
    );
}