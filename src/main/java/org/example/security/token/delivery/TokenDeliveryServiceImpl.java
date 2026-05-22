package org.example.security.token.delivery;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Cookie and Authorization header adapter for Token Delivery.
 */
@Service
public class TokenDeliveryServiceImpl implements TokenDeliveryService {

  private static final String REFRESH_TOKEN_COOKIE_NAME = "Refresh-Token";
  private static final String BEARER_PREFIX = "Bearer ";

  private final long refreshTokenTtlSeconds;
  private final boolean refreshCookieSecure;
  private final String refreshCookieSameSite;

  /**
   * Creates a Token Delivery adapter using token lifetime and cookie policy settings.
   */
  public TokenDeliveryServiceImpl(
      @Value("${jwt.refresh-token-expiration}") long refreshTokenExpirationMillis,
      @Value("${app.token-delivery.refresh-cookie.secure:true}") boolean refreshCookieSecure,
      @Value("${app.token-delivery.refresh-cookie.same-site:Lax}") String refreshCookieSameSite
  ) {
    this.refreshTokenTtlSeconds = refreshTokenExpirationMillis / 1000;
    this.refreshCookieSecure = refreshCookieSecure;
    this.refreshCookieSameSite = refreshCookieSameSite;
  }

  @Override
  public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
    ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
        .httpOnly(true)
        .secure(refreshCookieSecure)
        .path("/")
        .maxAge(refreshTokenTtlSeconds)
        .sameSite(refreshCookieSameSite)
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  @Override
  public void expireRefreshTokenCookie(HttpServletResponse response) {
    ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(refreshCookieSecure)
        .path("/")
        .maxAge(0)
        .sameSite(refreshCookieSameSite)
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  @Override
  public Optional<String> readRefreshToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }

    return Arrays.stream(cookies)
        .filter(cookie -> REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(StringUtils::hasText)
        .findFirst();
  }

  @Override
  public Optional<String> resolveBearerAccessToken(HttpServletRequest request) {
    String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
      return Optional.of(bearerToken.substring(BEARER_PREFIX.length()));
    }
    return Optional.empty();
  }
}
