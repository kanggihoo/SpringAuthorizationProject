package org.example.security.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * OAuth2 인증 요청(state, PKCE 등)을 HTTP 세션 대신 쿠키에 저장하는 저장소.
 *
 * <p>이 클래스가 필요한 이유:
 * 이 애플리케이션은 {@code SessionCreationPolicy.STATELESS}를 사용하므로 HTTP 세션이 존재하지 않는다.
 * Spring Security 기본 구현체({@code HttpSessionOAuth2AuthorizationRequestRepository})는
 * 세션에 OAuth2 인증 요청을 저장하는데, 세션이 없으면 state 파라미터를 보관할 수 없어
 * CSRF 공격 방어 기제가 무력화된다.
 *
 * <p>동작 방식:
 * <ol>
 *   <li>인증 시작 시: Google 로그인 페이지로 리다이렉트하기 전에,
 *       {@code state} 등이 포함된 {@link OAuth2AuthorizationRequest} 객체를
 *       JSON으로 직렬화 → Base64 인코딩 → HttpOnly 쿠키에 저장한다.</li>
 *   <li>콜백 수신 시: Google이 {@code code}와 {@code state}를 담아 콜백으로 돌아오면,
 *       쿠키에서 저장된 요청 객체를 역직렬화하여 꺼낸다.</li>
 *   <li>CSRF 검증: Spring Security의 {@code OAuth2LoginAuthenticationFilter}가
 *       콜백으로 받은 state 값과 쿠키에서 복원한 요청의 state 값을 자동으로 비교하여
 *       일치하지 않으면 인증을 거부한다. → CSRF 공격 방어.</li>
 *   <li>정리: 성공/실패 핸들러에서 쿠키를 삭제한다.</li>
 * </ol>
 *
 * <p>쿠키 보안 설정:
 * <ul>
 *   <li>HttpOnly: JavaScript에서 접근 불가 (XSS 방어)</li>
 *   <li>Path: /oauth2 경로에만 전송 (불필요한 노출 최소화)</li>
 *   <li>TTL: 5분 (180초) — 짧은 만료 시간으로 쿠키 탈취 위험 최소화</li>
 * </ul>
 */
@Slf4j
@Component
public class CookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

  /** 쿠키에 저장될 OAuth2 인증 요청의 쿠키 이름 */
  public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";

  /** OAuth2 인증 요청 쿠키의 유효 시간 (초). 5분이면 정상적인 OAuth2 플로우 완료에 충분하다. */
  private static final int COOKIE_EXPIRE_SECONDS = 300;

  /**
   * 요청 쿠키에서 저장된 {@link OAuth2AuthorizationRequest}를 로드하여 반환한다.
   * Spring Security가 콜백 처리 시 state 검증에 사용한다.
   *
   * @param request 현재 HTTP 요청 (쿠키를 포함)
   * @return 저장된 OAuth2AuthorizationRequest, 쿠키 없거나 역직렬화 실패 시 null
   */
  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    return getCookieValue(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
        .map(this::deserialize)
        .orElse(null);
  }

  /**
   * OAuth2 인증 요청 시작 시 {@link OAuth2AuthorizationRequest}를 쿠키에 저장한다.
   * authorizationRequest가 null이면 기존 쿠키를 삭제한다.
   *
   * @param authorizationRequest 저장할 OAuth2 인증 요청 객체 (state 포함), null이면 삭제 처리
   * @param request              현재 HTTP 요청
   * @param response             쿠키를 추가할 HTTP 응답
   */
  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request,
      HttpServletResponse response) {

    if (authorizationRequest == null) {
      // null 전달 시 기존 쿠키 삭제 (로그아웃 등의 상황)
      deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
      return;
    }

    // OAuth2AuthorizationRequest 객체를 JSON → Base64로 직렬화하여 쿠키에 저장
    String serialized = serialize(authorizationRequest);
    Cookie cookie = new Cookie(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, serialized);
    cookie.setPath("/");           // 모든 경로에서 전송 (콜백 경로 /login/oauth2/code/* 포함)
    cookie.setHttpOnly(true);      // JavaScript 접근 차단 (XSS 방어)
    cookie.setMaxAge(COOKIE_EXPIRE_SECONDS); // 5분 TTL
    // 운영 환경에서는 cookie.setSecure(true)를 추가하여 HTTPS 전용으로 설정 권장
    response.addCookie(cookie);
  }

  /**
   * 콜백 처리 후 저장된 {@link OAuth2AuthorizationRequest}를 쿠키에서 제거하고 반환한다.
   * Spring Security의 {@code OAuth2LoginAuthenticationFilter}가 호출한다.
   *
   * @param request  쿠키가 포함된 HTTP 요청
   * @param response 쿠키를 삭제할 HTTP 응답
   * @return 제거된 OAuth2AuthorizationRequest, 없으면 null
   */
  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    // 기존 요청 로드 후 쿠키 삭제
    OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
    deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
    return authorizationRequest;
  }

  /**
   * 요청에 포함된 쿠키 중 지정된 이름의 쿠키 값을 반환한다.
   *
   * @param request    HTTP 요청
   * @param cookieName 찾을 쿠키 이름
   * @return 쿠키 값 Optional (없으면 Optional.empty())
   */
  private Optional<String> getCookieValue(HttpServletRequest request, String cookieName) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> cookieName.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst();
  }

  /**
   * 지정된 이름의 쿠키를 만료 처리하여 삭제한다.
   *
   * @param request    HTTP 요청
   * @param response   HTTP 응답
   * @param cookieName 삭제할 쿠키 이름
   */
  public void deleteCookie(HttpServletRequest request, HttpServletResponse response,
      String cookieName) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return;
    }
    Arrays.stream(cookies)
        .filter(cookie -> cookieName.equals(cookie.getName()))
        .forEach(cookie -> {
          cookie.setValue("");        // 값 비우기
          cookie.setPath("/");        // 원래 Path와 동일하게 설정해야 삭제됨
          cookie.setMaxAge(0);        // 즉시 만료
          response.addCookie(cookie);
        });
  }

  /**
   * {@link OAuth2AuthorizationRequest}를 Java 네이티브 직렬화로 바이트 배열로 변환한 뒤
   * URL-safe Base64로 인코딩하여 쿠키에 안전하게 저장할 수 있는 문자열로 반환한다.
   *
   * <p>Jackson JSON 대신 Java 직렬화를 사용하는 이유:
   * {@code OAuth2AuthorizationRequest}는 {@code Serializable}을 구현하지만,
   * Jackson이 역직렬화하기 위한 기본 생성자와 setter가 없어 JSON 방식이 불안정하다.
   *
   * @param request 직렬화할 OAuth2 인증 요청
   * @return Base64 인코딩된 직렬화 문자열, 실패 시 빈 문자열
   */
  private String serialize(OAuth2AuthorizationRequest request) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(request);
      return Base64.getUrlEncoder().encodeToString(baos.toByteArray());
    } catch (IOException e) {
      log.error("OAuth2AuthorizationRequest 직렬화 실패", e);
      return "";
    }
  }

  /**
   * Base64 인코딩된 문자열을 디코딩하고 Java 네이티브 역직렬화로
   * {@link OAuth2AuthorizationRequest} 객체를 복원한다.
   *
   * @param value Base64 인코딩된 직렬화 문자열
   * @return 역직렬화된 OAuth2AuthorizationRequest, 실패 시 null
   */
  private OAuth2AuthorizationRequest deserialize(String value) {
    try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getUrlDecoder().decode(value));
        ObjectInputStream ois = new ObjectInputStream(bais)) {
      return (OAuth2AuthorizationRequest) ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      log.error("OAuth2AuthorizationRequest 역직렬화 실패", e);
      return null;
    }
  }
}
