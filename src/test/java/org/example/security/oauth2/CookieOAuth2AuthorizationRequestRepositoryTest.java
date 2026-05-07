package org.example.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class CookieOAuth2AuthorizationRequestRepositoryTest {

  private final CookieOAuth2AuthorizationRequestRepository repository =
      new CookieOAuth2AuthorizationRequestRepository();

  @Test
  @DisplayName("OAuth2 인증 요청을 HttpOnly 쿠키에 저장하고 다시 로드할 수 있다")
  void saveAndLoadAuthorizationRequest() {
    MockHttpServletRequest saveRequest = new MockHttpServletRequest();
    MockHttpServletResponse saveResponse = new MockHttpServletResponse();
    OAuth2AuthorizationRequest authorizationRequest = authorizationRequest("state-123");

    repository.saveAuthorizationRequest(authorizationRequest, saveRequest, saveResponse);

    Cookie savedCookie = saveResponse.getCookie(cookieName());
    assertThat(savedCookie).isNotNull();
    assertThat(savedCookie.isHttpOnly()).isTrue();
    assertThat(savedCookie.getPath()).isEqualTo("/");
    assertThat(savedCookie.getMaxAge()).isEqualTo(300);

    MockHttpServletRequest loadRequest = new MockHttpServletRequest();
    loadRequest.setCookies(savedCookie);
    OAuth2AuthorizationRequest loadedRequest = repository.loadAuthorizationRequest(loadRequest);

    assertThat(loadedRequest).isNotNull();
    assertThat(loadedRequest.getState()).isEqualTo("state-123");
    assertThat(loadedRequest.getClientId()).isEqualTo("google-client");
  }

  @Test
  @DisplayName("removeAuthorizationRequest는 저장된 요청을 반환하고 쿠키를 삭제한다")
  void removeAuthorizationRequest_returnsRequestAndDeletesCookie() {
    MockHttpServletRequest saveRequest = new MockHttpServletRequest();
    MockHttpServletResponse saveResponse = new MockHttpServletResponse();
    repository.saveAuthorizationRequest(authorizationRequest("state-456"), saveRequest, saveResponse);

    MockHttpServletRequest removeRequest = new MockHttpServletRequest();
    removeRequest.setCookies(saveResponse.getCookie(cookieName()));
    MockHttpServletResponse removeResponse = new MockHttpServletResponse();

    OAuth2AuthorizationRequest removedRequest =
        repository.removeAuthorizationRequest(removeRequest, removeResponse);

    assertThat(removedRequest).isNotNull();
    assertThat(removedRequest.getState()).isEqualTo("state-456");
    assertThat(removeResponse.getCookie(cookieName()).getMaxAge()).isZero();
    assertThat(removeResponse.getCookie(cookieName()).getValue()).isEmpty();
  }

  @Test
  @DisplayName("authorizationRequest가 null이면 기존 쿠키를 삭제한다")
  void saveAuthorizationRequest_deletesCookie_whenAuthorizationRequestIsNull() {
    Cookie existingCookie = new Cookie(cookieName(), "serialized-request");
    existingCookie.setPath("/");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(existingCookie);
    MockHttpServletResponse response = new MockHttpServletResponse();

    repository.saveAuthorizationRequest(null, request, response);

    assertThat(response.getCookie(cookieName()).getMaxAge()).isZero();
    assertThat(response.getCookie(cookieName()).getValue()).isEmpty();
  }

  @Test
  @DisplayName("쿠키가 없으면 loadAuthorizationRequest는 null을 반환한다")
  void loadAuthorizationRequest_returnsNull_whenCookieDoesNotExist() {
    OAuth2AuthorizationRequest result =
        repository.loadAuthorizationRequest(new MockHttpServletRequest());

    assertThat(result).isNull();
  }

  private OAuth2AuthorizationRequest authorizationRequest(String state) {
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
        .clientId("google-client")
        .redirectUri("http://localhost:8080/login/oauth2/code/google")
        .scopes(Set.of("profile", "email"))
        .state(state)
        .build();
  }

  private String cookieName() {
    return CookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME;
  }
}
