package org.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.dto.request.LoginRequestDto;
import org.example.dto.response.TokenResponseDto;
import org.example.repository.TokenRedisRepository;
import org.example.repository.UserRepository;
import org.example.security.CustomUserDetails;
import org.example.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

/**
 * AuthServiceImpl 순수 단위 테스트.
 *
 * <p>
 * Spring Context 없이 Mockito만 사용하여 login/logout/refresh 비즈니스 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private TokenRedisRepository tokenRedisRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthServiceImpl authServiceImpl;

    // ======================== login 테스트 ========================

    @Test
    @DisplayName("정상 로그인 시 tokenRedisRepository.saveRefreshToken이 호출된다")
    void login_callsSaveRefreshToken() {
        // given
        setupLoginMocks();

        // when
        authServiceImpl.login(createLoginRequest("testuser", "password123"));

        // then: Redis에 RT 저장 호출 검증
        verify(tokenRedisRepository).saveRefreshToken(
                eq("testuser"),
                eq("refresh-token"),
                anyLong());
    }

    @Test
    @DisplayName("정상 로그인 시 AT와 RT가 포함된 TokenResponseDto를 반환한다")
    void login_returnsTokenResponse() {
        // given
        setupLoginMocks();

        // when
        TokenResponseDto result = authServiceImpl.login(createLoginRequest("testuser", "password123"));

        // then
        assertThat(result).satisfies(r -> {
            assertThat(r.getAccessToken()).isEqualTo("access-token");
            assertThat(r.getRefreshToken()).isEqualTo("refresh-token");
        });
    }

    // ======================== logout 테스트 ========================

    @Test
    @DisplayName("정상 로그아웃 시 tokenRedisRepository.deleteRefreshToken이 호출된다")
    void logout_callsDeleteRefreshToken() {
        // given
        given(jwtTokenProvider.getRemainingExpiration("access-token")).willReturn(3_600_000L);

        // when
        authServiceImpl.logout("testuser", "access-token");

        // then
        verify(tokenRedisRepository).deleteRefreshToken("testuser");
    }

    @Test
    @DisplayName("정상 로그아웃 시 tokenRedisRepository.addToBlacklist가 호출된다")
    void logout_callsAddToBlacklist() {
        // given
        given(jwtTokenProvider.getRemainingExpiration("access-token")).willReturn(3_600_000L);

        // when
        authServiceImpl.logout("testuser", "access-token");

        // then
        verify(tokenRedisRepository).addToBlacklist("access-token", 3_600_000L);
    }

    // ======================== refresh 테스트 ========================

    @Test
    @DisplayName("유효한 RT로 refresh 시 새 AT와 RT를 발급하고 Redis에 새 RT를 저장한다")
    void refresh_withValidToken_returnsNewTokens() {
        // given
        String oldRt = "old-refresh-token";
        String username = "testuser";
        Claims claims = buildClaims(username);

        Role role = new Role("ROLE_USER");
        User user = User.builder().username(username).password("encoded").nickname("테스터").build();
        user.addRole(role);

        given(jwtTokenProvider.parseClaims(oldRt)).willReturn(claims);
        given(tokenRedisRepository.findRefreshToken(username)).willReturn(Optional.of(oldRt));
        given(userRepository.findByUsername(username)).willReturn(Optional.of(user));
        given(jwtTokenProvider.generateAccessToken(anyString(), any())).willReturn("new-access-token");
        given(jwtTokenProvider.generateRefreshToken(anyString())).willReturn("new-refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);

        // when
        TokenResponseDto result = authServiceImpl.refresh(oldRt);

        // then
        assertThat(result).satisfies(r -> {
            assertThat(r.getAccessToken()).isEqualTo("new-access-token");
            assertThat(r.getRefreshToken()).isEqualTo("new-refresh-token");
        });
        verify(tokenRedisRepository).saveRefreshToken(eq(username), eq("new-refresh-token"), anyLong());
    }

    @Test
    @DisplayName("Redis에 RT가 없으면 refresh 시 IllegalArgumentException이 발생한다")
    void refresh_throwsException_whenRtNotFoundInRedis() {
        // given
        String oldRt = "not-stored-token";
        Claims claims = buildClaims("testuser");

        given(jwtTokenProvider.parseClaims(oldRt)).willReturn(claims);
        given(tokenRedisRepository.findRefreshToken("testuser")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authServiceImpl.refresh(oldRt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Redis의 RT와 전달된 RT가 불일치하면 IllegalArgumentException이 발생한다 (RTR 방어)")
    void refresh_throwsException_whenRtMismatch() {
        // given
        String oldRt = "old-token";
        String storedRt = "different-stored-token";
        Claims claims = buildClaims("testuser");

        given(jwtTokenProvider.parseClaims(oldRt)).willReturn(claims);
        given(tokenRedisRepository.findRefreshToken("testuser")).willReturn(Optional.of(storedRt));

        // when & then
        assertThatThrownBy(() -> authServiceImpl.refresh(oldRt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ======================== 헬퍼 메서드 ========================

    private LoginRequestDto createLoginRequest(String username, String password) {
        return LoginRequestDto.builder()
                .username(username)
                .password(password)
                .build();
    }

    /** login 테스트에서 공통으로 사용되는 stub 설정 */
    private void setupLoginMocks() {
        User user = User.builder().username("testuser").password("encoded").nickname("테스터").build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        given(authenticationManager.authenticate(any())).willReturn(auth);
        given(jwtTokenProvider.generateAccessToken(anyString(), any())).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(anyString())).willReturn("refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(604_800_000L);
    }

    /** 실제 JJWT Claims 빌더로 생성 — 외부 라이브러리 내부 객체 mocking 회피 */
    private Claims buildClaims(String username) {
        return Jwts.claims().subject(username).build();
    }
}
