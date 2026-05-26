package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.entity.User;
import org.example.repository.UserRepository;
import org.example.security.account.LoginFailureCounter;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional service for ADMIN account recovery.
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

  private final UserRepository userRepository;
  private final LoginFailureCounter loginFailureCounter;

  @Override
  @Transactional
  public void unlockUser(String username) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new AuthFailureException(
            AuthFailureCode.USER_NOT_FOUND,
            "User not found."));

    user.unlock();
    loginFailureCounter.clear(username);
  }
}
