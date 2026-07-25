package com.autojob.modules.auth.security;

import com.autojob.modules.auth.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        String normalizedEmail = username
                .trim()
                .toLowerCase(Locale.ROOT);

        return userAccountRepository
                .findByEmailNormalized(normalizedEmail)
                .map(AuthPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Invalid email or password"
                ));
    }
}