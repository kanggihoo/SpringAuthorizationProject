package org.example.security.jwt;

import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtTokenProvider {

  private final SecretKey key;
  private final long accessTokenExpiration;
  private final long refreshTokenExpiration;

  public JwtTokenProvider(
      @Value("${jwt.secret}") String secretKey,
      @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
      @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
    byte[] keyBytes = Decoders.BASE64.decode(secretKey);
    this.key = Keys.hmacShaKeyFor(keyBytes); // 바이트 배열을 HS256 알고리즘에 사용할 비밀키 생성
    this.accessTokenExpiration = accessTokenExpiration;
    this.refreshTokenExpiration = refreshTokenExpiration;
  }

  // Access Token 생성
  public String generateAccessToken(String username, List<String> roles) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

    return Jwts.builder()
        .subject(username) // 토큰의 주체
        .claim("roles", roles) // 토큰에 담을 정보
        .issuedAt(now) // 토큰 발급 시간
        .expiration(expiryDate) // 토큰 만료 시간
        .signWith(key) // 서명
        .compact(); // 토큰 생성
  }

  // Refresh Token 생성
  public String generateRefreshToken(String username) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

    return Jwts.builder()
        .subject(username) // 토큰의 주체
        .issuedAt(now) // 토큰 발급 시간
        .expiration(expiryDate) // 토큰 만료 시간
        .signWith(key) // 서명
        .compact(); // 토큰 생성
  }

  // JWT 파싱 및 Claims 추출
  public Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  // 토큰 서명 유효성 검증
  public boolean validateToken(String token) {
    try {
      Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token);
      return true;
    } catch (ExpiredJwtException e) { // JwtException 자식
      log.warn("JWT 토큰이 만료되었습니다. token: {}", token);
      throw e;
    } catch (MalformedJwtException e) { // JwtException 자식
      log.warn("JWT 토큰 구조가 잘못되었습니다. token: {}", token);
      throw e;
    } catch (UnsupportedJwtException e) { // JwtException 자식
      log.warn("지원하지 않는 JWT 토큰 형식입니다. token: {}", token);
      throw e;
    } catch (IllegalArgumentException e) { // RuntimeException 자식
      log.warn("JWT 토큰이 비어있거나 잘못된 인자입니다. token: {}", token);
      throw new JwtException("JWT 토큰이 비어있거나 잘못된 인자입니다.", e);
    } catch (JwtException e) { // RuntimeException 자식
      log.warn("유효하지 않은 JWT 토큰입니다. token: {}", token);
      throw e;
    }
  }

  // Refresh Token 만료 시간 반환 (응답 쿠키 설정용)
  public long getRefreshTokenExpiration() {
    return refreshTokenExpiration;
  }

  /**
   * 토큰의 남은 유효 시간을 밀리초 단위로 반환한다.
   *
   * <p>로그아웃 시 AT를 Blacklist에 등록할 때 TTL로 사용된다.
   * 만료된 토큰을 전달하면 {@link io.jsonwebtoken.ExpiredJwtException}이 발생한다.
   *
   * @param token 검사할 JWT 문자열
   * @return 현재 시각 기준 남은 만료 시간 (밀리초)
   */
  public long getRemainingExpiration(String token) {
    Date expiration = parseClaims(token).getExpiration();
    return expiration.getTime() - System.currentTimeMillis();
  }
}


