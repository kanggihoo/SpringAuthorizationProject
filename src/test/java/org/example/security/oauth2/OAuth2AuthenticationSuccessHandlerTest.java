package org.example.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.example.domain.entity.AuthProvider;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.repository.TokenRedisRepository;
import org.example.security.jwt.JwtTokenProvider;
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
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private TokenRedisRepository tokenRedisRepository;

    @Mock
    private CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    @Test
    @DisplayName("OAuth2 로그인 성공 시 refresh token 을 Redis 에 저장한다")
    void onAuthenticationSuccess_savesRefreshTokenToRedis() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CustomOAuth2User principal = customOAuth2User(1L, "GOOGLE_123", "tester", "tester@example.com", "ROLE_USER");
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        ReflectionTestUtils.setField(successHandler, "redirectUri", "http://localhost:3000/oauth2/callback");

        when(jwtTokenProvider.generateAccessToken("GOOGLE_123", java.util.List.of("ROLE_USER")))
            .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken("GOOGLE_123"))
            .thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiration())
            .thenReturn(604_800_000L);

        successHandler.onAuthenticationSuccess(request, response, authentication);

        verify(tokenRedisRepository).saveRefreshToken("GOOGLE_123", "refresh-token", 604800L);
    }

    @Test
    @DisplayName("OAuth2 로그인 성공 시 refresh token 쿠키를 설정한다")
    void onAuthenticationSuccess_setsRefreshTokenCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CustomOAuth2User principal = customOAuth2User(1L, "GOOGLE_123", "tester", "tester@example.com", "ROLE_USER");
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        ReflectionTestUtils.setField(successHandler, "redirectUri", "http://localhost:3000/oauth2/callback");

        when(jwtTokenProvider.generateAccessToken("GOOGLE_123", java.util.List.of("ROLE_USER")))
            .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken("GOOGLE_123"))
            .thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiration())
            .thenReturn(604_800_000L);

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getHeader("Set-Cookie")).contains("Refresh-Token=refresh-token");
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly");
        assertThat(response.getHeader("Set-Cookie")).contains("SameSite=Lax");
    }

    @Test
    @DisplayName("OAuth2 로그인 성공 시 oauth cookie 를 지우고 access token fragment 로 redirect 한다")
    void onAuthenticationSuccess_clearsCookieAndRedirects() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CustomOAuth2User principal = customOAuth2User(1L, "GOOGLE_123", "tester", "tester@example.com", "ROLE_USER");
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        ReflectionTestUtils.setField(successHandler, "redirectUri", "http://localhost:3000/oauth2/callback");

        when(jwtTokenProvider.generateAccessToken("GOOGLE_123", java.util.List.of("ROLE_USER")))
            .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken("GOOGLE_123"))
            .thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiration())
            .thenReturn(604_800_000L);

        successHandler.onAuthenticationSuccess(request, response, authentication);

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
}
