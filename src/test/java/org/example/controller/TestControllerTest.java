package org.example.controller;

import org.example.TestFixtures;
import org.example.config.SecurityConfig;
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
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(TestController.class)
@Import(SecurityConfig.class)
@DisplayName("TestController — WebMvc DB 슬라이스 테스트")
class TestControllerTest {

    @Autowired
    private MockMvcTester mvc;

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
    @WithAnonymousUser
    @DisplayName("index: 인증되지 않은 사용자가 루트 경로 접근 시 메인 페이지 메시지(200 OK) 반환")
    void index_returns200_forAnonymousUser() {
        assertThat(mvc.get().uri("/"))
                .hasStatusOk()
                .bodyText().contains("메인 페이지입니다. 로그인이 필요합니다.");
    }

    @Test
    @WithUserDetails("alice")
    @DisplayName("index: 인증된 사용자가 루트 경로 접근 시 닉네임을 포함한 메시지 반환")
    void index_returns200_withNickname_forAuthenticatedUser() {
        // given
        CustomUserDetails mockDetails = TestFixtures.buildUserDetails(1L, "alice", "ROLE_USER");
        given(customUserDetailsService.loadUserByUsername("alice")).willReturn(mockDetails);

        // then
        assertThat(mvc.get().uri("/"))
                .hasStatusOk()
                .bodyText().contains("안녕하세요, TestNick님!");
    }

    @Test
    @WithAnonymousUser
    @DisplayName("userProfile: 인증되지 않은 사용자가 접근 시 401 Unauthorized 반환 (비권한)")
    void userProfile_returns401_forAnonymousUser() {
        assertThat(mvc.get().uri("/user/profile"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithUserDetails("alice_user")
    @DisplayName("userProfile: USER 권한 소유자가 접근 시 200 OK 및 프로필 메시지 반환")
    void userProfile_returns200_forUserRole() {
        // given
        CustomUserDetails mockDetails = TestFixtures.buildUserDetails(2L, "alice_user", "ROLE_USER");
        given(customUserDetailsService.loadUserByUsername("alice_user")).willReturn(mockDetails);

        assertThat(mvc.get().uri("/user/profile"))
                .hasStatusOk()
                .bodyText().contains("회원 프로필 페이지입니다.");
    }

    @Test
    @WithUserDetails("alice_admin")
    @DisplayName("userProfile: ADMIN 권한 소유자(hasAnyRole)가 접근 시 200 OK 반환")
    void userProfile_returns200_forAdminRole() {
        // given
        CustomUserDetails mockDetails = TestFixtures.buildUserDetails(3L, "alice_admin", "ROLE_ADMIN");
        given(customUserDetailsService.loadUserByUsername("alice_admin")).willReturn(mockDetails);

        assertThat(mvc.get().uri("/user/profile"))
                .hasStatusOk()
                .bodyText().contains("회원 프로필 페이지입니다.");
    }

    @Test
    @WithAnonymousUser
    @DisplayName("adminManage: 인증되지 않은 사용자가 접근 시 401 Unauthorized 반환")
    void adminManage_returns401_forAnonymousUser() {
        assertThat(mvc.get().uri("/admin/manage"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("adminManage: USER 권한 사용자가 관리자 페이지 접근 시 403 Forbidden 반환")
    void adminManage_returns403_forUserRole() {
        // Using WithMockUser since we don't need UserDetails injection for 403 check
        assertThat(mvc.get().uri("/admin/manage"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("adminManage: ADMIN 권한 사용자가 관리자 페이지 접근 시 200 OK 반환")
    void adminManage_returns200_forAdminRole() {
        assertThat(mvc.get().uri("/admin/manage"))
                .hasStatusOk()
                .bodyText().contains("관리자 전용 페이지입니다.");
    }
}
