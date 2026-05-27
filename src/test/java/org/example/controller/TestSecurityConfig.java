package org.example.controller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * AuthControllerTest 전용 Security 설정.
 *
 * <p>OAuth2 / JWT 커스텀 필터 없이 CSRF disable + STATELESS 만 적용한다.
 * 실제 보안 설정 검증은 @SpringBootTest 통합 테스트에서 수행한다.
 */
@TestConfiguration
class TestSecurityConfig {

  @Bean
  SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/login", "/refresh", "/signup").permitAll()
            .requestMatchers("/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated());
    return http.build();
  }
}
