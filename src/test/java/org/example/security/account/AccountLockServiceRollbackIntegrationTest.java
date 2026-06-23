package org.example.security.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import org.example.domain.entity.User;
import org.example.repository.UserRepository;
import org.example.security.audit.SecurityAuditService;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class AccountLockServiceRollbackIntegrationTest {

  @Autowired
  private AccountLockService accountLockService;

  @Autowired
  private UserRepository userRepository;

  @MockitoBean
  private SecurityAuditService securityAuditService;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("lockForLoginFailure rolls back User lock when ACCOUNT_LOCKED audit fails")
  void lockForLoginFailure_rollsBackUserLock_whenAuditFails() {
    userRepository.save(user("testuser"));
    doThrow(new AuthFailureException(
        AuthFailureCode.AUDIT_STORE_UNAVAILABLE,
        "Authentication service is temporarily unavailable."))
        .when(securityAuditService)
        .recordAccountLocked("testuser");

    assertThatThrownBy(() -> accountLockService.lockForLoginFailure("testuser"))
        .isInstanceOfSatisfying(AuthFailureException.class, failure ->
            assertThat(failure.getCode()).isEqualTo(AuthFailureCode.AUDIT_STORE_UNAVAILABLE));

    User reloaded = userRepository.findByUsername("testuser").orElseThrow();
    assertThat(reloaded.isAccountNonLocked()).isTrue();
  }

  private User user(String username) {
    return User.builder()
        .username(username)
        .password("encoded")
        .nickname("tester")
        .build();
  }
}

