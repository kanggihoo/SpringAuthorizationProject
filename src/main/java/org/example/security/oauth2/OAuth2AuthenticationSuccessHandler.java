package org.example.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.User;
import org.example.repository.TokenRedisRepository;
import org.example.security.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final JwtTokenProvider jwtTokenProvider;
  private final TokenRedisRepository tokenRedisRepository;
  private final CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

  @Value("${app.oauth2.redirect-uri}")
  private String redirectUri;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) throws IOException {

    CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
    User user = oAuth2User.getUser();

    log.info("OAuth2 login success - userId: {}, username: {}", user.getId(), user.getUsername());

    List<String> roles = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .toList();

    String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), roles);
    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

    long refreshTokenTtlSeconds = jwtTokenProvider.getRefreshTokenExpiration() / 1000;
    tokenRedisRepository.saveRefreshToken(user.getUsername(), refreshToken, refreshTokenTtlSeconds);

    ResponseCookie refreshCookie = ResponseCookie.from("Refresh-Token", refreshToken)
        .httpOnly(true)
        .secure(false)
        .path("/")
        .maxAge(refreshTokenTtlSeconds)
        .sameSite("Lax")
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

    cookieAuthorizationRequestRepository.deleteCookie(
        request,
        response,
        CookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);

    String targetUrl = redirectUri + "#accessToken=" + accessToken;
    log.info("OAuth2 login completed, redirecting to {}", redirectUri);

    getRedirectStrategy().sendRedirect(request, response, targetUrl);
  }
}
