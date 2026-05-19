package org.example.repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis 기반 토큰 저장소.
 *
 * <p>Refresh Token(RT)과 Access Token Blacklist(BL)를 Redis에 저장·조회·삭제한다.
 * TTL을 Redis에 위임하여 만료된 토큰은 자동으로 제거된다.
 *
 * <p>키 패턴:
 * <ul>
 *   <li>RT:{username} — Refresh Token 저장</li>
 *   <li>BL:{accessToken} — Blacklist 등록된 Access Token</li>
 * </ul>
 */
@Repository
public class TokenRedisRepository {

    private static final String RT_PREFIX = "RT:";
    private static final String BL_PREFIX = "BL:";

    private final StringRedisTemplate redisTemplate;

    public TokenRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Refresh Token을 Redis에 저장한다.
     *
     * @param username     사용자 고유 식별자 (JWT subject)
     * @param refreshToken 저장할 Refresh Token 문자열
     * @param ttlSeconds   만료 시간 (초 단위)
     */
    public void saveRefreshToken(String username, String refreshToken, long ttlSeconds) {
        redisTemplate.opsForValue().set(RT_PREFIX + username, refreshToken, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 저장된 Refresh Token을 조회한다.
     *
     * @param username 사용자 고유 식별자
     * @return 저장된 RT가 있으면 Optional.of(token), 없으면 Optional.empty()
     */
    public Optional<String> findRefreshToken(String username) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(RT_PREFIX + username));
    }

    /**
     * 저장된 Refresh Token을 삭제한다.
     *
     * @param username 사용자 고유 식별자
     */
    public void deleteRefreshToken(String username) {
        redisTemplate.delete(RT_PREFIX + username);
    }

    /**
     * Access Token을 Blacklist에 등록한다.
     *
     * @param accessToken        블랙리스트에 등록할 AT 문자열
     * @param remainingTtlMillis AT의 남은 유효 시간 (밀리초 단위)
     */
    public void addToBlacklist(String accessToken, long remainingTtlMillis) {
        redisTemplate.opsForValue().set(BL_PREFIX + accessToken, "blacklisted", remainingTtlMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Access Token이 Blacklist에 등록되어 있는지 확인한다.
     *
     * @param accessToken 검사할 AT 문자열
     * @return Blacklist에 있으면 true, 없으면 false
     */
    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BL_PREFIX + accessToken));
    }
}
