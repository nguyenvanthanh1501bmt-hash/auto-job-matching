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

    // Cấu hình auth được đọc từ application.yml/properties.
    private final AuthProperties authProperties;

    // Service dùng để load thông tin user khi authentication.
    private final AuthUserDetailsService userDetailsService;

    // Xử lý request chưa được authentication.
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    // Xử lý request đã đăng nhập nhưng không có quyền truy cập.
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RateLimitFilter rateLimitFilter
    ) throws Exception {
        http
                // API dùng JWT nên không cần CSRF protection của session.
                .csrf(csrf -> csrf.disable())

                // Bật CORS với cấu hình bên dưới.
                .cors(Customizer.withDefaults())

                // JWT authentication là stateless, server không lưu session.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // Cấu hình response khi authentication/authorization thất bại.
                .exceptionHandling(exceptions ->
                        exceptions
                                .authenticationEntryPoint(
                                        authenticationEntryPoint
                                )
                                .accessDeniedHandler(
                                        accessDeniedHandler
                                )
                )

                // Cấu hình Spring Security Resource Server để xác thực JWT.
                .oauth2ResourceServer(resourceServer ->
                        resourceServer
                                .jwt(jwt ->
                                        jwt.jwtAuthenticationConverter(
                                                jwtAuthenticationConverter()
                                        )
                                )
                                .authenticationEntryPoint(
                                        authenticationEntryPoint
                                )
                );

        /*
         * publicApiMode = true:
         * - Tất cả API đều được phép truy cập.
         * - JWT hợp lệ vẫn được Spring Security parse và tạo Authentication.
         *
         * publicApiMode = false:
         * - Áp dụng authorization rules bên dưới.
         */
        if (authProperties.isPublicApiMode()) {
            http.authorizeHttpRequests(authorize ->
                    authorize.anyRequest().permitAll()
            );
        } else {
            http.authorizeHttpRequests(authorize ->
                    authorize
                            .requestMatchers(
                                    HttpMethod.OPTIONS,
                                    "/**"
                            )
                            .permitAll()
                            .requestMatchers(
                                    "/api/auth/**",
                                    "/actuator/health/**",
                                    "/v3/api-docs/**",
                                    "/swagger-ui/**"
                            )
                            .permitAll()
                            .requestMatchers("/api/admin/**")
                            .hasRole("ADMIN")
                            .requestMatchers("/api/cvs/**")
                            .authenticated()
                            .anyRequest()
                            .permitAll()
            );
        }

        // Rate limit chạy sau BearerTokenAuthenticationFilter,
        // nên request có JWT hợp lệ đã có Authentication trong SecurityContext.
        http.addFilterAfter(
                rateLimitFilter,
                BearerTokenAuthenticationFilter.class
        );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // BCrypt dùng để hash và verify password.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(
            PasswordEncoder passwordEncoder
    ) {
        // Provider xác thực username/password thông qua UserDetailsService.
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(
                        userDetailsService
                );

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(
            DaoAuthenticationProvider provider
    ) {
        // AuthenticationManager ủy quyền authentication cho provider.
        return new ProviderManager(provider);
    }

    @Bean
    SecretKey jwtSecretKey() {
        byte[] secretBytes;

        try {
            // JWT secret được lưu dưới dạng Base64 nên cần decode trước khi sử dụng.
            secretBytes = Base64
                    .getDecoder()
                    .decode(
                            authProperties
                                    .getJwtSecretBase64()
                    );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "autojob.auth.jwt-secret-base64 must be valid Base64",
                    exception
            );
        }

        // Đảm bảo secret có ít nhất 256 bit (32 bytes) cho HS256.
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must contain at least 32 decoded bytes"
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
        // Dùng secret key để ký JWT.
        JWKSource<SecurityContext> jwkSource =
                new ImmutableSecret<>(jwtSecretKey);

        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey
    ) {
        // Decoder dùng cùng secret key để verify chữ ký JWT.
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder
                        .withSecretKey(jwtSecretKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();

        // Ngoài chữ ký, JWT phải có issuer hợp lệ.
        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(
                        authProperties.getIssuer()
                )
        );

        return decoder;
    }

    @Bean
    JwtAuthenticationConverter
    jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        // Lấy role từ claim "roles" trong JWT.
        authoritiesConverter.setAuthoritiesClaimName(
                "roles"
        );

        // Thêm prefix ROLE_ để Spring Security nhận diện role.
        authoritiesConverter.setAuthorityPrefix(
                "ROLE_"
        );

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        // Dùng claim "sub" làm principal/username.
        converter.setPrincipalClaimName("sub");

        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration =
                new CorsConfiguration();

        // Chỉ cho phép các origin được cấu hình trong auth properties.
        configuration.setAllowedOrigins(
                authProperties.getAllowedOrigins()
        );

        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With"
        ));

        // Cho phép frontend đọc các header rate limit này.
        configuration.setExposedHeaders(List.of(
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "Retry-After"
        ));

        configuration.setAllowCredentials(true);

        // Browser có thể cache kết quả preflight trong 1 giờ.
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}