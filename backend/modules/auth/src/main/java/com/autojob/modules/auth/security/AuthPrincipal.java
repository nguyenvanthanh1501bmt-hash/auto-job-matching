package com.autojob.modules.auth.security;

import com.autojob.modules.auth.domain.UserAccount;
import com.autojob.modules.auth.domain.UserRole;
import com.autojob.modules.auth.domain.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public record AuthPrincipal(
        String userId,
        String email,
        String passwordHash,
        boolean enabled,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails {

    public static AuthPrincipal from(UserAccount user) {
        return new AuthPrincipal(
                user.getId(),
                user.getEmailNormalized(),
                user.getPasswordHash(),
                user.getStatus() == UserStatus.ACTIVE,
                user.getRoles()
                        .stream()
                        .map(UserRole::name)
                        .map(role -> new SimpleGrantedAuthority(
                                "ROLE_" + role
                        ))
                        .toList()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}