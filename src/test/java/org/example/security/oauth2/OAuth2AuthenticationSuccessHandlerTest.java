package org.example.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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

    private static final String REDIRECT_URI = "http://localhost:3000/oauth2/callback";
    private static final String USERNAME = "GOOGLE_123";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private TokenRedisRepository tokenRedisRepository;

    @Mock
    private CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    @Test
    @DisplayName("OAuth2 로그인 성공 시 refresh token 을 Redis 에 저장하고 쿠키로 내려준다")
    void onAuthenticationSuccess_savesRefreshTokenAndSetsCookie() throws Exception {
        MockHttpServletRequest request = oauth2CallbackRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        UsernamePasswordAuthenticationToken authentication = oauth2Authentication();
        givenSuccessHandlerTokens();

        successHandler.onAuthenticationSuccess(request, response, authentication);

        verify(tokenRedisRepository).saveRefreshToken(USERNAME, REFRESH_TOKEN, 604800L);
        assertThat(response.getHeader("Set-Cookie")).contains("Refresh-Token=" + REFRESH_TOKEN);
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly");
        assertThat(response.getHeader("Set-Cookie")).contains("SameSite=Lax");
    }

    @Test
    @DisplayName("OAuth2 로그인 성공 시 oauth cookie 를 지우고 access token fragment 로 redirect 한다")
    void onAuthenticationSuccess_clearsCookieAndRedirects() throws Exception {
        MockHttpServletRequest request = oauth2CallbackRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        UsernamePasswordAuthenticationToken authentication = oauth2Authentication();
        givenSuccessHandlerTokens();

        successHandler.onAuthenticationSuccess(request, response, authentication);

        verify(cookieAuthorizationRequestRepository).deleteCookie(
                request,
                response,
                CookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        assertThat(response.getRedirectedUrl())
                .isEqualTo(REDIRECT_URI + "#accessToken=" + ACCESS_TOKEN);
    }

    private MockHttpServletRequest oauth2CallbackRequest() {
        return new MockHttpServletRequest("GET", "/login/oauth2/code/google");
    }

    private UsernamePasswordAuthenticationToken oauth2Authentication() {
        CustomOAuth2User principal = customOAuth2User(1L, USERNAME, "tester", "tester@example.com", "ROLE_USER");
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private void givenSuccessHandlerTokens() {
        ReflectionTestUtils.setField(successHandler, "redirectUri", REDIRECT_URI);
        when(jwtTokenProvider.generateAccessToken(USERNAME, List.of("ROLE_USER")))
                .thenReturn(ACCESS_TOKEN);
        when(jwtTokenProvider.generateRefreshToken(USERNAME))
                .thenReturn(REFRESH_TOKEN);
        when(jwtTokenProvider.getRefreshTokenExpiration())
                .thenReturn(604_800_000L);
    }

    private CustomOAuth2User customOAuth2User(
            Long userId,
            String username,
            String nickname,
            String email,
            String... roles) {
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
                List.of(),
                Map.of("sub", "123", "email", email, "name", nickname),
                "sub");
        return new CustomOAuth2User(oauth2User, user);
    }
}
