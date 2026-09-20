package com.autojob.modules.cvtailoring.repository;

import com.autojob.modules.cvtailoring.domain.TailoredCvDraft;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TailoredCvDraftRepository
        extends MongoRepository<TailoredCvDraft, String> {

    Optional<TailoredCvDraft>
    findByIdAndOwnerUserId(
            String id,
            String ownerUserId
    );

    Optional<TailoredCvDraft>
    findFirstByOwnerUserIdAndCandidateProfileIdAndNormalizedJobIdAndStatusOrderByUpdatedAtDesc(
            String ownerUserId,
            String candidateProfileId,
            String normalizedJobId,
            TailoredCvDraft.Status status
    );
}