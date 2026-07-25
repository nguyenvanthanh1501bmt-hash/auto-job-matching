package com.autojob.modules.auth.api;

import com.autojob.modules.auth.api.AuthDtos.AuthResponse;
import com.autojob.modules.auth.api.AuthDtos.LoginRequest;
import com.autojob.modules.auth.api.AuthDtos.LogoutRequest;
import com.autojob.modules.auth.api.AuthDtos.MeResponse;
import com.autojob.modules.auth.api.AuthDtos.RefreshRequest;
import com.autojob.modules.auth.api.AuthDtos.RegisterRequest;
import com.autojob.modules.auth.service.AuthService;
import com.autojob.modules.auth.service.RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        return authService.register(
                request,
                metadata(servletRequest)
        );
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        return authService.login(
                request,
                metadata(servletRequest)
        );
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest servletRequest
    ) {
        return authService.refresh(
                request.refreshToken(),
                metadata(servletRequest)
        );
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        if (!(authentication
                instanceof JwtAuthenticationToken jwtAuth)
                || !authentication.isAuthenticated()) {
            return new MeResponse(
                    false,
                    null,
                    null,
                    null
            );
        }

        return new MeResponse(
                true,
                jwtAuth.getToken().getSubject(),
                jwtAuth.getToken().getClaimAsString("email"),
                jwtAuth.getToken().getClaim("roles")
        );
    }

    private RequestMetadata metadata(
            HttpServletRequest request
    ) {
        return new RequestMetadata(
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
    }
}