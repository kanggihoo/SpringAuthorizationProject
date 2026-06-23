package org.example.security.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.domain.entity.User;
import org.example.repository.SecurityAuditEventRepository;
import org.example.repository.UserRepository;
import org.example.security.audit.SecurityAuditEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AccountLockServiceIntegrationTest {

  @Autowired
  private AccountLockService accountLockService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private SecurityAuditEventRepository securityAuditEventRepository;

  @BeforeEach
  void setUp() {
    securityAuditEventRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("lockForLoginFailure commits User lock and ACCOUNT_LOCKED audit together")
  void lockForLoginFailure_commitsUserLockAndAuditTogether() {
    userRepository.save(user("testuser"));

    accountLockService.lockForLoginFailure("testuser");

    User reloaded = userRepository.findByUsername("testuser").orElseThrow();
    assertThat(reloaded.isAccountNonLocked()).isFalse();
    assertThat(securityAuditEventRepository.findAll())
        .anySatisfy(event -> {
          assertThat(event.getEventType()).isEqualTo(SecurityAuditEventType.ACCOUNT_LOCKED);
          assertThat(event.getUsername()).isEqualTo("testuser");
        });
  }

  private User user(String username) {
    return User.builder()
        .username(username)
        .password("encoded")
        .nickname("tester")
        .build();
  }
}

