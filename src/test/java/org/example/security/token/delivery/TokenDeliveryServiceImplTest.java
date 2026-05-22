package org.example.security.token.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TokenDeliveryServiceImplTest {

  private final TokenDeliveryServiceImpl tokenDeliveryService =
      new TokenDeliveryServiceImpl(604_800_000L, true, "Lax");

  @Test
  @DisplayName("adds Refresh Token as an HttpOnly Secure SameSite cookie")
  void addRefreshTokenCookie_addsSecureHttpOnlyCookie() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    tokenDeliveryService.addRefreshTokenCookie(response, "refresh-token");

    String setCookie = response.getHeader("Set-Cookie");
    assertThat(setCookie).contains("Refresh-Token=refresh-token");
    assertThat(setCookie).contains("Max-Age=604800");
    assertThat(setCookie).contains("Path=/");
    assertThat(setCookie).contains("Secure");
    assertThat(setCookie).contains("HttpOnly");
    assertThat(setCookie).contains("SameSite=Lax");
  }

  @Test
  @DisplayName("expires the Refresh Token cookie")
  void expireRefreshTokenCookie_expiresCookie() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    tokenDeliveryService.expireRefreshTokenCookie(response);

    String setCookie = response.getHeader("Set-Cookie");
    assertThat(setCookie).contains("Refresh-Token=");
    assertThat(setCookie).contains("Max-Age=0");
    assertThat(setCookie).contains("Path=/");
    assertThat(setCookie).contains("Secure");
    assertThat(setCookie).contains("HttpOnly");
    assertThat(setCookie).contains("SameSite=Lax");
  }

  @Test
  @DisplayName("reads Refresh Token from the delivery cookie")
  void readRefreshToken_returnsCookieValue() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("Refresh-Token", "refresh-token"));

    assertThat(tokenDeliveryService.readRefreshToken(request))
        .contains("refresh-token");
  }

  @Test
  @DisplayName("returns empty when Refresh Token cookie is missing")
  void readRefreshToken_returnsEmptyWhenMissing() {
    MockHttpServletRequest request = new MockHttpServletRequest();

    assertThat(tokenDeliveryService.readRefreshToken(request)).isEmpty();
  }

  @Test
  @DisplayName("resolves Bearer Access Token from Authorization header")
  void resolveBearerAccessToken_returnsBearerToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer access-token");

    assertThat(tokenDeliveryService.resolveBearerAccessToken(request))
        .contains("access-token");
  }

  @Test
  @DisplayName("ignores non-Bearer Authorization header")
  void resolveBearerAccessToken_returnsEmptyForNonBearerHeader() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Basic access-token");

    assertThat(tokenDeliveryService.resolveBearerAccessToken(request)).isEmpty();
  }
}
