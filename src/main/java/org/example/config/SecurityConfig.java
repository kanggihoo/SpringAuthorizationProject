package org.example.config;

import lombok.RequiredArgsConstructor;
import org.example.security.exception.CustomAccessDeniedHandler;
import org.example.security.exception.CustomAuthenticationEntryPoint;
import org.example.security.jwt.ExceptionHandlerFilter;
import org.example.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 환경 설정 클래스
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ExceptionHandlerFilter exceptionHandlerFilter;
  private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
  private final CustomAccessDeniedHandler customAccessDeniedHandler;

  /**
   * 비밀번호 암호화를 위한 BCryptPasswordEncoder 빈 등록
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * AuthenticationManager 빈 등록 (Spring Security 7 / Boot 4 방식) => 얘가 왜 이렇게
   * 해야하는거지??
   */
  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
    return configuration.getAuthenticationManager();
  }

  /**
   * HTTP 보안 필터 체인 설정 (무상태 JWT 방식으로 변경)
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // CSRF 보호 비활성화 (무상태 인증이므로 불필요)
        .csrf(AbstractHttpConfigurer::disable)

        // Form Login 및 Basic Http 활성화 안함 (REST API)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)

        // 세션 관리: 무상태(Stateless) 설정
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 예외 처리 핸들러 설정
        .exceptionHandling(conf -> conf
            .authenticationEntryPoint(customAuthenticationEntryPoint)
            .accessDeniedHandler(customAccessDeniedHandler))

        // 요청 URL별 권한 설정
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/", "/signup", "/login", "/refresh", "/error").permitAll() // 로그인 없이 접근 가능
            .requestMatchers("/admin/**").hasRole("ADMIN") // ADMIN 권한 필요
            .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN") // USER 또는 ADMIN 권한 필요
            .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요
        )

        // JWT 필터 등록
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        // JWT 필터 앞단에 예외 처리 핸들러 필터 등록
        .addFilterBefore(exceptionHandlerFilter, JwtAuthenticationFilter.class);

    return http.build();
  }
}
