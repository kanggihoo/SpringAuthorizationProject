package org.example.security.failure;

import org.springframework.http.HttpStatus;

/**
 * Stable codes for authentication and account failure modes.
 */
public enum AuthFailureCode {

  REFRESH_TOKEN_MISSING(HttpStatus.BAD_REQUEST),
  REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
  REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED),
  TOKEN_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
  BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
  ACCOUNT_LOCKED(HttpStatus.LOCKED),
  USER_DISABLED(HttpStatus.FORBIDDEN),
  USER_ALREADY_EXISTS(HttpStatus.CONFLICT),
  USER_NOT_FOUND(HttpStatus.UNAUTHORIZED);

  private final HttpStatus httpStatus;

  AuthFailureCode(HttpStatus httpStatus) {
    this.httpStatus = httpStatus;
  }

  /**
   * Returns the HTTP status used for this failure.
   */
  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
