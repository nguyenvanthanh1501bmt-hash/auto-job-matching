package com.autojob.modules.auth.service;

import com.autojob.modules.auth.config.AuthProperties;
import com.autojob.modules.auth.domain.RefreshTokenSession;
import com.autojob.modules.auth.domain.RefreshTokenStatus;
import com.autojob.modules.auth.repository.RefreshTokenSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private static final Base64.Encoder TOKEN_ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenSessionRepository repository;
    private final MongoTemplate mongoTemplate;
    private final AuthProperties authProperties;

    public IssuedRefreshToken create(
            String userId,
            RequestMetadata metadata
    ) {
        return createInFamily(
                userId,
                UUID.randomUUID().toString(),
                metadata
        );
    }

    public RotatedRefreshToken rotate(
            String rawToken,
            RequestMetadata metadata
    ) {
        String currentHash = hash(rawToken);

        RefreshTokenSession current = repository
                .findByTokenHash(currentHash)
                .orElseThrow(this::invalidRefreshToken);

        Instant now = Instant.now();

        if (current.getExpiresAt() == null
                || !current.getExpiresAt().isAfter(now)) {
            throw invalidRefreshToken();
        }

        /*
         * Token đã rotate nhưng bị sử dụng lại:
         * coi như refresh token bị đánh cắp.
         */
        if (current.getStatus() == RefreshTokenStatus.ROTATED) {
            revokeFamily(
                    current.getFamilyId(),
                    "REFRESH_TOKEN_REUSE_DETECTED"
            );

            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_REUSED",
                    "Refresh token reuse detected; token family revoked"
            );
        }

        if (current.getStatus() != RefreshTokenStatus.ACTIVE) {
            throw invalidRefreshToken();
        }

        /*
         * Tạo token mới trước.
         * Nếu xảy ra concurrent refresh, toàn bộ family sẽ bị revoke.
         */
        IssuedRefreshToken next = createInFamily(
                current.getUserId(),
                current.getFamilyId(),
                metadata
        );

        Query query = Query.query(
                Criteria.where("_id")
                        .is(current.getId())
                        .and("status")
                        .is(RefreshTokenStatus.ACTIVE)
                        .and("expiresAt")
                        .gt(now)
        );

        Update update = new Update()
                .set(
                        "status",
                        RefreshTokenStatus.ROTATED
                )
                .set(
                        "replacedByTokenHash",
                        hash(next.value())
                )
                .set("lastUsedAt", now);

        long modified = mongoTemplate
                .updateFirst(
                        query,
                        update,
                        RefreshTokenSession.class
                )
                .getModifiedCount();

        if (modified != 1) {
            revokeFamily(
                    current.getFamilyId(),
                    "CONCURRENT_REFRESH_OR_REUSE"
            );

            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_REUSED",
                    "Refresh token is no longer active"
            );
        }

        return new RotatedRefreshToken(
                current.getUserId(),
                next.value(),
                next.expiresAt()
        );
    }

    public void revokeByRawToken(String rawToken) {
        RefreshTokenSession session = repository
                .findByTokenHash(hash(rawToken))
                .orElse(null);

        if (session != null) {
            revokeFamily(
                    session.getFamilyId(),
                    "USER_LOGOUT"
            );
        }
    }

    private IssuedRefreshToken createInFamily(
            String userId,
            String familyId,
            RequestMetadata metadata
    ) {
        String rawToken = generateRawToken();
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plus(
                authProperties.getRefreshTokenTtl()
        );

        RefreshTokenSession session =
                RefreshTokenSession.builder()
                        .userId(userId)
                        .familyId(familyId)
                        .tokenHash(hash(rawToken))
                        .status(RefreshTokenStatus.ACTIVE)
                        .userAgent(
                                truncate(metadata.userAgent(), 500)
                        )
                        .ipAddress(
                                truncate(metadata.ipAddress(), 100)
                        )
                        .createdAt(createdAt)
                        .expiresAt(expiresAt)
                        .build();

        repository.save(session);

        return new IssuedRefreshToken(
                rawToken,
                expiresAt
        );
    }

    private void revokeFamily(
            String familyId,
            String reason
    ) {
        Instant now = Instant.now();

        mongoTemplate.updateMulti(
                Query.query(
                        Criteria.where("familyId")
                                .is(familyId)
                                .and("status")
                                .ne(RefreshTokenStatus.REVOKED)
                ),
                new Update()
                        .set(
                                "status",
                                RefreshTokenStatus.REVOKED
                        )
                        .set("revokedAt", now)
                        .set("revokeReason", reason),
                RefreshTokenSession.class
        );
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);

        return TOKEN_ENCODER.encodeToString(bytes);
    }

    private String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidRefreshToken();
        }

        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                            rawToken.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }

    private AuthException invalidRefreshToken() {
        return new AuthException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_REFRESH_TOKEN",
                "Refresh token is invalid or expired"
        );
    }

    private String truncate(
            String value,
            int maxLength
    ) {
        if (value == null) {
            return null;
        }

        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

    public record IssuedRefreshToken(
            String value,
            Instant expiresAt
    ) {
    }

    public record RotatedRefreshToken(
            String userId,
            String value,
            Instant expiresAt
    ) {
    }
}