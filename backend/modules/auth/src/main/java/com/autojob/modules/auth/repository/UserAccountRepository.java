package com.autojob.modules.auth.repository;

import com.autojob.modules.auth.domain.UserAccount;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserAccountRepository
        extends MongoRepository<UserAccount, String> {

    Optional<UserAccount> findByEmailNormalized(
            String emailNormalized
    );

    boolean existsByEmailNormalized(
            String emailNormalized
    );
}