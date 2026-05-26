package org.example.security.account;

import java.time.Duration;
import org.example.repository.LoginFailureRedisRepository;
import org.example.security.token.RedisFailurePolicy;
import org.springframework.stereotype.Component;

/**
 * Applies login failure threshold policy for Account Lock decisions.
 */
@Component
public class LoginFailureCounter {

  private static final int LOCK_THRESHOLD = 5;
  private static final Duration LOCK_WINDOW = Duration.ofMinutes(30);

  private final LoginFailureRedisRepository repository;
  private final RedisFailurePolicy redisFailurePolicy;

  /**
   * Creates a login failure counter backed by Redis.
   *
   * @param repository login failure Redis repository
   * @param redisFailurePolicy Redis availability policy
   */
  public LoginFailureCounter(
      LoginFailureRedisRepository repository,
      RedisFailurePolicy redisFailurePolicy) {
    this.repository = repository;
    this.redisFailurePolicy = redisFailurePolicy;
  }

  /**
   * Records a login failure and returns whether the User should be locked.
   *
   * @param username JWT Subject / login username
   * @return true when the lock threshold has been reached
   */
  public boolean recordFailure(String username) {
    return redisFailurePolicy.requireAvailable(
        () -> repository.incrementFailure(username, LOCK_WINDOW)) >= LOCK_THRESHOLD;
  }

  /**
   * Clears login failures for the username.
   *
   * @param username JWT Subject / login username
   */
  public void clear(String username) {
    redisFailurePolicy.requireAvailable(() -> repository.clearFailure(username));
  }
}
