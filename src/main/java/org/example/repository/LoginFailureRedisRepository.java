package org.example.repository;

import java.time.Duration;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * Redis repository for username-based login failure counters.
 */
@Repository
public class LoginFailureRedisRepository {

  private static final String ACCOUNT_FAILURE_PREFIX = "auth:login:fail:user:";
  private static final RedisScript<Long> INCREMENT_FAILURE_SCRIPT =
      createIncrementFailureScript();

  private final StringRedisTemplate redisTemplate;

  /**
   * Creates a repository backed by the given Redis template.
   *
   * @param redisTemplate Redis string template
   */
  public LoginFailureRedisRepository(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * Increments the login failure counter and starts the TTL on first failure.
   *
   * @param username JWT Subject / login username
   * @param ttl counter expiration window
   * @return incremented failure count
   */
  public long incrementFailure(String username, Duration ttl) {
    String key = accountFailureKey(username);
    Long count = redisTemplate.execute(
        INCREMENT_FAILURE_SCRIPT,
        List.of(key),
        String.valueOf(ttl.toSeconds()));
    if (count == null) {
      throw new DataAccessResourceFailureException("Redis login failure counter returned null");
    }
    return count;
  }

  /**
   * Clears the login failure counter for the username.
   *
   * @param username JWT Subject / login username
   */
  public void clearFailure(String username) {
    redisTemplate.delete(accountFailureKey(username));
  }

  /**
   * Builds the Redis key for a username failure counter.
   *
   * @param username JWT Subject / login username
   * @return Redis key
   */
  private String accountFailureKey(String username) {
    return ACCOUNT_FAILURE_PREFIX + username;
  }

  /**
   * Loads the Redis script that atomically increments and starts the TTL.
   *
   * @return Redis script returning the updated count
   */
  private static RedisScript<Long> createIncrementFailureScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("redis/increment-login-failure.lua"));
    script.setResultType(Long.class);
    return script;
  }
}
