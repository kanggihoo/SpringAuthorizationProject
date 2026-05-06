package org.example.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.repository.TokenRedisRepository;
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

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET =
        "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZw==";

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private TokenRedisRepository tokenRedisRepository;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 3_600_000L, 604_800_000L);
        jwtAuthenticationFilter = new JwtAuthenticationFilter(
            jwtTokenProvider,
            customUserDetailsService,
            tokenRedisRepository
        );
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 bearer token 이고 blacklist 에 없으면 SecurityContext 에 인증 정보를 저장한다")
    void doFilter_setsAuthentication_whenBearerTokenIsValidAndNotBlacklisted() throws Exception {
        String token = accessToken("testuser", "ROLE_USER");
        CustomUserDetails userDetails = createUserDetails("testuser", "tester", "ROLE_USER");
        MockHttpServletRequest request = requestWithBearerToken("/user/profile", token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(tokenRedisRepository.isBlacklisted(token)).thenReturn(false);
        when(customUserDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(userDetails);
        assertThat(authentication.getName()).isEqualTo("testuser");
        assertThat(authentication.getAuthorities())
            .extracting(grantedAuthority -> grantedAuthority.getAuthority())
            .containsExactly("ROLE_USER");
        verify(tokenRedisRepository).isBlacklisted(token);
        verify(customUserDetailsService).loadUserByUsername("testuser");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("유효한 bearer token 이지만 blacklist 에 있으면 인증 정보를 저장하지 않는다")
    void doFilter_skipsAuthentication_whenTokenIsBlacklisted() throws Exception {
        String token = accessToken("testuser", "ROLE_USER");
        MockHttpServletRequest request = requestWithBearerToken("/user/profile", token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(tokenRedisRepository.isBlacklisted(token)).thenReturn(true);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenRedisRepository).isBlacklisted(token);
        verifyNoInteractions(customUserDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization header 가 없으면 인증 없이 다음 필터로 넘긴다")
    void doFilter_skipsAuthentication_whenAuthorizationHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenRedisRepository, customUserDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("제외 경로는 JWT 처리를 수행하지 않는다")
    void doFilter_bypassesJwtProcessing_forExcludedPath() throws Exception {
        MockHttpServletRequest request = requestWithBearerToken("POST", "/login", accessToken("testuser", "ROLE_USER"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenRedisRepository, customUserDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("잘못된 token 이면 예외를 전파하고 다음 필터를 호출하지 않는다")
    void doFilter_throwsJwtException_whenTokenIsInvalid() throws Exception {
        MockHttpServletRequest request = requestWithBearerToken(
            "/user/profile",
            accessToken("testuser", "ROLE_USER") + "tampered"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> jwtAuthenticationFilter.doFilter(request, response, filterChain))
            .isInstanceOf(JwtException.class);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenRedisRepository, customUserDetailsService);
        verify(filterChain, never()).doFilter(request, response);
    }

    private CustomUserDetails createUserDetails(String username, String nickname, String... roles) {
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
        return jwtTokenProvider.generateAccessToken(username, List.of(roles));
    }

    private MockHttpServletRequest requestWithBearerToken(String path, String token) {
        return requestWithBearerToken("GET", path, token);
    }

    private MockHttpServletRequest requestWithBearerToken(String method, String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
