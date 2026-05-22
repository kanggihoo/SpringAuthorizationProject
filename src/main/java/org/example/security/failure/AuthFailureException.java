package org.example.security.failure;

/**
 * Exception that preserves the exact authentication failure mode.
 */
public class AuthFailureException extends RuntimeException {

  private final AuthFailureCode code;

  public AuthFailureException(AuthFailureCode code, String message) {
    super(message);
    this.code = code;
  }

  public AuthFailureException(AuthFailureCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  /**
   * Returns the stable failure code.
   */
  public AuthFailureCode getCode() {
    return code;
  }
}
