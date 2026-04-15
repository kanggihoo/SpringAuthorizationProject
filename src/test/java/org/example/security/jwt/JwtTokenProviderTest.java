package org.example.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JwtTokenProvider 순수 단위 테스트.
 *
 * <p>Spring Context 없이 직접 생성하여 JWT 생성·검증·만료 시간 추출 로직을 검증한다.
 */
class JwtTokenProviderTest {

    // 테스트용 시크릿 키 (Base64, 32바이트 이상)
    private static final String SECRET =
        "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZw==";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        // AT: 1시간, RT: 7일
        jwtTokenProvider = new JwtTokenProvider(SECRET, 3_600_000L, 604_800_000L);
    }

    @Test
    @DisplayName("generateAccessToken 후 getRemainingExpiration은 양수이고 AT 만료 시간 이하이다")
    void getRemainingExpiration_returnsPositive_afterGenerate() {
        // given
        String token = jwtTokenProvider.generateAccessToken("testuser", List.of("ROLE_USER"));

        // when
        long remaining = jwtTokenProvider.getRemainingExpiration(token);

        // then: 남은 시간이 양수이고 AT 만료 시간(1시간) 이하
        assertThat(remaining).isPositive().isLessThanOrEqualTo(3_600_000L);
    }

    @Test
    @DisplayName("만료된 토큰에 대해 getRemainingExpiration은 ExpiredJwtException을 던진다")
    void getRemainingExpiration_throwsExpiredJwtException_forExpiredToken() {
        // given: AT 만료 시간을 1ms로 설정하여 즉시 만료
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, 1L, 604_800_000L);
        String expiredToken = shortLivedProvider.generateAccessToken("testuser", List.of("ROLE_USER"));

        // 토큰이 만료될 때까지 대기
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.getRemainingExpiration(expiredToken))
            .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("validateToken은 유효한 토큰에 대해 true를 반환한다")
    void validateToken_returnsTrue_forValidToken() {
        // given
        String token = jwtTokenProvider.generateAccessToken("testuser", List.of("ROLE_USER"));

        // when & then
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken은 만료된 토큰에 대해 ExpiredJwtException을 던진다")
    void validateToken_throwsExpiredJwtException_forExpiredToken() {
        // given
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, 1L, 604_800_000L);
        String expiredToken = shortLivedProvider.generateAccessToken("testuser", List.of("ROLE_USER"));

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(expiredToken))
            .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("validateToken은 변조된 토큰에 대해 JwtException을 던진다")
    void validateToken_throwsJwtException_forTamperedToken() {
        // given
        String token = jwtTokenProvider.generateAccessToken("testuser", List.of("ROLE_USER"));
        String tamperedToken = token + "tampered";

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(tamperedToken))
            .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
