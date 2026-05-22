package org.example.security.authenticated;

import java.util.Optional;

/**
 * Loads Authenticated Users that may access Protected APIs.
 */
public interface AuthenticatedUserService {

  /**
   * Finds an enabled and unlocked Authenticated User by JWT Subject.
   */
  Optional<AuthenticatedUser> findActiveUserByJwtSubject(String jwtSubject);
}
