package org.example.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.example.dto.response.TokenResponseDto;
import org.example.security.CustomUserDetails;
import org.example.security.CustomUserDetailsService;
import org.example.security.jwt.JwtTokenProvider;
import org.example.service.AuthService;
import org.example.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AuthController 슬라이스 테스트.
 *
 * <p>
 * 
 * @WebMvcTest로 HTTP 레이어만 로드하여 엔드포인트 동작을 검증한다.
 *              Spring Security 필터가 함께 로드되므로
 *              SecurityMockMvcRequestPostProcessors를 사용한다.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("logout: 인증된 사용자가 Authorization 헤더와 함께 요청하면 authService.logout이 호출된다")
    void logout_callsAuthServiceLogout_whenAuthenticated() throws Exception {
        // given: SecurityContextHolder에 직접 인증 정보 주입 (addFilters=false 환경)
        CustomUserDetails userDetails = createUserDetails("testuser");
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);

        // when & then
        mockMvc.perform(post("/logout")
                .header("Authorization", "Bearer test-access-token"))
                .andExpect(status().isOk());

        verify(authService).logout(eq("testuser"), eq("test-access-token"));
    }

    @Test
    @DisplayName("logout: 응답에 Refresh-Token 쿠키가 maxAge=0으로 삭제된다")
    void logout_deletesCookie() throws Exception {
        // given
        CustomUserDetails userDetails = createUserDetails("testuser");
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);

        // when & then
        mockMvc.perform(post("/logout")
                .header("Authorization", "Bearer test-access-token"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("Refresh-Token", 0));
    }

    @Test
    @DisplayName("login: 정상 요청 시 200과 TokenResponseDto를 반환한다")
    void login_returns200WithTokenResponse() throws Exception {
        // given
        TokenResponseDto tokenResponse = TokenResponseDto.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .build();
        given(authService.login(any())).willReturn(tokenResponse);
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);

        Map<String, String> body = Map.of("username", "testuser", "password", "password123");

        // when & then
        mockMvc.perform(post("/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("login: username이 빈 값이면 422를 반환한다")
    void login_returns422_whenUsernameIsBlank() throws Exception {
        // given
        Map<String, String> body = Map.of("username", "", "password", "password123");

        // when & then
        mockMvc.perform(post("/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("refresh: Refresh-Token 쿠키가 있으면 200과 새 토큰을 반환한다")
    void refresh_returns200_whenCookieExists() throws Exception {
        // given
        TokenResponseDto tokenResponse = TokenResponseDto.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .tokenType("Bearer")
                .build();
        given(authService.refresh(anyString())).willReturn(tokenResponse);
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);

        // when & then
        mockMvc.perform(post("/refresh")
                .with(csrf())
                .cookie(new Cookie("Refresh-Token", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    @DisplayName("refresh: Refresh-Token 쿠키가 없으면 400을 반환한다")
    void refresh_returns400_whenNoCookie() throws Exception {
        // given: 쿠키 없는 요청 → IllegalArgumentException 발생
        given(authService.refresh(anyString()))
                .willThrow(new IllegalArgumentException("Refresh Token이 존재하지 않습니다."));

        // when & then
        mockMvc.perform(post("/refresh")
                .with(csrf()))
                .andExpect(status().isBadRequest());
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
