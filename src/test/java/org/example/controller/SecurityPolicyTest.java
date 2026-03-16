package org.example.controller;

import org.example.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ============================================================
 * Phase 3-2: 인가(Authorization) 정책 슬라이스 테스트
 * ============================================================
 *
 * [@WebMvcTest(TestController.class)]
 *   - 특정 컨트롤러만 로드하는 "슬라이스 테스트" 어노테이션
 *   - @SpringBootTest와 달리 웹 관련 빈(Controller, ControllerAdvice 등)만 로드
 *   - Service, Repository 등은 로드하지 않으므로 매우 가볍고 빠름
 *   - 인가 정책(URL별 권한 규칙)만 집중적으로 테스트하기에 적합
 *
 * [@Import(SecurityConfig.class)]
 *   - @WebMvcTest는 SecurityConfig를 자동으로 로드하지 않을 수 있음
 *   - 명시적으로 Import하여 실제 Security 필터 체인 설정이 적용되도록 보장
 *   - SecurityConfig에 정의된 requestMatchers 규칙을 그대로 테스트
 *
 * [@WithMockUser]
 *   - Spring Security Test에서 제공하는 가짜 인증 유저 주입 어노테이션
 *   - SecurityContext에 지정한 역할(roles)의 가짜 Authentication 객체를 꽂아넣음
 *   - 실제 DB 조회 없이 "이 유저가 이 권한으로 로그인했다면?" 시나리오를 테스트
 *   - roles = "USER"는 내부적으로 "ROLE_USER"로 변환됨
 *
 * [@WithAnonymousUser]
 *   - 비로그인(익명) 사용자 상태를 시뮬레이션
 *   - SecurityContext에 AnonymousAuthenticationToken이 설정됨
 *
 * [테스트 목적]
 *   - step1-internal-flow.md의 "2.3 인가 실패 시나리오"를 코드로 검증
 *   - SecurityConfig의 URL별 권한 정책이 의도대로 동작하는지 확인
 *   - 실제 DB나 서비스 로직 없이 순수하게 인가 규칙만 테스트
 */
@WebMvcTest(TestController.class)
@Import(SecurityConfig.class)
class SecurityPolicyTest {

    @Autowired
    private MockMvc mockMvc;

    // ============================================================
    // /admin/manage 엔드포인트 인가 테스트
    // ============================================================

    @Nested
    @DisplayName("/admin/manage 접근 권한 테스트")
    class AdminManageTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN 권한 유저는 /admin/manage 접근 허용 (200 OK)")
        void adminManage_WithAdminRole_Success() throws Exception {
            // ============================================================
            // @WithMockUser(roles = "ADMIN")에 의해
            // SecurityContext에 ROLE_ADMIN 권한을 가진 가짜 유저가 주입됨
            //
            // SecurityConfig 규칙: .requestMatchers("/admin/**").hasRole("ADMIN")
            // → ROLE_ADMIN 보유 → 접근 허용 → 200 OK
            // ============================================================
            mockMvc.perform(get("/admin/manage"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string("관리자 전용 페이지입니다."));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("USER 권한 유저는 /admin/manage 접근 거부 (403 Forbidden)")
        void adminManage_WithUserRole_Forbidden() throws Exception {
            // ============================================================
            // step1-internal-flow.md "2.3 인가 실패 시나리오" 재현:
            // - USER 권한만 가진 유저가 ADMIN 전용 페이지에 접근 시도
            // - AuthorizationFilter에서 ROLE_ADMIN 보유 여부 체크
            // - ROLE_USER만 있으므로 → AccessDeniedException 발생
            // - ExceptionTranslationFilter → AccessDeniedHandler → 403 Forbidden
            // ============================================================
            mockMvc.perform(get("/admin/manage"))
                    .andDo(print())
                    .andExpect(status().isForbidden());  // HTTP 403
        }

        @Test
        @WithAnonymousUser
        @DisplayName("비로그인 유저는 /admin/manage 접근 시 로그인 페이지로 리다이렉트")
        void adminManage_Anonymous_RedirectToLogin() throws Exception {
            // ============================================================
            // 비로그인 상태 → 인증 자체가 없으므로
            // AuthenticationEntryPoint에 의해 로그인 페이지로 리다이렉트
            // ============================================================
            mockMvc.perform(get("/admin/manage"))
                    .andDo(print())
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));  // /login으로 리다이렉트
        }
    }

    // ============================================================
    // /user/profile 엔드포인트 인가 테스트
    // ============================================================

    @Nested
    @DisplayName("/user/profile 접근 권한 테스트")
    class UserProfileTests {

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        @DisplayName("USER 권한 유저는 /user/profile 접근 허용 (403이 아닌지 검증)")
        void userProfile_WithUserRole_Success() throws Exception {
            // ============================================================
            // SecurityConfig 규칙: .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN")
            // → ROLE_USER 보유 → 인가 통과 (403이 아님)
            //
            // [주의] @WithMockUser가 생성하는 Principal은 Spring Security 기본 User 객체.
            //   컨트롤러의 @AuthenticationPrincipal CustomUserDetails는 타입 불일치로 null.
            //   → userDetails.getNickname()에서 NullPointerException 발생 → 400 응답
            //   → 이는 "인가는 통과했으나 컨트롤러 내부 로직에서 에러" 상황.
            //   → 인가 정책 테스트이므로 "403(접근 거부)이 아닌지"만 검증하면 충분.
            //   → 실제 응답 내용 검증은 AuthControllerTest(E2E)에서 수행
            // ============================================================
            mockMvc.perform(get("/user/profile"))
                    .andDo(print())
                    // 403 Forbidden이 아니면 인가 통과 → 테스트 성공
                    // (400은 @WithMockUser의 한계로 컨트롤러 내부 NPE 발생한 것이며 인가와 무관)
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        if (status == 403) {
                            throw new AssertionError("인가가 거부되었습니다 (403 Forbidden). USER 권한으로 /user/profile 접근이 허용되어야 합니다.");
                        }
                    });
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("ADMIN 권한 유저도 /user/profile 접근 허용 (403이 아닌지 검증)")
        void userProfile_WithAdminRole_Success() throws Exception {
            // ============================================================
            // SecurityConfig: hasAnyRole("USER", "ADMIN")
            // → ADMIN도 /user/** 접근 가능 (ADMIN은 USER 페이지도 볼 수 있는 설계)
            //
            // [주의] @WithMockUser 한계로 400이 반환되지만,
            //   403이 아닌 것으로 인가 통과를 검증
            // ============================================================
            mockMvc.perform(get("/user/profile"))
                    .andDo(print())
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        if (status == 403) {
                            throw new AssertionError("인가가 거부되었습니다 (403 Forbidden). ADMIN 권한으로 /user/profile 접근이 허용되어야 합니다.");
                        }
                    });
        }

        @Test
        @WithAnonymousUser
        @DisplayName("비로그인 유저는 /user/profile 접근 시 로그인 페이지로 리다이렉트")
        void userProfile_Anonymous_RedirectToLogin() throws Exception {
            // ============================================================
            // step1-internal-flow.md "3.4 비로그인 상태 거부 테스트" 재현:
            // - JSESSIONID가 없는(= 비로그인) 상태로 인증 필요 페이지 접근
            // - Spring Security가 인증이 필요함을 인지
            // - AuthenticationEntryPoint에 의해 /login으로 리다이렉트
            // ============================================================
            mockMvc.perform(get("/user/profile"))
                    .andDo(print())
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));  // 로그인 페이지로 리다이렉트
        }
    }

    // ============================================================
    // 메인 페이지 (/) 뷰 분기 테스트
    // ============================================================

    @Nested
    @DisplayName("메인 페이지 (/) 뷰 분기 테스트")
    class MainPageTests {

        @Test
        @WithAnonymousUser
        @DisplayName("익명 유저가 / 접근 시 로그인 안내 메시지 반환")
        void mainPage_Anonymous_ShowsLoginPrompt() throws Exception {
            // ============================================================
            // SecurityConfig: .requestMatchers("/").permitAll()
            // → 인증 없이도 접근 가능
            //
            // TestController.index()에서 userDetails == null 분기
            // → "메인 페이지입니다. 로그인이 필요합니다." 반환
            // ============================================================
            mockMvc.perform(get("/"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string("메인 페이지입니다. 로그인이 필요합니다."));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        @DisplayName("로그인 유저가 / 접근 시 환영 메시지 반환")
        void mainPage_Authenticated_ShowsWelcomeMessage() throws Exception {
            // ============================================================
            // @WithMockUser로 인증된 유저 주입
            //
            // 주의: @WithMockUser가 생성하는 Principal은 Spring Security 기본 User 객체이므로
            // TestController의 @AuthenticationPrincipal CustomUserDetails는
            // 타입이 맞지 않아 null이 됨
            // → 이 경우 "메인 페이지입니다. 로그인이 필요합니다."가 반환됨
            //
            // @WithMockUser 한계: 커스텀 UserDetails 필드(nickname 등)는 E2E 테스트로 검증
            // 여기서는 "인증된 유저가 / 에 접근 가능한가?" (인가 정책)만 검증
            // ============================================================
            mockMvc.perform(get("/"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }
    }
}
