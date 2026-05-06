package org.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.example.domain.entity.User;
import org.example.dto.request.LoginRequestDto;
import org.example.dto.request.SignupRequest;
import org.example.dto.response.TokenResponseDto;
import org.example.repository.TokenRedisRepository;
import org.example.security.CustomUserDetails;
import org.example.security.CustomUserDetailsService;
import org.example.security.jwt.JwtTokenProvider;
import org.example.service.AuthService;
import org.example.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;
import tools.jackson.core.JacksonException;

import tools.jackson.databind.json.JsonMapper;

/**
 * AuthController 슬라이스 테스트.
 *
 * <p>
 * 운영용 SecurityConfig 대신 테스트 전용 최소 보안 체인 위에서 컨트롤러의 HTTP 입출력을 검증한다.
 * JWT 커스텀 필터 검증은 별도 테스트에서 수행한다.
 */
@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private TokenRedisRepository tokenRedisRepository;

    // ======================== POST /logout ========================

    @Nested
    @DisplayName("POST /logout")
    class Logout {

        @Test
        @DisplayName("인증된 사용자가 Authorization 헤더와 함께 요청하면 authService.logout이 호출된다")
        void callsAuthServiceLogout_whenAuthenticated() {
            assertThat(mvc.post().uri("/logout")
                    .with(authenticatedUser("testuser"))
                    .header("Authorization", "Bearer test-access-token"))
                    .hasStatusOk();

            verify(authService).logout(eq("testuser"), eq("test-access-token"));
        }

        @Test
        @DisplayName("응답에 Refresh-Token 쿠키가 maxAge=0으로 설정된다")
        void deletesCookieWithMaxAgeZero() {
            assertThat(mvc.post().uri("/logout")
                    .with(authenticatedUser("testuser"))
                    .header("Authorization", "Bearer test-access-token"))
                    .hasStatusOk()
                    .cookies()
                    .extractingByKey("Refresh-Token")
                    .satisfies(cookie -> {
                        assertThat(cookie.getMaxAge()).isZero();
                    });
        }

        @Test
        @DisplayName("미인증 상태면 403을 반환한다")
        void returnsForbidden_whenAnonymous() {
            assertThat(mvc.post().uri("/logout"))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    // ======================== POST /login ========================

    @Nested
    @DisplayName("POST /login")
    class Login {

        @BeforeEach
        void setUp() {
            TokenResponseDto tokenResponse = TokenResponseDto.builder()
                    .accessToken("access-token")
                    .refreshToken("refresh-token")
                    .tokenType("Bearer")
                    .build();
            given(authService.login(any())).willReturn(tokenResponse);
            given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);
        }

        @Test
        @DisplayName("정상 요청 시 Refresh-Token 쿠키를 설정하고 200을 반환한다")
        void returns200WithRefreshTokenCookie() {
            assertThat(mvc.post().uri("/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(writeJson(loginRequest("testuser", "password123"))))
                    .hasStatusOk()
                    .cookies()
                    .extractingByKey("Refresh-Token")
                    .satisfies(cookie -> {
                        assertThat(cookie.getValue()).isEqualTo("refresh-token");
                        assertThat(cookie.isHttpOnly()).isTrue();
                        assertThat(cookie.getSecure()).isTrue();
                        assertThat(cookie.getPath()).isEqualTo("/");
                        assertThat(cookie.getMaxAge()).isEqualTo(604_800);
                    });
        }

        @Test
        @DisplayName("정상 요청 시 응답 본문에 accessToken을 반환한다")
        void returns200WithTokenResponse() {
            assertThat(mvc.post().uri("/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(writeJson(loginRequest("testuser", "password123"))))
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(TokenResponseDto.class)
                    .satisfies(response -> assertThat(response.getAccessToken()).isEqualTo("access-token"));
        }

        @Test
        @DisplayName("username이 빈 값이면 422를 반환한다")
        void returns422_whenUsernameIsBlank() {
            assertThat(mvc.post().uri("/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(writeJson(loginRequest("", "password123"))))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        }
    }

    // ======================== POST /refresh ========================

    @Nested
    @DisplayName("POST /refresh")
    class Refresh {

        @BeforeEach
        void setUp() {
            TokenResponseDto tokenResponse = TokenResponseDto.builder()
                    .accessToken("new-access-token")
                    .refreshToken("new-refresh-token")
                    .tokenType("Bearer")
                    .build();
            given(authService.refresh(anyString())).willReturn(tokenResponse);
            given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);
        }

        @Test
        @DisplayName("Refresh-Token 쿠키가 있으면 새 Refresh-Token 쿠키를 재설정하고 200을 반환한다")
        void returns200WithRefreshedCookie_whenCookieExists() {
            assertThat(mvc.post().uri("/refresh")
                    .cookie(new Cookie("Refresh-Token", "old-refresh-token")))
                    .hasStatusOk()
                    .cookies()
                    .extractingByKey("Refresh-Token")
                    .satisfies(cookie -> {
                        assertThat(cookie.getValue()).isEqualTo("new-refresh-token");
                        assertThat(cookie.isHttpOnly()).isTrue();
                        assertThat(cookie.getSecure()).isTrue();
                        assertThat(cookie.getPath()).isEqualTo("/");
                        assertThat(cookie.getMaxAge()).isEqualTo(604_800);
                    });
        }

        @Test
        @DisplayName("Refresh-Token 쿠키가 있으면 응답 본문에 새 accessToken을 반환한다")
        void returns200_whenCookieExists() {
            assertThat(mvc.post().uri("/refresh")
                    .cookie(new Cookie("Refresh-Token", "old-refresh-token")))
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(TokenResponseDto.class)
                    .satisfies(response -> assertThat(response.getAccessToken()).isEqualTo("new-access-token"));
        }

        @Test
        @DisplayName("Refresh-Token 쿠키가 없으면 400을 반환한다")
        void returns400_whenNoCookie() {
            assertThat(mvc.post().uri("/refresh"))
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    // ======================== POST /signup ========================

    @Nested
    @DisplayName("POST /signup")
    class Signup {

        @Test
        @DisplayName("정상 요청 시 userService.signup이 호출되고 200을 반환한다")
        void callsUserServiceAndReturns200() {
            assertThat(mvc.post().uri("/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(writeJson(signupRequest("newuser", "password123", "새유저"))))
                    .hasStatusOk();

            verify(userService).signup(any(SignupRequest.class));
        }

        @Test
        @DisplayName("username이 빈 값이면 422를 반환한다")
        void returns422_whenUsernameIsBlank() {
            assertThat(mvc.post().uri("/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(writeJson(signupRequest("", "password123", "새유저"))))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        }
    }

    // ======================== helpers ========================

    private RequestPostProcessor authenticatedUser(String username) {
        CustomUserDetails userDetails = createUserDetails(username);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null,
                userDetails.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    private CustomUserDetails createUserDetails(String username) {
        User user = User.builder()
                .username(username)
                .password("encoded")
                .nickname("테스터")
                .build();
        return new CustomUserDetails(user);
    }

    private LoginRequestDto loginRequest(String username, String password) {
        return LoginRequestDto.builder()
                .username(username)
                .password(password)
                .build();
    }

    private SignupRequest signupRequest(String username, String password, String nickname) {
        SignupRequest request = new SignupRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setNickname(nickname);
        return request;
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("테스트 JSON 직렬화에 실패했습니다.", e);
        }
    }
}
