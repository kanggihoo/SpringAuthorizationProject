package org.example.controller;

import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.repository.RoleRepository;
import org.example.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ============================================================
 * Phase 3-1: 인증 E2E 통합 테스트 (AuthController)
 * ============================================================
 *
 * [@SpringBootTest]
 *   - 전체 Spring 애플리케이션 컨텍스트를 로드하는 "통합 테스트" 어노테이션
 *   - 실제 서비스, 리포지토리, Security 필터 체인 등 모든 빈이 로드됨
 *   - DB 연동이 필요한 "실제 인증 흐름"을 테스트할 때 사용
 *
 * [@AutoConfigureMockMvc]
 *   - MockMvc를 자동 설정해주는 어노테이션
 *   - MockMvc란? 실제 서버를 띄우지 않고도 HTTP 요청/응답을 시뮬레이션하는 도구
 *   - 브라우저나 Postman 없이도 컨트롤러 + Security 필터 체인을 통합 테스트 가능
 *
 * [@Transactional]
 *   - 각 테스트 메서드 실행 후 DB 변경사항을 자동 롤백
 *   - 테스트 간 데이터 오염을 방지 (테스트 격리)
 *
 * [테스트 목적]
 *   - step1-internal-flow.md에 기술된 인증 성공/실패 시나리오를 코드로 검증
 *   - 실제 DB(H2) 조회를 동반하는 Security 전체 필터 체인 E2E 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    /**
     * MockMvc: HTTP 요청을 시뮬레이션하는 핵심 도구
     * - perform(): 요청 실행
     * - andExpect(): 응답 검증
     * - andDo(print()): 요청/응답 상세 정보를 콘솔에 출력 (디버깅 용도)
     */
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ============================================================
    // 회원가입 테스트 그룹
    // ============================================================

    /**
     * [@Nested]
     *   - 테스트 클래스 안에 내부 클래스를 만들어 관련 테스트를 그룹화
     *   - 테스트 결과 리포트에서 계층적으로 표시되어 가독성 향상
     */
    @Nested
    @DisplayName("회원가입 API 테스트")
    class SignupTests {

        @Test
        @DisplayName("정상적인 회원가입 요청 시 200 OK 반환")
        void signup_Success() throws Exception {
            // ============================================================
            // [Given] 회원가입 요청 JSON 데이터를 문자열로 직접 작성
            // - ObjectMapper 없이 JSON 문자열 리터럴을 사용
            // ============================================================
            String requestJson = """
                    {
                        "username": "testuser",
                        "password": "password123",
                        "nickname": "Tester"
                    }
                    """;

            // ============================================================
            // [When & Then] MockMvc로 POST /signup 요청을 시뮬레이션
            // ============================================================
            mockMvc.perform(
                            post("/signup")                                 // POST 메서드로 /signup 호출
                                    .contentType(MediaType.APPLICATION_JSON) // Content-Type: application/json 헤더 설정
                                    .content(requestJson)                   // 요청 Body에 JSON 데이터 삽입
                    )
                    .andDo(print())                                         // 요청/응답 상세 정보 콘솔 출력 (디버깅)
                    .andExpect(status().isOk())                             // HTTP 200 OK 검증
                    .andExpect(content().string("회원가입이 완료되었습니다.")); // 응답 본문 검증

            // ★ 추가 검증: 실제 DB에 유저가 저장되었는지 확인
            assertThat(userRepository.findByUsername("testuser")).isPresent();
        }

        @Test
        @DisplayName("Validation 위반: username이 4자 미만이면 400 Bad Request")
        void signup_InvalidUsername_ReturnsBadRequest() throws Exception {
            // ============================================================
            // [Given] username이 2자 → @Size(min=4) 위반
            // ============================================================
            String requestJson = """
                    {
                        "username": "ab",
                        "password": "password123",
                        "nickname": "Tester"
                    }
                    """;

            // ============================================================
            // [When & Then]
            // - @Valid 어노테이션에 의해 유효성 검사가 자동 수행됨
            // - 위반 시 MethodArgumentNotValidException 발생
            // - GlobalExceptionHandler가 이를 잡아 400 응답 + 에러 메시지 맵 반환
            // ============================================================
            mockMvc.perform(
                            post("/signup")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestJson)
                    )
                    .andDo(print())
                    .andExpect(status().isBadRequest())  // HTTP 400 검증
                    .andExpect(jsonPath("$.username").exists()); // 에러 응답에 "username" 필드가 존재하는지
        }

        @Test
        @DisplayName("Validation 위반: password가 8자 미만이면 400 Bad Request")
        void signup_ShortPassword_ReturnsBadRequest() throws Exception {
            // ============================================================
            // [Given] password가 5자 → @Size(min=8) 위반
            // ============================================================
            String requestJson = """
                    {
                        "username": "testuser",
                        "password": "short",
                        "nickname": "Tester"
                    }
                    """;

            // ============================================================
            // [When & Then]
            // ============================================================
            mockMvc.perform(
                            post("/signup")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestJson)
                    )
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.password").exists());
        }
    }

    // ============================================================
    // 폼 로그인 테스트 그룹
    // ============================================================

    @Nested
    @DisplayName("폼 로그인 테스트")
    class FormLoginTests {

        /**
         * [@BeforeEach]
         *   - 각 테스트 메서드 실행 전에 자동으로 호출되는 셋업 메서드
         *   - 로그인 테스트를 위해 DB에 미리 유저를 등록해두는 역할
         *   - @Transactional에 의해 테스트 후 자동 롤백되므로 데이터 오염 없음
         */
        @BeforeEach
        void setUp() {
            // 테스트용 유저를 DB에 미리 저장
            Role role = roleRepository.save(new Role("ROLE_USER"));
            User user = User.builder()
                    .username("testuser")
                    .password(passwordEncoder.encode("password123"))  // BCrypt 해싱
                    .nickname("Tester")
                    .build();
            user.addRole(role);
            userRepository.save(user);
        }

        @Test
        @DisplayName("올바른 자격 증명으로 폼 로그인 시 리다이렉트 (인증 성공)")
        void formLogin_Success() throws Exception {
            // ============================================================
            // [When & Then]
            // formLogin(): Spring Security Test가 제공하는 폼 로그인 시뮬레이터
            //   - 내부적으로 POST /login 요청을 x-www-form-urlencoded 형식으로 전송
            //   - step1-internal-flow.md "2.1 인증 성공 시나리오"의 전체 흐름을 재현:
            //     UsernamePasswordAuthenticationFilter → ProviderManager
            //     → DaoAuthenticationProvider → CustomUserDetailsService
            //     → 비밀번호 검증 → SecurityContext 저장 → SuccessHandler
            //
            // 성공 시 SecurityConfig의 defaultSuccessUrl("/", true)에 의해
            // HTTP 302 리다이렉트가 발생함
            // ============================================================
            mockMvc.perform(
                            formLogin()
                                    .user("testuser")       // username 파라미터
                                    .password("password123") // password 파라미터
                    )
                    .andDo(print())
                    .andExpect(status().is3xxRedirection())           // 302 리다이렉트 검증
                    .andExpect(redirectedUrl("/"));                   // 리다이렉트 대상 URL 검증
        }

        @Test
        @DisplayName("잘못된 비밀번호로 폼 로그인 시 인증 실패")
        void formLogin_WrongPassword_Failure() throws Exception {
            // ============================================================
            // [When & Then]
            // - step1-internal-flow.md "2.2 인증 실패 시나리오" 중
            //   "비밀번호 검증이 실패한 경우" 에 해당
            // - DaoAuthenticationProvider에서 BCrypt 해시 불일치 판단
            //   → BadCredentialsException 발생
            //   → AuthenticationFailureHandler가 /login?error로 리다이렉트
            // ============================================================
            mockMvc.perform(
                            formLogin()
                                    .user("testuser")
                                    .password("wrongPassword")  // 틀린 비밀번호
                    )
                    .andDo(print())
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));   // 실패 시 /login?error로 리다이렉트
        }

        @Test
        @DisplayName("존재하지 않는 사용자로 폼 로그인 시 인증 실패")
        void formLogin_UserNotFound_Failure() throws Exception {
            // ============================================================
            // [When & Then]
            // - step1-internal-flow.md "2.2 인증 실패 시나리오" 중
            //   "DB에 찾는 회원이 없는 경우" 에 해당
            // - CustomUserDetailsService에서 UsernameNotFoundException 발생
            //   → AuthenticationFailureHandler가 /login?error로 리다이렉트
            // ============================================================
            mockMvc.perform(
                            formLogin()
                                    .user("nonexistent")
                                    .password("password123")
                    )
                    .andDo(print())
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));
        }
    }
}
