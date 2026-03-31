package org.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.example.TestFixtures;
import org.example.config.SecurityConfig;
import org.example.dto.request.LoginRequestDto;
import org.example.dto.request.SignupRequest;
import org.example.dto.response.TokenResponseDto;
import org.example.security.CustomUserDetails;
import org.example.security.CustomUserDetailsService;
import org.example.security.exception.CustomAccessDeniedHandler;
import org.example.security.exception.CustomAuthenticationEntryPoint;
import org.example.security.jwt.ExceptionHandlerFilter;
import org.example.security.jwt.JwtAuthenticationFilter;
import org.example.security.jwt.JwtTokenProvider;
import org.example.service.AuthService;
import org.example.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController — WebMvc DB 슬라이스 테스트")
class AuthControllerTest {

    @Test
        void testName() {
                
        }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private ExceptionHandlerFilter exceptionHandlerFilter;

    @MockitoBean
    private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @MockitoBean
    private CustomAccessDeniedHandler customAccessDeniedHandler;

    @Test
    @DisplayName("signup: 유효한 요청시 200 OK 반환")
    void signup_returns200_forValidRequest() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("testuser");
        request.setPassword("password123!");
        request.setNickname("nickname");

        assertThat(mvc.post().uri("/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .hasStatusOk();
    }

    @Test
    @DisplayName("signup: 아이디 빈 문자열시 422 반환")
    void signup_returns422_forBlankUsername() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("");
        request.setPassword("password123!");
        request.setNickname("nickname");

        assertThat(mvc.post().uri("/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT); // 422 Validation
    }

    @Test
    @DisplayName("signup: 아이디 길이가 짧을 경우 422 반환")
    void signup_returns422_forTooShortUsername() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("abc"); // min 4
        request.setPassword("password123!");
        request.setNickname("nickname");

        assertThat(mvc.post().uri("/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("signup: 비밀번호 길이가 짧을 경우 422 반환")
    void signup_returns422_forShortPassword() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("testuser");
        request.setPassword("short"); // min 8
        request.setNickname("nickname");

        assertThat(mvc.post().uri("/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("signup: 중복 사용자 발생 시 서비스가 RuntimeException을 던지면 400 반환")
    void signup_returns400_whenServiceThrowsDuplicate() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("testuser");
        request.setPassword("password123!");
        request.setNickname("nickname");

        doThrow(new RuntimeException("Duplicate")).when(userService).signup(any());

        assertThat(mvc.post().uri("/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("login: 정상 로그인 시 200과 함께 AccessToken(Body) 및 RefreshToken(Cookie) 반환")
    void login_returns200_withAccessToken_andRefreshTokenCookie() throws Exception {
        LoginRequestDto request = new LoginRequestDto();
        ReflectionTestUtils.setField(request, "username", "testuser");
        ReflectionTestUtils.setField(request, "password", "password123!");

        TokenResponseDto responseDto = TokenResponseDto.builder()
                .accessToken("access-token-value")
                .tokenType("Bearer")
                .refreshToken("refresh-token-value")
                .build();

        given(authService.login(any())).willReturn(responseDto);
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604800000L); // 7일

        var response = mvc.post().uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));

        assertThat(response)
                .hasStatusOk()
                .bodyJson().extractingPath("$.accessToken").asString().isEqualTo("access-token-value");
        assertThat(response)
                .cookies()
                .containsCookie("Refresh-Token")
                .hasValue("Refresh-Token", "refresh-token-value");
    }

    @Test
    @DisplayName("login: 빈 값이 주어지면 422 반환")
    void login_returns422_forBlankCredentials() throws Exception {
        LoginRequestDto request = new LoginRequestDto();
        ReflectionTestUtils.setField(request, "username", "");
        ReflectionTestUtils.setField(request, "password", "");

        assertThat(mvc.post().uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("login: 인증 실패로 예외 발생 시 400 반환")
    void login_returns400_whenAuthenticationFails() throws Exception {
        LoginRequestDto request = new LoginRequestDto();
        ReflectionTestUtils.setField(request, "username", "testuser");
        ReflectionTestUtils.setField(request, "password", "wrongpass");

        given(authService.login(any())).willThrow(new RuntimeException("Auth fail"));

        assertThat(mvc.post().uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithUserDetails("alice")
    @DisplayName("logout: 인증 상태에서 호출 시 쿠키 삭제와 함께 200 반환")
    void logout_returns200_andClearsCookie_whenAuthenticated() throws Exception {
        CustomUserDetails mockDetails = TestFixtures.buildUserDetails(1L, "alice", "ROLE_USER");
        given(customUserDetailsService.loadUserByUsername("alice")).willReturn(mockDetails);

        assertThat(mvc.post().uri("/logout"))
                .hasStatusOk()
                .cookies()
                .containsCookie("Refresh-Token")
                .hasMaxAge("Refresh-Token", java.time.Duration.ZERO);

        verify(authService).logout(1L);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("logout: 인증되지 않은 사용자가 호출 시 401 반환")
    void logout_returns401_whenNotAuthenticated() throws Exception {
        assertThat(mvc.post().uri("/logout"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("refresh: 유효한 RT 쿠키 전달 시 새 토큰과 쿠키 반환 (200 OK)")
    void refresh_returns200_withNewTokens() {
        TokenResponseDto responseDto = TokenResponseDto.builder()
                .accessToken("new-access-token")
                .tokenType("Bearer")
                .refreshToken("new-refresh-token")
                .build();

        given(authService.refresh("old-rt")).willReturn(responseDto);
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604800000L);

        Cookie cookie = new Cookie("Refresh-Token", "old-rt");

        var response = mvc.post().uri("/refresh").cookie(cookie);

        assertThat(response)
                .hasStatusOk()
                .bodyJson().extractingPath("$.accessToken").asString().isEqualTo("new-access-token");
        assertThat(response)
                .cookies()
                .containsCookie("Refresh-Token")
                .hasValue("Refresh-Token", "new-refresh-token");
    }

    @Test
    @DisplayName("refresh: 쿠키가 없을 경우 400 반환")
    void refresh_returns400_whenNoCookiePresent() {
        assertThat(mvc.post().uri("/refresh"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("refresh: 토큰 만료 예외 발생 시 401 반환")
    void refresh_returns401_whenTokenExpired() {
        given(authService.refresh("expired-rt")).willThrow(new ExpiredJwtException(null, null, "Expired"));
        Cookie cookie = new Cookie("Refresh-Token", "expired-rt");

        assertThat(mvc.post().uri("/refresh").cookie(cookie))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
