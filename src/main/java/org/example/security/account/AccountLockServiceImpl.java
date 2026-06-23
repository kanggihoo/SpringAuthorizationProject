package org.example.security.account;

import lombok.RequiredArgsConstructor;
import org.example.domain.entity.User;
import org.example.repository.UserRepository;
import org.example.security.audit.SecurityAuditService;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 실패 임계치에 따른 User 잠금 처리.
 */
@Service
@RequiredArgsConstructor
public class AccountLockServiceImpl implements AccountLockService {

  private final UserRepository userRepository;
  private final SecurityAuditService securityAuditService;

  @Override
  @Transactional
  public void lockForLoginFailure(String username) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new AuthFailureException(
            AuthFailureCode.USER_NOT_FOUND,
            "User not found."));

    user.lock();
    securityAuditService.recordAccountLocked(username);
  }
}

