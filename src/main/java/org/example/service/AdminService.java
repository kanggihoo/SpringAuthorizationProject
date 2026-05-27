package org.example.service;

/**
 * Admin recovery use cases.
 */
public interface AdminService {

  /**
   * Unlocks a User account by username.
   *
   * @param username User login username
   */
  void unlockUser(String username);
}
