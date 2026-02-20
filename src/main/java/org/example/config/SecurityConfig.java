package org.example.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 환경 설정 클래스
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  /**
   * 비밀번호 암호화를 위한 BCryptPasswordEncoder 빈 등록
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * HTTP 보안 필터 체인 설정
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // CSRF 보호 비활성화 (테스트 편의성을 위함, 운영 시 검토 필요)
        .csrf(AbstractHttpConfigurer::disable)
        // 요청 URL별 권한 설정
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/", "/signup", "/login", "/error").permitAll() // 로그인 없이 접근 가능
            .requestMatchers("/admin/**").hasRole("ADMIN") // ADMIN 권한 필요
            .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN") // USER 또는 ADMIN 권한 필요
            .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요
        )
        // 폼 로그인 설정
        .formLogin(form -> form
            .defaultSuccessUrl("/", true) // 로그인 성공 시 이동할 URL
            .permitAll())
        // 로그아웃 설정
        .logout(logout -> logout
            .logoutSuccessUrl("/") // 로그아웃 성공 시 이동할 URL
            .invalidateHttpSession(true) // 세션 무효화
        );

    return http.build();
  }
}
