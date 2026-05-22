package org.example.security.authenticated;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

/**
 * Service-facing representation of a User after authentication.
 */
public interface AuthenticatedUser {

  /**
   * Returns the service User id.
   */
  Long getId();

  /**
   * Returns the JWT Subject that owns service tokens.
   */
  String getJwtSubject();

  /**
   * Returns the display nickname.
   */
  String getNickname();

  /**
   * Returns whether the User is enabled.
   */
  boolean isEnabled();

  /**
   * Returns whether the User is not under Account Lock.
   */
  boolean isAccountNonLocked();

  /**
   * Returns service Roles as Spring Security authorities.
   */
  Collection<? extends GrantedAuthority> getAuthorities();
}
