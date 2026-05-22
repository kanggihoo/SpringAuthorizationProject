package org.example.security.authenticated;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.example.security.CustomUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Enforces User state before a Protected API request is authenticated.
 */
@Service
@RequiredArgsConstructor
public class AuthenticatedUserServiceImpl implements AuthenticatedUserService {

  private final CustomUserDetailsService customUserDetailsService;

  @Override
  public Optional<AuthenticatedUser> findActiveUserByJwtSubject(String jwtSubject) {
    UserDetails userDetails = customUserDetailsService.loadUserByUsername(jwtSubject);
    if (!(userDetails instanceof AuthenticatedUser authenticatedUser)) {
      return Optional.empty();
    }
    if (!authenticatedUser.isEnabled() || !authenticatedUser.isAccountNonLocked()) {
      return Optional.empty();
    }
    return Optional.of(authenticatedUser);
  }
}
