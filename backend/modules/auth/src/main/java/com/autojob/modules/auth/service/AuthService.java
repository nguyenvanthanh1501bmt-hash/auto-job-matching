package com.autojob.modules.auth.service;

import com.autojob.modules.auth.api.AuthDtos.AuthResponse;
import com.autojob.modules.auth.api.AuthDtos.LoginRequest;
import com.autojob.modules.auth.api.AuthDtos.RegisterRequest;
import com.autojob.modules.auth.api.AuthDtos.UserResponse;
import com.autojob.modules.auth.domain.UserAccount;
import com.autojob.modules.auth.domain.UserRole;
import com.autojob.modules.auth.domain.UserStatus;
import com.autojob.modules.auth.repository.UserAccountRepository;
import com.autojob.modules.auth.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthResponse register(
            RegisterRequest request,
            RequestMetadata metadata
    ) {
        String normalizedEmail =
                normalizeEmail(request.email());

        if (userAccountRepository.existsByEmailNormalized(
                normalizedEmail
        )) {
            throw emailAlreadyExists();
        }

        Instant now = Instant.now();

        UserAccount user = UserAccount.builder()
                .email(request.email().trim())
                .emailNormalized(normalizedEmail)
                .passwordHash(
                        passwordEncoder.encode(
                                request.password()
                        )
                )
                .displayName(request.displayName().trim())
                .roles(Set.of(UserRole.USER))
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            user = userAccountRepository.save(user);
        } catch (DuplicateKeyException exception) {
            throw emailAlreadyExists();
        }

        return issueTokens(user, metadata);
    }

    public AuthResponse login(
            LoginRequest request,
            RequestMetadata metadata
    ) {
        var authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                normalizeEmail(request.email()),
                                request.password()
                        )
                );

        AuthPrincipal principal =
                (AuthPrincipal) authentication.getPrincipal();

        UserAccount user = userAccountRepository
                .findById(principal.userId())
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS",
                        "Invalid email or password"
                ));

        user.setLastLoginAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        userAccountRepository.save(user);

        return issueTokens(user, metadata);
    }

    public AuthResponse refresh(
            String rawRefreshToken,
            RequestMetadata metadata
    ) {
        RefreshTokenService.RotatedRefreshToken rotated =
                refreshTokenService.rotate(
                        rawRefreshToken,
                        metadata
                );

        UserAccount user = userAccountRepository
                .findById(rotated.userId())
                .filter(account ->
                        account.getStatus() == UserStatus.ACTIVE
                )
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED,
                        "USER_NOT_ACTIVE",
                        "User is not active"
                ));

        JwtTokenService.AccessToken accessToken =
                jwtTokenService.issue(user);

        return new AuthResponse(
                "Bearer",
                accessToken.value(),
                accessToken.expiresAt(),
                rotated.value(),
                rotated.expiresAt(),
                UserResponse.from(user)
        );
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeByRawToken(
                rawRefreshToken
        );
    }

    private AuthResponse issueTokens(
            UserAccount user,
            RequestMetadata metadata
    ) {
        JwtTokenService.AccessToken accessToken =
                jwtTokenService.issue(user);

        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.create(
                        user.getId(),
                        metadata
                );

        return new AuthResponse(
                "Bearer",
                accessToken.value(),
                accessToken.expiresAt(),
                refreshToken.value(),
                refreshToken.expiresAt(),
                UserResponse.from(user)
        );
    }

    private String normalizeEmail(String email) {
        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private AuthException emailAlreadyExists() {
        return new AuthException(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_EXISTS",
                "Email is already registered"
        );
    }
}