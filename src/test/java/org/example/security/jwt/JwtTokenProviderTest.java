package org.example.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZw==";
    private static final String OTHER_SECRET = "b3RoZXItc2VjcmV0LWtleS1mb3ItdGVzdGluZy1tdXN0LWJlLWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmc=";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 3_600_000L, 604_800_000L);
    }

    @Test
    @DisplayName("액세스 토큰 생성 후 남은 만료 시간은 양수이고 최대 만료 시간 이하이다")
    void getRemainingExpiration_returnsPositive_afterGenerate() {
        String token = jwtTokenProvider.generateAccessToken("testuser", List.of("ROLE_USER"));

        long remaining = jwtTokenProvider.getRemainingExpiration(token);

        assertThat(remaining).isPositive().isLessThanOrEqualTo(3_600_000L);
    }

    @Test
    @DisplayName("액세스 토큰에는 subject와 roles claim이 포함된다")
    void generateAccessToken_containsSubjectAndRolesClaims() {
        List<String> roles = List.of("ROLE_USER", "ROLE_ADMIN");

        String token = jwtTokenProvider.generateAccessToken("testuser", roles);
        Claims claims = jwtTokenProvider.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("testuser");
        assertThat(claims.get("roles")).asInstanceOf(list(String.class)).containsExactlyElementsOf(roles);
    }

    @Test
    @DisplayName("리프레시 토큰에는 subject만 포함되고 roles claim은 없다")
    void generateRefreshToken_containsSubjectWithoutRolesClaim() {
        String token = jwtTokenProvider.generateRefreshToken("refresh-user");
        Claims claims = jwtTokenProvider.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("refresh-user");
        assertThat(claims.get("roles")).isNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("만료된 토큰으로 남은 만료 시간을 조회하면 ExpiredJwtException이 발생한다")
    void getRemainingExpiration_throwsExpiredJwtException_forExpiredToken() {
        String expiredToken = expiredAccessToken();

        assertThatThrownBy(() -> jwtTokenProvider.getRemainingExpiration(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("유효한 토큰 검증은 true를 반환한다")
    void validateToken_returnsTrue_forValidToken() {
        String token = jwtTokenProvider.generateAccessToken("testuser", List.of("ROLE_USER"));

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("만료된 토큰 검증 시 ExpiredJwtException이 발생한다")
    void validateToken_throwsExpiredJwtException_forExpiredToken() {
        String expiredToken = expiredAccessToken();

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("변조된 토큰 검증 시 JwtException이 발생한다")
    void validateToken_throwsJwtException_forTamperedToken() {
        String token = jwtTokenProvider.generateAccessToken("testuser", List.of("ROLE_USER"));
        String tamperedToken = token + "tampered";

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(tamperedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("다른 secret으로 서명된 토큰 검증 시 JwtException이 발생한다")
    void validateToken_throwsJwtException_forTokenSignedWithDifferentSecret() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(OTHER_SECRET, 3_600_000L, 604_800_000L);
        String token = otherProvider.generateAccessToken("testuser", List.of("ROLE_USER"));

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("null 토큰 검증 시 JwtException이 발생한다")
    void validateToken_throwsJwtException_forNullToken() {
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(null))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("빈 문자열 토큰 검증 시 JwtException이 발생한다")
    void validateToken_throwsJwtException_forBlankToken() {
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(""))
                .isInstanceOf(JwtException.class);
    }

    private String expiredAccessToken() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -1_000L, 604_800_000L);
        return expiredProvider.generateAccessToken("testuser", List.of("ROLE_USER"));
    }
}
