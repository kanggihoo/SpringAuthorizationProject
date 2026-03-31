package org.example.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenProvider — JWT 토큰 생성 및 검증")
class JwtTokenProviderTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1qdW5pdC10ZXN0aW5nLW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXM="; // 64 bytes base64
    private static final long ACCESS_EXPIRATION = 3_600_000L;
    private static final long REFRESH_EXPIRATION = 604_800_000L;

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
    }

    @Test
    @DisplayName("generateAccessToken: subject에 username, claim에 roles가 포함된 토큰을 생성한다")
    void generateAccessToken_containsUsernameAndRoles() {
        // given
        String username = "testuser";
        List<String> roles = List.of("ROLE_USER", "ROLE_ADMIN");

        // when
        String token = jwtTokenProvider.generateAccessToken(username, roles);
        Claims claims = jwtTokenProvider.parseClaims(token);

        // then
        assertThat(token).isNotBlank();
        assertThat(claims.getSubject()).isEqualTo(username);
        assertThat(claims.get("roles", List.class)).containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("generateRefreshToken: subject에 username만 포함된 토큰을 생성한다")
    void generateRefreshToken_containsOnlySubject() {
        // given
        String username = "testuser";

        // when
        String token = jwtTokenProvider.generateRefreshToken(username);
        Claims claims = jwtTokenProvider.parseClaims(token);

        // then
        assertThat(token).isNotBlank();
        assertThat(claims.getSubject()).isEqualTo(username);
        assertThat(claims.get("roles")).isNull();
    }

    @Test
    @DisplayName("validateToken: 조건에 맞는 정상 토큰은 검사를 통과하고 true를 반환한다")
    void validateToken_returnsTrueForValidToken() {
        // given
        String token = jwtTokenProvider.generateAccessToken("testuser", List.of("ROLE_USER"));

        // when
        boolean isValid = jwtTokenProvider.validateToken(token);

        // then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("validateToken: 만료된 토큰을 검증하면 ExpiredJwtException을 던진다")
    void validateToken_throwsExpiredJwtException() {
        // given
        // 만료시간을 과거로 설정한 토큰 생성
        Date now = new Date();
        Date past = new Date(now.getTime() - 1000); // 1초 전 만료
        String expiredToken = Jwts.builder()
                .subject("testuser")
                .expiration(past)
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("validateToken: 잘못된 형식의 토큰 문자열이면 MalformedJwtException을 던진다")
    void validateToken_throwsMalformedJwtException() {
        // given
        String malformedToken = "abc.def";

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(malformedToken))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    @DisplayName("validateToken: 빈 문자열 혹은 잘못된 인자가 주어지면 IllegalArgumentException을 잡아 JwtException을 던진다")
    void validateToken_throwsJwtException_forBlankToken() {
        // IllegalArgumentException은 내부에서 JwtException으로 감싸서 던지도록 되어 있음
        // given
        String blankToken = "   ";

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(blankToken))
                .isInstanceOf(io.jsonwebtoken.JwtException.class)
                .hasMessageContaining("비어있거나 잘못된 인자");
    }

    @Test
    @DisplayName("validateToken: 다른 키로 서명된 토큰은 예외를 발생시킨다")
    void validateToken_throwsJwtException_forWrongSignature() {
        // given
        String wrongSecret = "d3Jvbmctc2VjcmV0LWtleS1mb3ItanVuaXQtdGVzdGluZy1tdXN0LWJlLWF0LWxlYXN0LTMyLWJ5dGVz";
        String tokenWithWrongKey = Jwts.builder()
                .subject("testuser")
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(wrongSecret)))
                .compact();

        // when & then (SignatureException은 JwtException의 하위 타입)
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(tokenWithWrongKey))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("validateToken: 서명되지 않은 토큰(alg=none)은 UnsupportedJwtException을 던진다")
    void validateToken_throwsUnsupportedJwtException_forUnsignedToken() {
        // given
        String unsignedToken = Jwts.builder()
                .subject("testuser")
                .compact();

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(unsignedToken))
                .isInstanceOf(UnsupportedJwtException.class);
    }

    @Test
    @DisplayName("getRefreshTokenExpiration: 설정된 리프레시 토큰 만료시간(ms)을 반환한다")
    void getRefreshTokenExpiration_returnsConfiguredValue() {
        // when
        long result = jwtTokenProvider.getRefreshTokenExpiration();

        // then
        assertThat(result).isEqualTo(REFRESH_EXPIRATION);
    }
}
