package org.example.security.token;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.domain.entity.User;
import org.example.dto.response.TokenResponseDto;
import org.example.repository.TokenRedisRepository;
import org.example.repository.UserRepository;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.example.security.jwt.JwtTokenProvider;
import org.springframework.stereotype.Service;

/**
 * Redis-backed implementation of token lifecycle policy.
 */
@Service
@RequiredArgsConstructor
public class TokenLifecycleServiceImpl implements TokenLifecycleService {

  private final JwtTokenProvider jwtTokenProvider;
  private final TokenRedisRepository tokenRedisRepository;
  private final UserRepository userRepository;
  private final RedisFailurePolicy redisFailurePolicy;

  @Override
  public TokenResponseDto issue(String jwtSubject, List<String> roles) {
    String accessToken = jwtTokenProvider.generateAccessToken(jwtSubject, roles);
    String refreshToken = jwtTokenProvider.generateRefreshToken(jwtSubject);
    redisFailurePolicy.requireAvailable(() ->
        tokenRedisRepository.saveRefreshToken(jwtSubject, refreshToken, getRefreshTokenTtlSeconds()));

    return TokenResponseDto.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .build();
  }

  @Override
  public TokenResponseDto rotate(String refreshToken) {
    String jwtSubject = jwtTokenProvider.parseClaims(refreshToken).getSubject();
    String storedToken = redisFailurePolicy.requireAvailable(
            () -> tokenRedisRepository.findRefreshToken(jwtSubject))
        .orElseThrow(() -> new AuthFailureException(
            AuthFailureCode.REFRESH_TOKEN_INVALID,
            "유효하지 않은 Refresh Token입니다. (만료 또는 미존재)"));

    if (!storedToken.equals(refreshToken)) {
      throw new AuthFailureException(
          AuthFailureCode.REFRESH_TOKEN_REUSED,
          "Refresh Token이 일치하지 않습니다. (탈취 의심)");
    }

    User user = userRepository.findByUsername(jwtSubject)
        .orElseThrow(() -> new AuthFailureException(
            AuthFailureCode.USER_NOT_FOUND,
            "사용자를 찾을 수 없습니다."));
    List<String> roles = user.getRoles().stream()
        .map(role -> role.getName())
        .collect(Collectors.toList());

    return issue(jwtSubject, roles);
  }

  @Override
  public void logout(String jwtSubject, String accessToken) {
    redisFailurePolicy.requireAvailable(() -> tokenRedisRepository.deleteRefreshToken(jwtSubject));

    long remainingTtl = jwtTokenProvider.getRemainingExpiration(accessToken);
    redisFailurePolicy.requireAvailable(() ->
        tokenRedisRepository.addToBlacklist(accessToken, remainingTtl));
  }

  @Override
  public boolean isAccessTokenAllowed(String accessToken) {
    return redisFailurePolicy.requireAvailable(() -> !tokenRedisRepository.isBlacklisted(accessToken));
  }

  @Override
  public long getRefreshTokenTtlSeconds() {
    return jwtTokenProvider.getRefreshTokenExpiration() / 1000;
  }
}
