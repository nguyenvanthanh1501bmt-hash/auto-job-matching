package com.autojob.modules.auth.api;

import com.autojob.modules.auth.domain.UserAccount;
import com.autojob.modules.auth.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank
            @Email
            @Size(max = 254)
            String email,

            @NotBlank
            @Size(min = 8, max = 128)
            String password,

            @NotBlank
            @Size(max = 100)
            String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank
            @Email
            String email,

            @NotBlank
            String password
    ) {
    }

    public record RefreshRequest(
            @NotBlank
            String refreshToken
    ) {
    }

    public record LogoutRequest(
            @NotBlank
            String refreshToken
    ) {
    }

    public record AuthResponse(
            String tokenType,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            UserResponse user
    ) {
    }

    public record UserResponse(
            String id,
            String email,
            String displayName,
            Set<UserRole> roles,
            Instant createdAt
    ) {
        public static UserResponse from(UserAccount user) {
            return new UserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getRoles(),
                    user.getCreatedAt()
            );
        }
    }

    public record MeResponse(
            boolean authenticated,
            String userId,
            String email,
            Object roles
    ) {
    }
}