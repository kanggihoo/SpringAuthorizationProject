package org.example.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

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
    this.key = Keys.hmacShaKeyFor(keyBytes);
    this.accessTokenExpiration = accessTokenExpiration;
    this.refreshTokenExpiration = refreshTokenExpiration;
  }

  // Access Token 생성
  public String generateAccessToken(String username, List<String> roles) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

    return Jwts.builder()
        .subject(username)
        .claim("roles", roles)
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(key)
        .compact();
  }

  // Refresh Token 생성
  public String generateRefreshToken(String username) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

    return Jwts.builder()
        .subject(username)
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(key)
        .compact();
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
      Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
      return true;
    } catch (ExpiredJwtException e) {
      log.warn("JWT 토큰이 만료되었습니다. token: {}", token);
      throw e;
    } catch (MalformedJwtException e) {
      log.warn("JWT 토큰 구조가 잘못되었습니다. token: {}", token);
      throw e;
    } catch (UnsupportedJwtException e) {
      log.warn("지원하지 않는 JWT 토큰 형식입니다. token: {}", token);
      throw e;
    } catch (IllegalArgumentException e) {
      log.warn("JWT 토큰이 비어있거나 잘못된 인자입니다. token: {}", token);
      throw e;
    } catch (JwtException e) {
      log.warn("유효하지 않은 JWT 토큰입니다. token: {}", token);
      throw e;
    }
  }

  // Refresh Token 만료 시간 반환 (응답 쿠키 설정용)
  public long getRefreshTokenExpiration() {
    return refreshTokenExpiration;
  }
}
