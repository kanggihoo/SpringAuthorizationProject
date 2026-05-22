package org.example.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.response.TokenResponseDto;
import org.example.security.authenticated.AuthenticatedUser;
import org.example.security.token.TokenLifecycleService;
import org.example.security.token.delivery.TokenDeliveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles successful OAuth2 login by issuing service-owned tokens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final TokenLifecycleService tokenLifecycleService;
  private final TokenDeliveryService tokenDeliveryService;
  private final CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

  @Value("${app.oauth2.redirect-uri}")
  private String redirectUri;

  @Override
  @Transactional
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) throws IOException {

    AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();

    log.info("OAuth2 login succeeded - userId: {}, username: {}",
        authenticatedUser.getId(), authenticatedUser.getJwtSubject());

    List<String> roles = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());

    TokenResponseDto tokenResponse = tokenLifecycleService.issue(
        authenticatedUser.getJwtSubject(), roles);
    tokenDeliveryService.addRefreshTokenCookie(response, tokenResponse.getRefreshToken());

    cookieAuthorizationRequestRepository.deleteCookie(
        request, response,
        CookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);

    String targetUrl = redirectUri + "#accessToken=" + tokenResponse.getAccessToken();
    log.info("OAuth2 login completed, redirecting to frontend: {}", redirectUri);

    getRedirectStrategy().sendRedirect(request, response, targetUrl);
  }
}
