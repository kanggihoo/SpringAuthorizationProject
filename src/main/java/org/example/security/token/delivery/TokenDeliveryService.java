package org.example.security.token.delivery;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;

/**
 * Owns browser-facing token transport policy.
 */
public interface TokenDeliveryService {

  /**
   * Adds the Refresh Token delivery cookie to the response.
   */
  void addRefreshTokenCookie(HttpServletResponse response, String refreshToken);

  /**
   * Expires the Refresh Token delivery cookie.
   */
  void expireRefreshTokenCookie(HttpServletResponse response);

  /**
   * Reads the Refresh Token from the delivery cookie.
   */
  Optional<String> readRefreshToken(HttpServletRequest request);

  /**
   * Resolves the Access Token from a Bearer Authorization header.
   */
  Optional<String> resolveBearerAccessToken(HttpServletRequest request);
}
