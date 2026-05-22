package org.example.security.failure;

/**
 * HTTP response body for authentication failures.
 */
public record AuthFailureResponse(String code, String message) {

  public static AuthFailureResponse from(AuthFailureException exception) {
    return new AuthFailureResponse(exception.getCode().name(), exception.getMessage());
  }
}
