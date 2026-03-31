package org.example.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.example.TestFixtures;
import org.example.security.CustomUserDetails;
import org.example.security.CustomUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter — 요청별 JWT 인증 처리 필터")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private Claims claims;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext(); // 컨텍스트 초기화
    }

    @Test
    @DisplayName("doFilterInternal: 유효한 Bearer 토큰이면 SecurityContextHolder에 인증을 세팅한다")
    void doFilterInternal_setsAuthentication_forValidBearerToken() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
        given(jwtTokenProvider.parseClaims("valid-token")).willReturn(claims);
        given(claims.getSubject()).willReturn("testuser");

        CustomUserDetails userDetails = TestFixtures.buildUserDetails(1L, "testuser", "ROLE_USER");
        given(customUserDetailsService.loadUserByUsername("testuser")).willReturn(userDetails);

        // when
        ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "doFilterInternal", request, response, filterChain);

        // then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(userDetails);
        assertThat(auth.getAuthorities()).hasSize(1);
    }

    @Test
    @DisplayName("doFilterInternal: Authorization 헤더가 없으면 인증 세팅 없이 넘어간다")
    void doFilterInternal_doesNotSetAuthentication_whenNoHeader() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        // when
        ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "doFilterInternal", request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("doFilterInternal: 토큰이 유효하지 않으면 예외가 발생한다")
    void doFilterInternal_propagatesException_whenTokenInvalid() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = new MockFilterChain();

        given(jwtTokenProvider.validateToken("invalid-token")).willThrow(new MalformedJwtException("Malformed"));

        // when & then
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "doFilterInternal", request, response, filterChain))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    @DisplayName("shouldNotFilter: /login 경로는 필터링 하지 않는다 (true 반환)")
    void shouldNotFilter_returnsTrue_forLoginPath() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/login");

        // when
        boolean result = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "shouldNotFilter", request);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("shouldNotFilter: /signup 경로는 필터링 하지 않는다 (true 반환)")
    void shouldNotFilter_returnsTrue_forSignupPath() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/signup");

        // when
        boolean result = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "shouldNotFilter", request);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("shouldNotFilter: /refresh 경로는 필터링 하지 않는다 (true 반환)")
    void shouldNotFilter_returnsTrue_forRefreshPath() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/refresh");

        // when
        boolean result = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "shouldNotFilter", request);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("shouldNotFilter: /swagger-ui 경로는 필터링 하지 않는다 (true 반환)")
    void shouldNotFilter_returnsTrue_forSwaggerPath() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/swagger-ui/index.html");

        // when
        boolean result = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "shouldNotFilter", request);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("shouldNotFilter: /user/profile 등 일반 API 경로는 필터링 한다 (false 반환)")
    void shouldNotFilter_returnsFalse_forApiPath() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/user/profile");

        // when
        boolean result = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "shouldNotFilter", request);

        // then
        assertThat(result).isFalse();
    }
}
