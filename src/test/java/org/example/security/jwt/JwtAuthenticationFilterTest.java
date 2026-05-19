package org.example.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.List;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.security.CustomUserDetails;
import org.example.security.CustomUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import io.jsonwebtoken.JwtException;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    // 테스트용 JWT 서명 비밀키.
    // 이 값은 실제 운영 환경에서 사용하면 안 되고, 테스트에서만 토큰 생성/검증이 일관되게 되도록 고정한다.
    // 즉, "같은 키로 토큰을 만들고 같은 키로 검증한다"는 전제를 만들어 필터 로직만 집중해서 검증할 수 있게 한다.
    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZw==";

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        // 각 테스트가 서로 영향을 주지 않도록 매번 새 필터 인스턴스를 만들고,
        // SecurityContextHolder에 남아 있을 수 있는 이전 인증 정보를 비운다.
        // 이렇게 해야 한 테스트의 인증 결과가 다음 테스트로 새지 않는다.
        jwtTokenProvider = new JwtTokenProvider(SECRET, 3_600_000L, 604_800_000L);
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider, customUserDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        // 테스트가 끝난 뒤에도 스레드 로컬에 인증 정보가 남아 있으면 다음 테스트를 오염시킬 수 있으므로 정리한다.
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Stores authentication in SecurityContext for a valid bearer token")
    void doFilter_setsAuthentication_whenBearerTokenIsValid() throws Exception {
        // 1. 유효한 access token을 만든다.
        // 2. 해당 username으로 조회했을 때 반환될 사용자 정보를 준비한다.
        // 3. Authorization: Bearer ... 헤더가 붙은 요청을 만든다.
        // 4. 필터를 실행한 뒤 SecurityContext에 인증이 들어갔는지 확인한다.
        // 이 테스트는 "정상 요청이면 JWT 필터가 인증을 세팅한다"는 가장 중요한 성공 경로를 검증한다.
        String token = accessToken("testuser", "ROLE_USER");
        CustomUserDetails userDetails = createUserDetails("testuser", "tester", "ROLE_USER");
        MockHttpServletRequest request = requestWithBearerToken("/user/profile", token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(customUserDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        // 필터는 JWT에서 꺼낸 username으로 사용자를 조회해 principal로 세팅해야 한다.
        assertThat(authentication.getPrincipal()).isEqualTo(userDetails);
        // Authentication.getName()은 보통 인증 주체의 username을 반환한다.
        assertThat(authentication.getName()).isEqualTo("testuser");
        // 토큰에 들어 있던 ROLE_USER 권한이 그대로 반영되어야 한다.
        assertThat(authentication.getAuthorities())
                .extracting(grantedAuthority -> grantedAuthority.getAuthority())
                .containsExactly("ROLE_USER");
        // 유효한 토큰이므로 userDetailsService를 정확히 한 번 호출해야 한다.
        verify(customUserDetailsService).loadUserByUsername("testuser");
        // 인증 처리가 끝나면 필터 체인이 계속 진행되어야 한다.
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Continues the chain without authentication when Authorization header is missing")
    void doFilter_skipsAuthentication_whenAuthorizationHeaderMissing() throws Exception {
        // Authorization 헤더 자체가 없으면 JWT 인증 대상이 아니므로 아무 인증도 만들지 않는다.
        // 대신 요청은 막지 않고 다음 필터로 그대로 넘겨야 한다.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // 인증 정보가 없으므로 SecurityContext는 비어 있어야 한다.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // 헤더가 없으니 사용자 조회도 발생하면 안 된다.
        verifyNoInteractions(customUserDetailsService);
        // 요청은 계속 진행되어야 하므로 필터 체인을 호출해야 한다.
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Ignores non-bearer Authorization headers and continues the chain")
    void doFilter_skipsAuthentication_whenAuthorizationHeaderIsNotBearer() throws Exception {
        // Authorization 헤더가 있더라도 값이 "Bearer ..." 형식이 아니면 JWT 필터는 처리하지 않는다.
        // 여기서는 Basic 인증 헤더처럼 보이는 값을 넣어, JWT 필터가 무시하는지 확인한다.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Basic abc123");

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // JWT 필터가 아닌 다른 인증 방식이 들어온 것이므로 여기서는 인증을 세팅하지 않는다.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // JWT 사용자 조회가 발생하지 않아야 한다.
        verifyNoInteractions(customUserDetailsService);
        // 인증을 건너뛰되 요청은 정상적으로 이어져야 한다.
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bypasses JWT processing for excluded paths")
    void doFilter_bypassesJwtProcessing_forExcludedPath() throws Exception {
        // /login 같은 경로는 토큰 검증 대상에서 제외되어야 한다.
        // 이 테스트는 "필터 제외 경로가 실제로 우회되는지"를 확인한다.
        MockHttpServletRequest request = requestWithBearerToken("POST", "/login", accessToken("testuser", "ROLE_USER"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // shouldNotFilter로 제외된 경로이므로 SecurityContext에는 아무 인증도 남지 않아야 한다.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // 제외 경로에서는 사용자 조회 자체가 일어나면 안 된다.
        verifyNoInteractions(customUserDetailsService);
        // 필터는 요청을 막지 않고 다음 단계로 넘긴다.
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Propagates an exception and stops the chain for an invalid token")
    void doFilter_throwsJwtException_whenTokenIsInvalid() throws Exception {
        // 정상 토큰 뒤에 문자열을 덧붙여 서명을 깨뜨린다.
        // 이렇게 하면 필터가 토큰 위조를 감지하고 예외를 던지는지 확인할 수 있다.
        MockHttpServletRequest request = requestWithBearerToken("/user/profile",
                accessToken("testuser", "ROLE_USER") + "tampered");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // 잘못된 토큰은 조용히 무시하면 안 되고, 예외로 실패해야 한다.
        assertThatThrownBy(() -> jwtAuthenticationFilter.doFilter(request, response, filterChain))
                .isInstanceOf(JwtException.class);

        // 검증에 실패했으므로 인증 정보는 절대 세팅되면 안 된다.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // 토큰 검증이 실패했으니 사용자 조회도 발생하면 안 된다.
        verifyNoInteractions(customUserDetailsService);
        // 예외가 발생한 경우 필터 체인은 진행되지 않아야 한다.
        verify(filterChain, never()).doFilter(request, response);
    }

    private CustomUserDetails createUserDetails(String username, String nickname, String... roles) {
        // 실제 서비스가 반환하는 형태를 최대한 비슷하게 만들기 위해
        // 도메인 User를 생성한 뒤 Spring Security가 사용하는 CustomUserDetails로 감싼다.
        // 이렇게 하면 필터가 principal을 세팅할 때의 동작을 더 현실적으로 검증할 수 있다.
        User user = User.builder()
                .username(username)
                .password("encoded-password")
                .nickname(nickname)
                .build();
        for (String roleName : roles) {
            user.addRole(new Role(roleName));
        }
        return new CustomUserDetails(user);
    }

    private String accessToken(String username, String... roles) {
        // 테스트에서 사용할 access token을 직접 만들어 반환한다.
        // username과 role 정보가 토큰 클레임에 들어가야 필터가 이를 읽어 인증 객체를 구성할 수 있다.
        return jwtTokenProvider.generateAccessToken(username, List.of(roles));
    }

    private MockHttpServletRequest requestWithBearerToken(String path, String token) {
        // 대부분의 테스트는 GET 요청이므로, 경로와 토큰만 받는 오버로드를 둬서 중복을 줄인다.
        return requestWithBearerToken("GET", path, token);
    }

    private MockHttpServletRequest requestWithBearerToken(String method, String path, String token) {
        // MockHttpServletRequest는 실제 서블릿 요청 대신 사용할 수 있는 테스트용 요청 객체다.
        // 여기서는 메서드와 경로를 세팅한 뒤 Authorization 헤더에 Bearer 토큰을 넣어
        // JWT 필터가 처리할 수 있는 전형적인 요청 형태를 구성한다.
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
