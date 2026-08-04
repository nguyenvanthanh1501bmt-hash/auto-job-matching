package com.autojob.modules.cv.repository;

import com.autojob.modules.cv.domain.CandidateProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateProfileRepository
        extends MongoRepository<CandidateProfile, String> {

    Optional<CandidateProfile> findByRawCvId(
            String rawCvId
    );

    Optional<CandidateProfile> findByRawCvIdAndOwnerUserId(
            String rawCvId,
            String ownerUserId
    );

    List<CandidateProfile> findByOwnerUserIdOrderByCreatedAtDesc(
            String ownerUserId
    );

    boolean existsByRawCvId(String rawCvId);
}