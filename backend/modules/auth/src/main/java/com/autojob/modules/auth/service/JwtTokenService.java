package com.autojob.modules.auth.service;

import com.autojob.modules.auth.config.AuthProperties;
import com.autojob.modules.auth.domain.UserAccount;
import com.autojob.modules.auth.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final AuthProperties authProperties;

    public AccessToken issue(UserAccount user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(
                authProperties.getAccessTokenTtl()
        );

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getId())
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim(
                        "roles",
                        user.getRoles()
                                .stream()
                                .map(UserRole::name)
                                .sorted()
                                .toList()
                )
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        String tokenValue = jwtEncoder
                .encode(
                        JwtEncoderParameters.from(
                                header,
                                claims
                        )
                )
                .getTokenValue();

        return new AccessToken(
                tokenValue,
                expiresAt
        );
    }

    public record AccessToken(
            String value,
            Instant expiresAt
    ) {
    }
}