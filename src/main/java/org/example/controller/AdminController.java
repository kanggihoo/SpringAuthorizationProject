package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin HTTP adapter for account recovery operations.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

  private final AdminService adminService;

  /**
   * Unlocks a locked User account.
   *
   * @param username User login username
   * @return unlock success response
   */
  @PostMapping("/users/{username}/unlock")
  public ResponseEntity<String> unlockUser(@PathVariable String username) {
    adminService.unlockUser(username);
    return ResponseEntity.ok("User unlocked successfully.");
  }
}
