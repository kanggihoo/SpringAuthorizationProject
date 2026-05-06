package org.example.config;

import lombok.RequiredArgsConstructor;
import org.example.security.exception.CustomAccessDeniedHandler;
import org.example.security.exception.CustomAuthenticationEntryPoint;
import org.example.security.jwt.ExceptionHandlerFilter;
import org.example.security.jwt.JwtAuthenticationFilter;
import org.example.security.oauth2.CookieOAuth2AuthorizationRequestRepository;
import org.example.security.oauth2.CustomOAuth2UserService;
import org.example.security.oauth2.OAuth2AuthenticationFailureHandler;
import org.example.security.oauth2.OAuth2AuthenticationSuccessHandler;
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
 * Spring Security 환경 설정 클래스.
 *
 * <p>두 가지 인증 방식을 동시에 지원한다:
 * <ol>
 *   <li>일반 로그인: {@code POST /login} → JWT 발급 (기존 방식 유지)</li>
 *   <li>Google OAuth2 로그인: {@code /oauth2/authorization/google} → Google 동의 →
 *       {@link OAuth2AuthenticationSuccessHandler}에서 JWT 발급 (신규 추가)</li>
 * </ol>
 *
 * <p>세션 정책은 {@code STATELESS}를 유지하며, OAuth2 인증 요청(state)은
 * {@link CookieOAuth2AuthorizationRequestRepository}를 통해 쿠키에 저장하여 CSRF를 방어한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ExceptionHandlerFilter exceptionHandlerFilter;
  private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
  private final CustomAccessDeniedHandler customAccessDeniedHandler;

  // OAuth2 관련 빈
  private final CustomOAuth2UserService customOAuth2UserService;
  private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
  private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
  private final CookieOAuth2AuthorizationRequestRepository cookieOAuth2AuthorizationRequestRepository;

  /**
   * 비밀번호 암호화를 위한 BCryptPasswordEncoder 빈 등록.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * 일반 로그인(AuthService.login)에서 수동으로 인증을 처리하기 위한 AuthenticationManager 빈 등록.
   */
  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration configuration) throws Exception {
    return configuration.getAuthenticationManager();
  }

  /**
   * HTTP 보안 필터 체인 설정.
   *
   * <p>필터 실행 순서:
   * <ol>
   *   <li>{@link ExceptionHandlerFilter}: JWT 필터 단 예외를 MVC GlobalExceptionHandler로 위임</li>
   *   <li>{@link JwtAuthenticationFilter}: Bearer 토큰 검증 및 SecurityContext 주입</li>
   *   <li>Spring Security 기본 필터들 (OAuth2 포함)</li>
   * </ol>
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // CSRF 보호 비활성화 (무상태 JWT 인증이므로 불필요)
        .csrf(AbstractHttpConfigurer::disable)

        // Form Login 및 HTTP Basic 비활성화 (REST API + OAuth2 사용)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)

        // 세션 관리: 무상태(Stateless) 설정 — OAuth2 state는 쿠키로 관리
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 예외 처리 핸들러 설정
        .exceptionHandling(conf -> conf
            .authenticationEntryPoint(customAuthenticationEntryPoint)  // 401 Unauthorized
            .accessDeniedHandler(customAccessDeniedHandler))            // 403 Forbidden

        // 요청 URL별 권한 설정
        .authorizeHttpRequests(auth -> auth
            // 인증 없이 접근 가능한 공개 경로
            .requestMatchers(
                "/", "/signup", "/login", "/refresh", "/error",
                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                // OAuth2 인증 시작 경로 (/oauth2/authorization/google 등)
                "/oauth2/**",
                // OAuth2 콜백 수신 경로 (/login/oauth2/code/google 등)
                "/login/oauth2/**"
            ).permitAll()
            .requestMatchers("/admin/**").hasRole("ADMIN")             // ADMIN 전용
            .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN")   // USER 이상
            .anyRequest().authenticated()                              // 그 외 인증 필요
        )

        // ── OAuth2 소셜 로그인 설정 ──────────────────────────────────────────
        .oauth2Login(oauth2 -> oauth2
            // STATELESS 환경에서 state를 쿠키에 저장하여 CSRF 방어
            // (기본값인 HttpSessionOAuth2AuthorizationRequestRepository는 세션이 없으면 동작 불가)
            .authorizationEndpoint(endpoint -> endpoint
                .authorizationRequestRepository(cookieOAuth2AuthorizationRequestRepository))

            // Google 콜백 수신 경로 (Spring Security 기본값과 동일하나 명시적으로 지정)
            .redirectionEndpoint(endpoint ->
                endpoint.baseUri("/login/oauth2/code/*"))

            // Google로부터 받은 사용자 정보를 우리 서버 User 엔티티와 연결하는 서비스
            .userInfoEndpoint(endpoint ->
                endpoint.userService(customOAuth2UserService))

            // OAuth2 성공 시: JWT 발급 + 프론트엔드 리다이렉트 (#accessToken=...)
            .successHandler(oAuth2AuthenticationSuccessHandler)

            // OAuth2 실패 시: 에러 메시지 포함 프론트엔드 리다이렉트 (?error=...)
            .failureHandler(oAuth2AuthenticationFailureHandler)
        )
        // ─────────────────────────────────────────────────────────────────────

        // JWT 필터 등록: UsernamePasswordAuthenticationFilter 앞에 배치
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        // 예외 처리 필터: JWT 필터 앞에 배치하여 필터 단 예외를 MVC로 위임
        .addFilterBefore(exceptionHandlerFilter, JwtAuthenticationFilter.class);

    return http.build();
  }
}
