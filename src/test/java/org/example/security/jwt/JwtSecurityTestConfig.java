package org.example.security.jwt;

import org.example.security.CustomUserDetailsService;
import org.example.dto.response.TokenResponseDto;
import org.example.security.authenticated.AuthenticatedUserService;
import org.example.security.authenticated.AuthenticatedUserServiceImpl;
import org.example.security.exception.CustomAccessDeniedHandler;
import org.example.security.exception.CustomAuthenticationEntryPoint;
import org.example.security.token.TokenLifecycleService;
import org.example.security.token.delivery.TokenDeliveryService;
import org.example.security.token.delivery.TokenDeliveryServiceImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import tools.jackson.databind.ObjectMapper;

@TestConfiguration
class JwtSecurityTestConfig {

    private static final String SECRET =
        "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZw==";

    @Bean
    JwtTokenProvider jwtTokenProvider() {
        return new JwtTokenProvider(SECRET, 3_600_000L, 604_800_000L);
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(
        JwtTokenProvider jwtTokenProvider,
        AuthenticatedUserService authenticatedUserService,
        TokenLifecycleService tokenLifecycleService,
        TokenDeliveryService tokenDeliveryService
    ) {
        return new JwtAuthenticationFilter(
            jwtTokenProvider,
            authenticatedUserService,
            tokenLifecycleService,
            tokenDeliveryService);
    }

    @Bean
    AuthenticatedUserService authenticatedUserService(CustomUserDetailsService customUserDetailsService) {
        return new AuthenticatedUserServiceImpl(customUserDetailsService);
    }

    @Bean
    TokenDeliveryService tokenDeliveryService() {
        return new TokenDeliveryServiceImpl(604_800_000L, true, "Lax");
    }

    @Bean
    TokenLifecycleService tokenLifecycleService() {
        return new TokenLifecycleService() {
            @Override
            public TokenResponseDto issue(String jwtSubject, java.util.List<String> roles) {
                throw new UnsupportedOperationException("Not used in JWT security slice tests");
            }

            @Override
            public TokenResponseDto rotate(String refreshToken) {
                throw new UnsupportedOperationException("Not used in JWT security slice tests");
            }

            @Override
            public void logout(String jwtSubject, String accessToken) {
                throw new UnsupportedOperationException("Not used in JWT security slice tests");
            }

            @Override
            public boolean isAccessTokenAllowed(String accessToken) {
                return true;
            }

            @Override
            public long getRefreshTokenTtlSeconds() {
                return 604_800L;
            }
        };
    }

    @Bean
    ExceptionHandlerFilter exceptionHandlerFilter(
        @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver
    ) {
        return new ExceptionHandlerFilter(resolver);
    }

    @Bean
    CustomAuthenticationEntryPoint customAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new CustomAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    CustomAccessDeniedHandler customAccessDeniedHandler(ObjectMapper objectMapper) {
        return new CustomAccessDeniedHandler(objectMapper);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        ExceptionHandlerFilter exceptionHandlerFilter,
        CustomAuthenticationEntryPoint customAuthenticationEntryPoint,
        CustomAccessDeniedHandler customAccessDeniedHandler
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(conf -> conf
                .authenticationEntryPoint(customAuthenticationEntryPoint)
                .accessDeniedHandler(customAccessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/signup", "/login", "/refresh", "/error",
                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                    "/oauth2/**", "/login/oauth2/**"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(exceptionHandlerFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
