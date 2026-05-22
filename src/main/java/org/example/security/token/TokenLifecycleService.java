package org.example.security.token;

import java.util.List;
import org.example.dto.response.TokenResponseDto;

/**
 * Coordinates Access Token and Refresh Token lifecycle policy.
 */
public interface TokenLifecycleService {

  /**
   * Issues a new token pair and stores the active Refresh Token for the JWT Subject.
   */
  TokenResponseDto issue(String jwtSubject, List<String> roles);

  /**
   * Rotates the active Refresh Token and returns a new token pair.
   */
  TokenResponseDto rotate(String refreshToken);

  /**
   * Removes the active Refresh Token and blacklists the current Access Token.
   */
  void logout(String jwtSubject, String accessToken);

  /**
   * Returns whether the Access Token may authenticate a Protected API request.
   */
  boolean isAccessTokenAllowed(String accessToken);

  /**
   * Returns the Refresh Token lifetime in seconds.
   */
  long getRefreshTokenTtlSeconds();
}
