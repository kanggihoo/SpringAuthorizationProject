package org.example.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.example.domain.entity.AuthProvider;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.dto.response.TokenResponseDto;
import org.example.security.token.TokenLifecycleService;
import org.example.security.token.delivery.TokenDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

  @Mock
  private TokenLifecycleService tokenLifecycleService;

  @Mock
  private TokenDeliveryService tokenDeliveryService;

  @Mock
  private CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

  @InjectMocks
  private OAuth2AuthenticationSuccessHandler successHandler;

  @Test
  @DisplayName("Issues service-owned JWTs through TokenLifecycleService and redirects")
  void onAuthenticationSuccess_issuesTokensThroughTokenLifecycleServiceAndRedirects() throws Exception {
    ReflectionTestUtils.setField(successHandler, "redirectUri", "http://localhost:3000/oauth2/callback");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
    MockHttpServletResponse response = new MockHttpServletResponse();
    CustomOAuth2User principal = customOAuth2User(
        1L,
        "GOOGLE_123",
        "tester",
        "tester@example.com",
        "ROLE_USER");
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

    when(tokenLifecycleService.issue(eq("GOOGLE_123"), any()))
        .thenReturn(tokenResponse("access-token", "refresh-token"));

    successHandler.onAuthenticationSuccess(request, response, authentication);

    verify(tokenLifecycleService).issue(eq("GOOGLE_123"), any());
    verify(tokenDeliveryService).addRefreshTokenCookie(response, "refresh-token");
    verify(cookieAuthorizationRequestRepository).deleteCookie(
        request,
        response,
        CookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME
    );
    assertThat(response.getRedirectedUrl())
        .isEqualTo("http://localhost:3000/oauth2/callback#accessToken=access-token");
  }

  private CustomOAuth2User customOAuth2User(
      Long userId,
      String username,
      String nickname,
      String email,
      String... roles
  ) {
    User user = User.oauthBuilder()
        .username(username)
        .nickname(nickname)
        .email(email)
        .provider(AuthProvider.GOOGLE)
        .providerId("123")
        .build();
    ReflectionTestUtils.setField(user, "id", userId);
    for (String roleName : roles) {
      user.addRole(new Role(roleName));
    }

    OAuth2User oauth2User = new DefaultOAuth2User(
        java.util.List.of(),
        Map.of("sub", "123", "email", email, "name", nickname),
        "sub"
    );
    return new CustomOAuth2User(oauth2User, user);
  }

  private TokenResponseDto tokenResponse(String accessToken, String refreshToken) {
    return TokenResponseDto.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .build();
  }
}
