package org.example.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.RefreshToken;
import org.example.domain.entity.User;
import org.example.repository.RefreshTokenRepository;
import org.example.security.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth2 로그인 성공 시 JWT(Access Token + Refresh Token)를 발급하고 프론트엔드로 리다이렉트하는 핸들러.
 *
 * <p>기존 일반 로그인({@code POST /login})과 동일한 JWT 발급 방식을 사용하여
 * 두 인증 방식의 이후 흐름을 완전히 통일한다.
 *
 * <p>토큰 전달 방식:
 * <ul>
 *   <li>Access Token: URL Fragment({@code #accessToken=...})로 전달.
 *       Query Parameter({@code ?})와 달리 Fragment는 서버 로그, Referer 헤더에 포함되지 않아
 *       보안상 더 안전하다.</li>
 *   <li>Refresh Token: {@code HttpOnly} 쿠키로 전달 (기존 방식과 동일).</li>
 * </ul>
 *
 * <p>향후 개선 사항: 일회성 단기 코드(TTL 30초) 교환 방식으로 변경하면
 * Access Token의 URL 노출 자체를 제거할 수 있다. (현재는 Fragment 방식 사용)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenRepository refreshTokenRepository;
  private final CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

  /** OAuth2 로그인 성공/실패 후 리다이렉트할 프론트엔드 기본 URI */
  @Value("${app.oauth2.redirect-uri}")
  private String redirectUri;

  /**
   * OAuth2 인증 성공 시 JWT를 발급하고 프론트엔드로 리다이렉트한다.
   *
   * @param request        HTTP 요청
   * @param response       HTTP 응답
   * @param authentication 인증 성공 정보 (principal은 {@link CustomOAuth2User})
   * @throws IOException 리다이렉트 실패 시
   */
  @Override
  @Transactional
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) throws IOException {

    // 1. 인증 주체에서 우리 서버의 User 엔티티 추출
    CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
    User user = oAuth2User.getUser();

    log.info("OAuth2 로그인 성공 - userId: {}, username: {}", user.getId(), user.getUsername());

    // 2. 권한 목록 추출 (JWT claims에 포함할 ROLE_USER 등)
    List<String> roles = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());

    // 3. JWT 토큰 쌍 발급 (기존 일반 로그인과 동일한 방식)
    String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), roles);
    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

    // 4. Refresh Token을 DB에 저장 (RTR 패턴 — 기존 AuthServiceImpl.login()과 동일한 로직)
    LocalDateTime expiryDate = LocalDateTime.now()
        .plusSeconds(jwtTokenProvider.getRefreshTokenExpiration() / 1000);

    Optional<RefreshToken> existingToken = refreshTokenRepository.findByUserId(user.getId());
    if (existingToken.isPresent()) {
      // 이미 발급된 RT가 있으면 새 값으로 갱신 (Dirty Checking)
      existingToken.get().updateToken(refreshToken, expiryDate);
    } else {
      // 최초 OAuth2 로그인 시 새로 저장
      refreshTokenRepository.save(new RefreshToken(user.getId(), refreshToken, expiryDate));
    }

    // 5. Refresh Token을 HttpOnly 쿠키로 설정 (기존 방식과 동일)
    ResponseCookie refreshCookie = ResponseCookie.from("Refresh-Token", refreshToken)
        .httpOnly(true)     // JavaScript 접근 차단 (XSS 방어)
        .secure(false)      // 운영 환경에서는 true로 변경 (HTTPS 전용)
        .path("/")          // 모든 경로에서 전송
        .maxAge(jwtTokenProvider.getRefreshTokenExpiration() / 1000) // 밀리초 → 초 변환
        .sameSite("Lax")    // CSRF 방어: 동일 사이트 + 일부 cross-site 허용
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

    // 6. OAuth2 state 저장 쿠키 삭제 (인증 흐름 완료 후 정리)
    cookieAuthorizationRequestRepository.deleteCookie(
        request, response,
        CookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);

    // 7. Access Token을 URL Fragment(#)에 담아 프론트엔드로 리다이렉트
    //    Fragment는 서버로 전송되지 않아 Query Param(?)보다 서버 로그 노출 위험이 낮다
    String targetUrl = redirectUri + "#accessToken=" + accessToken;
    log.info("OAuth2 로그인 완료, 프론트엔드로 리다이렉트: {}", redirectUri);

    getRedirectStrategy().sendRedirect(request, response, targetUrl);
  }
}
