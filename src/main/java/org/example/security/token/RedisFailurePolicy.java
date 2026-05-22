package org.example.security.token;

import java.util.function.Supplier;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Converts Redis availability failures into Token Store policy failures.
 */
@Component
public class RedisFailurePolicy {

  /**
   * Requires Redis-backed Token Store state for a security-critical operation.
   */
  public void requireAvailable(Runnable operation) {
    try {
      operation.run();
    } catch (DataAccessException e) {
      throw tokenStoreUnavailable(e);
    }
  }

  /**
   * Requires Redis-backed Token Store state for a security-critical lookup.
   */
  public <T> T requireAvailable(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (DataAccessException e) {
      throw tokenStoreUnavailable(e);
    }
  }

  private AuthFailureException tokenStoreUnavailable(DataAccessException cause) {
    return new AuthFailureException(
        AuthFailureCode.TOKEN_STORE_UNAVAILABLE,
        "Token Store를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.",
        cause);
  }
}
