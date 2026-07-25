package com.autojob.modules.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("users")
@CompoundIndex(
        name = "uk_users_email_normalized",
        def = "{'emailNormalized': 1}",
        unique = true
)
public class UserAccount {

    @Id
    private String id;

    private String email;
    private String emailNormalized;

    /**
     * BCrypt hash, không bao giờ trả ra API.
     */
    private String passwordHash;

    private String displayName;

    @Builder.Default
    private Set<UserRole> roles = Set.of(UserRole.USER);

    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
}