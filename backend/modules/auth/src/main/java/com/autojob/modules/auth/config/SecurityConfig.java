package com.autojob.modules.auth.config;

import com.autojob.modules.auth.ratelimit.RateLimitFilter;
import com.autojob.modules.auth.security.AuthUserDetailsService;
import com.autojob.modules.auth.security.RestAccessDeniedHandler;
import com.autojob.modules.auth.security.RestAuthenticationEntryPoint;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties({
        AuthProperties.class,
        RateLimitProperties.class
})
public class SecurityConfig {

    private final AuthProperties authProperties;

    private final AuthUserDetailsService userDetailsService;

    private final RestAuthenticationEntryPoint
            authenticationEntryPoint;

    private final RestAccessDeniedHandler
            accessDeniedHandler;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RateLimitFilter rateLimitFilter
    ) throws Exception {

        http
                /*
                 * API stateless bằng JWT nên không dùng
                 * CSRF protection dựa trên session.
                 */
                .csrf(
                        csrf ->
                                csrf.disable()
                )

                .cors(
                        Customizer.withDefaults()
                )

                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS
                                )
                )

                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                authenticationEntryPoint
                                        )
                                        .accessDeniedHandler(
                                                accessDeniedHandler
                                        )
                )

                .oauth2ResourceServer(
                        resourceServer ->
                                resourceServer
                                        .jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                jwtAuthenticationConverter()
                                                        )
                                        )
                                        .authenticationEntryPoint(
                                                authenticationEntryPoint
                                        )
                );

        /*
         * -----------------------------------------------------
         * PUBLIC MODE
         * -----------------------------------------------------
         *
         * Dùng cho local/demo:
         *
         * - tất cả API được permit
         * - controller dùng public-local-user
         *
         * JWT nếu client gửi vẫn được Spring parse.
         */
        if (authProperties.isPublicApiMode()) {

            http.authorizeHttpRequests(
                    authorize ->
                            authorize
                                    .anyRequest()
                                    .permitAll()
            );

        } else {

            /*
             * -------------------------------------------------
             * PRIVATE MODE
             * -------------------------------------------------
             */
            http.authorizeHttpRequests(
                    authorize ->
                            authorize

                                    /*
                                     * CORS preflight.
                                     */
                                    .requestMatchers(
                                            HttpMethod.OPTIONS,
                                            "/**"
                                    )
                                    .permitAll()

                                    /*
                                     * Public infrastructure/auth.
                                     */
                                    .requestMatchers(
                                            "/api/auth/**",
                                            "/actuator/health/**",
                                            "/v3/api-docs/**",
                                            "/swagger-ui/**"
                                    )
                                    .permitAll()

                                    /*
                                     * Admin routes.
                                     */
                                    .requestMatchers(
                                            "/api/admin/**"
                                    )
                                    .hasRole(
                                            "ADMIN"
                                    )

                                    /*
                                     * CV ownership API.
                                     */
                                    .requestMatchers(
                                            "/api/cvs/**"
                                    )
                                    .authenticated()

                                    /*
                                     * Hybrid matching API.
                                     *
                                     * Đây là phần mới của đợt 5.
                                     *
                                     * MatchingController còn kiểm tra
                                     * candidate.ownerUserId ở service,
                                     * nên ta có cả:
                                     *
                                     * Layer 1:
                                     * JWT authentication.
                                     *
                                     * Layer 2:
                                     * resource ownership.
                                     */
                                    .requestMatchers(
                                            "/api/matching/**"
                                    )
                                    .authenticated()

                                    /*
                                     * Các API khác hiện giữ behavior cũ.
                                     */
                                    .anyRequest()
                                    .permitAll()
            );
        }

        /*
         * Rate limit chạy sau JWT Bearer filter,
         * nên SecurityContext đã có principal nếu token hợp lệ.
         */
        http.addFilterAfter(
                rateLimitFilter,
                BearerTokenAuthenticationFilter.class
        );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(
                12
        );
    }

    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(
                        userDetailsService
                );

        provider.setPasswordEncoder(
                passwordEncoder
        );

        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(
            DaoAuthenticationProvider provider
    ) {
        return new ProviderManager(
                provider
        );
    }

    @Bean
    SecretKey jwtSecretKey() {
        byte[] secretBytes;

        try {
            secretBytes =
                    Base64
                            .getDecoder()
                            .decode(
                                    authProperties
                                            .getJwtSecretBase64()
                            );

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                    "autojob.auth.jwt-secret-base64 "
                            + "must be valid Base64",
                    exception
            );
        }

        /*
         * HS256 cần ít nhất 256 bit.
         */
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must contain at least "
                            + "32 decoded bytes"
            );
        }

        return new SecretKeySpec(
                secretBytes,
                "HmacSHA256"
        );
    }

    @Bean
    JwtEncoder jwtEncoder(
            SecretKey jwtSecretKey
    ) {
        JWKSource<SecurityContext> jwkSource =
                new ImmutableSecret<>(
                        jwtSecretKey
                );

        return new NimbusJwtEncoder(
                jwkSource
        );
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey
    ) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder
                        .withSecretKey(
                                jwtSecretKey
                        )
                        .macAlgorithm(
                                MacAlgorithm.HS256
                        )
                        .build();

        decoder.setJwtValidator(
                JwtValidators
                        .createDefaultWithIssuer(
                                authProperties
                                        .getIssuer()
                        )
        );

        return decoder;
    }

    @Bean
    JwtAuthenticationConverter
    jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        /*
         * JWT:
         *
         * "roles": ["USER", "ADMIN"]
         *
         * =>
         *
         * ROLE_USER
         * ROLE_ADMIN
         */
        authoritiesConverter
                .setAuthoritiesClaimName(
                        "roles"
                );

        authoritiesConverter
                .setAuthorityPrefix(
                        "ROLE_"
                );

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter
                .setJwtGrantedAuthoritiesConverter(
                        authoritiesConverter
                );

        /*
         * authentication.getName()
         * sẽ lấy JWT subject.
         *
         * Matching dùng giá trị này làm ownerUserId
         * khi public-api-mode=false.
         */
        converter.setPrincipalClaimName(
                "sub"
        );

        return converter;
    }

    @Bean
    CorsConfigurationSource
    corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                authProperties
                        .getAllowedOrigins()
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "X-Requested-With"
                )
        );

        configuration.setExposedHeaders(
                List.of(
                        "X-RateLimit-Limit",
                        "X-RateLimit-Remaining",
                        "Retry-After"
                )
        );

        configuration.setAllowCredentials(
                true
        );

        configuration.setMaxAge(
                3600L
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}