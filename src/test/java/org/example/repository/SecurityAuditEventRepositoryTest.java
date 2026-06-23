package org.example.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.security.audit.SecurityAuditEvent;
import org.example.security.audit.SecurityAuditEventType;
import org.example.security.failure.AuthFailureCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SecurityAuditEventRepositoryTest {

  @Autowired
  private SecurityAuditEventRepository securityAuditEventRepository;

  @BeforeEach
  void setUp() {
    securityAuditEventRepository.deleteAll();
  }

  @Test
  @DisplayName("stores and retrieves security audit events")
  void storesAndFindsSecurityAuditEvent() {
    SecurityAuditEvent event = SecurityAuditEvent.loginFailed("testuser");
    securityAuditEventRepository.saveAndFlush(event);

    SecurityAuditEvent reloaded = securityAuditEventRepository.findById(event.getId())
        .orElseThrow();

    assertThat(reloaded.getEventType()).isEqualTo(SecurityAuditEventType.LOGIN_FAILED);
    assertThat(reloaded.getUsername()).isEqualTo("testuser");
    assertThat(reloaded.getAuthFailureCode()).isEqualTo(AuthFailureCode.BAD_CREDENTIALS);
  }
}

