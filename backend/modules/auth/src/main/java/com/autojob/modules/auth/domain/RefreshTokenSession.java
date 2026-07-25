package com.autojob.modules.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("refresh_tokens")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_refresh_tokens_user_status",
                def = "{'userId': 1, 'status': 1}"
        ),
        @CompoundIndex(
                name = "idx_refresh_tokens_family_status",
                def = "{'familyId': 1, 'status': 1}"
        )
})
public class RefreshTokenSession {

    @Id
    private String id;

    private String userId;

    /**
     * Mỗi lần login tạo một token family.
     * Các token được rotate giữ cùng familyId.
     */
    private String familyId;

    /**
     * Chỉ lưu SHA-256 hash, không lưu raw refresh token.
     */
    @Indexed(unique = true)
    private String tokenHash;

    private RefreshTokenStatus status;
    private String replacedByTokenHash;

    private String userAgent;
    private String ipAddress;

    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant revokedAt;
    private String revokeReason;

    /**
     * MongoDB tự xóa document khi token hết hạn.
     */
    @Indexed(
            name = "idx_refresh_tokens_expires_at_ttl",
            expireAfter = "0s"
    )
    private Instant expiresAt;
}