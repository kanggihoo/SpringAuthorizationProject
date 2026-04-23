package org.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.Cookie;
import org.example.dto.response.TokenResponseDto;
import org.example.security.CustomUserDetails;
import org.example.security.CustomUserDetailsService;
import org.example.security.jwt.JwtTokenProvider;
import org.example.service.AuthService;
import org.example.service.UserService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    private MockMvcTester mvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // ======================== POST /logout ========================

    @Nested
    @DisplayName("POST /logout")
    class Logout {

        @Test
        @DisplayName("인증된 사용자가 Authorization 헤더와 함께 요청하면 authService.logout이 호출된다")
        void callsAuthServiceLogout_whenAuthenticated() {
            given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);

            assertThat(mvc.post().uri("/logout")
                    .with(authenticatedUser("testuser"))
                    .header("Authorization", "Bearer test-access-token"))
                    .hasStatusOk();

            verify(authService).logout(eq("testuser"), eq("test-access-token"));
        }

        @Test
        @DisplayName("응답에 Refresh-Token 쿠키가 maxAge=0으로 설정된다")
        void deletesCookieWithMaxAgeZero() {
            given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);

            assertThat(mvc.post().uri("/logout")
                    .with(authenticatedUser("testuser"))
                    .header("Authorization", "Bearer test-access-token"))
                    .hasStatusOk()
                    .cookies()
                    .extractingByKey("Refresh-Token")
                    .satisfies(cookie -> {
                        assertThat(cookie.getMaxAge()).isZero();
                        assertThat(cookie.getPath()).isEqualTo("/");
                    });
        }

        @Test
        @DisplayName("미인증 상태면 401/403을 반환한다")
        void returnsUnauthorized_whenAnonymous() {
            assertThat(mvc.post().uri("/logout"))
                    .hasStatus4xxClientError();
        }
    }

    // ======================== POST /login ========================

    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("정상 요청 시 200과 accessToken을 반환한다")
        void returns200WithTokenResponse() {
            TokenResponseDto tokenResponse = TokenResponseDto.builder()
                    .accessToken("access-token")
                    .refreshToken("refresh-token")
                    .tokenType("Bearer")
                    .build();
            given(authService.login(any())).willReturn(tokenResponse);
            given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);

            assertThat(mvc.post().uri("/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"username": "testuser", "password": "password123"}
                            """))
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
                    .content("""
                            {"username": "", "password": "password123"}
                            """))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        }
    }

    // ======================== POST /refresh ========================

    @Nested
    @DisplayName("POST /refresh")
    class Refresh {

        @Test
        @DisplayName("Refresh-Token 쿠키가 있으면 200과 새 accessToken을 반환한다")
        void returns200_whenCookieExists() {
            TokenResponseDto tokenResponse = TokenResponseDto.builder()
                    .accessToken("new-access-token")
                    .refreshToken("new-refresh-token")
                    .tokenType("Bearer")
                    .build();
            given(authService.refresh(anyString())).willReturn(tokenResponse);
            given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);

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

    // ======================== helpers ========================

    private RequestPostProcessor authenticatedUser(String username) {
        CustomUserDetails userDetails = createUserDetails(username);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null,
                userDetails.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    private CustomUserDetails createUserDetails(String username) {
        org.example.domain.entity.User user = org.example.domain.entity.User.builder()
                .username(username)
                .password("encoded")
                .nickname("테스터")
                .build();
        return new CustomUserDetails(user);
    }
}
