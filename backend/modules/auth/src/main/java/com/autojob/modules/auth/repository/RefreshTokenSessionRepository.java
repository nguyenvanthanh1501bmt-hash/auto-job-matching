package com.autojob.modules.auth.repository;

import com.autojob.modules.auth.domain.RefreshTokenSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RefreshTokenSessionRepository
        extends MongoRepository<RefreshTokenSession, String> {

    Optional<RefreshTokenSession> findByTokenHash(
            String tokenHash
    );
}