package org.example.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.example.domain.entity.AuthProvider;
import org.example.domain.entity.RefreshToken;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.repository.RefreshTokenRepository;
import org.example.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    @Test
    @DisplayName("Issues JWTs, stores a new refresh token, clears oauth cookie, and redirects")
    void onAuthenticationSuccess_issuesTokensAndRedirects_forNewRefreshToken() throws Exception {
        ReflectionTestUtils.setField(successHandler, "redirectUri", "http://localhost:3000/oauth2/callback");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CustomOAuth2User principal = customOAuth2User(1L, "GOOGLE_123", "tester", "tester@example.com", "ROLE_USER");
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(jwtTokenProvider.generateAccessToken(eq("GOOGLE_123"), any()))
            .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken("GOOGLE_123"))
            .thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiration())
            .thenReturn(604_800_000L);
        when(refreshTokenRepository.findByUserId(1L))
            .thenReturn(Optional.empty());

        successHandler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken savedToken = refreshTokenCaptor.getValue();
        assertThat(savedToken.getUserId()).isEqualTo(1L);
        assertThat(savedToken.getRefreshToken()).isEqualTo("refresh-token");

        verify(cookieAuthorizationRequestRepository).deleteCookie(
            request,
            response,
            CookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME
        );
        assertThat(response.getRedirectedUrl())
            .isEqualTo("http://localhost:3000/oauth2/callback#accessToken=access-token");
        assertThat(response.getHeader("Set-Cookie")).contains("Refresh-Token=refresh-token");
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly");
    }

    @Test
    @DisplayName("Updates an existing refresh token instead of creating a new row")
    void onAuthenticationSuccess_updatesExistingRefreshToken() throws Exception {
        ReflectionTestUtils.setField(successHandler, "redirectUri", "http://localhost:3000/oauth2/callback");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CustomOAuth2User principal = customOAuth2User(1L, "GOOGLE_123", "tester", "tester@example.com", "ROLE_USER");
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        RefreshToken existingToken = RefreshToken.builder()
            .userId(1L)
            .refreshToken("old-refresh-token")
            .expiryDate(java.time.LocalDateTime.now().minusDays(1))
            .build();

        when(jwtTokenProvider.generateAccessToken(eq("GOOGLE_123"), any()))
            .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken("GOOGLE_123"))
            .thenReturn("new-refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiration())
            .thenReturn(604_800_000L);
        when(refreshTokenRepository.findByUserId(1L))
            .thenReturn(Optional.of(existingToken));

        successHandler.onAuthenticationSuccess(request, response, authentication);

        verify(refreshTokenRepository, never()).save(any());
        assertThat(existingToken.getRefreshToken()).isEqualTo("new-refresh-token");
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
}
