package org.example.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * OAuth2 로그인 실패 시 에러 정보를 담아 프론트엔드로 리다이렉트하는 핸들러.
 *
 * <p>실패 원인 예시:
 * <ul>
 *   <li>사용자가 Google 동의 화면에서 취소를 선택한 경우</li>
 *   <li>state 파라미터 불일치로 CSRF 검증 실패한 경우</li>
 *   <li>지원하지 않는 OAuth2 제공자 ID가 들어온 경우</li>
 *   <li>Google 사용자 정보 조회 중 네트워크 오류가 발생한 경우</li>
 * </ul>
 *
 * <p>에러 메시지는 URL 인코딩하여 Query Parameter로 전달한다.
 * Access Token과 달리 에러 메시지는 민감 정보가 아니므로 Query Parameter 사용이 적절하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

  private final CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

  /** OAuth2 로그인 성공/실패 후 리다이렉트할 프론트엔드 기본 URI */
  @Value("${app.oauth2.redirect-uri}")
  private String redirectUri;

  /**
   * OAuth2 인증 실패 시 에러 메시지를 Query Parameter에 담아 프론트엔드로 리다이렉트한다.
   * 인증 흐름에서 사용된 state 쿠키도 함께 정리한다.
   *
   * @param request   HTTP 요청
   * @param response  HTTP 응답
   * @param exception 인증 실패 원인 예외
   * @throws IOException 리다이렉트 실패 시
   */
  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException exception) throws IOException {

    log.warn("OAuth2 로그인 실패: {}", exception.getMessage());

    // OAuth2 state 저장 쿠키 삭제 (실패 후에도 잔여 쿠키 정리)
    cookieAuthorizationRequestRepository.deleteCookie(
        request, response,
        CookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);

    // 에러 메시지를 URL 인코딩하여 Query Parameter로 전달
    // (에러 메시지는 민감 정보가 아니므로 Query Param 사용 적절)
    String errorMessage = URLEncoder.encode(
        exception.getLocalizedMessage(), StandardCharsets.UTF_8);
    String targetUrl = redirectUri + "?error=" + errorMessage;

    getRedirectStrategy().sendRedirect(request, response, targetUrl);
  }
}
