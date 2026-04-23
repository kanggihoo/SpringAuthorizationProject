package org.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import jakarta.servlet.http.Cookie;
import org.example.dto.response.TokenResponseDto;
import org.example.security.CustomUserDetails;
import org.example.security.CustomUserDetailsService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * AuthController 슬라이스 테스트.
 *
 * <p>
 * Security 필터 활성화 상태에서 HTTP 레이어 동작을 검증한다.
 * JWT 커스텀 필터 검증은 별도 통합 테스트(@SpringBootTest)에서 수행한다.
 */
@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private MockMvcTester mvc() {
        return MockMvcTester.create(mockMvc);
    }

    // ======================== POST /logout ========================

    @Test
    @DisplayName("logout: 인증된 사용자가 Authorization 헤더와 함께 요청하면 authService.logout이 호출된다")
    void logout_callsAuthServiceLogout_whenAuthenticated() {
        // given
        CustomUserDetails userDetails = createUserDetails("testuser");
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);

        // when & then
        assertThat(mvc().post().uri("/logout")
                .with(authentication(auth))
                .header("Authorization", "Bearer test-access-token"))
                .hasStatusOk();

        verify(authService).logout(eq("testuser"), eq("test-access-token"));
    }

    @Test
    @DisplayName("logout: 응답에 Refresh-Token 쿠키가 설정된다")
    void logout_deletesCookie() {
        // given
        CustomUserDetails userDetails = createUserDetails("testuser");
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);

        // when & then
        assertThat(mvc().post().uri("/logout")
                .with(authentication(auth))
                .header("Authorization", "Bearer test-access-token"))
                .hasStatusOk()
                .cookies().containsKey("Refresh-Token");
    }

    // ======================== POST /login ========================

    @Test
    @DisplayName("login: 정상 요청 시 200과 accessToken을 반환한다")
    void login_returns200WithTokenResponse() {
        // given
        TokenResponseDto tokenResponse = TokenResponseDto.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .build();
        given(authService.login(any())).willReturn(tokenResponse);
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);

        String body = """
                {"username": "testuser", "password": "password123"}
                """;

        // when & then
        assertThat(mvc().post().uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.accessToken")
                .asString()
                .isEqualTo("access-token");
    }

    @Test
    @DisplayName("login: username이 빈 값이면 422를 반환한다")
    void login_returns422_whenUsernameIsBlank() {
        // given
        String body = """
                {"username": "", "password": "password123"}
                """;

        // when & then
        assertThat(mvc().post().uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    // ======================== POST /refresh ========================

    @Test
    @DisplayName("refresh: Refresh-Token 쿠키가 있으면 200과 새 accessToken을 반환한다")
    void refresh_returns200_whenCookieExists() {
        // given
        TokenResponseDto tokenResponse = TokenResponseDto.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .tokenType("Bearer")
                .build();
        given(authService.refresh(anyString())).willReturn(tokenResponse);
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);

        // when & then
        assertThat(mvc().post().uri("/refresh")
                .cookie(new Cookie("Refresh-Token", "old-refresh-token")))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.accessToken")
                .asString()
                .isEqualTo("new-access-token");
    }

    @Test
    @DisplayName("refresh: Refresh-Token 쿠키가 없으면 400을 반환한다")
    void refresh_returns400_whenNoCookie() {
        // when & then
        assertThat(mvc().post().uri("/refresh"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    /** 테스트용 CustomUserDetails 생성 헬퍼 */
    private CustomUserDetails createUserDetails(String username) {
        org.example.domain.entity.User user = org.example.domain.entity.User.builder()
                .username(username)
                .password("encoded")
                .nickname("테스터")
                .build();
        return new CustomUserDetails(user);
    }
}
