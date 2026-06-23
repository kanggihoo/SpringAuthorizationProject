package org.example.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.example.repository.SecurityAuditEventRepository;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceTest {

  @Mock
  private SecurityAuditEventRepository securityAuditEventRepository;

  @InjectMocks
  private SecurityAuditServiceImpl securityAuditService;

  @Test
  @DisplayName("recordLoginFailed stores LOGIN_FAILED audit event")
  void recordLoginFailed_savesLoginFailedEvent() {
    securityAuditService.recordLoginFailed("testuser");

    ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
    verify(securityAuditEventRepository).saveAndFlush(captor.capture());
    SecurityAuditEvent event = captor.getValue();

    assertThat(event.getEventType()).isEqualTo(SecurityAuditEventType.LOGIN_FAILED);
    assertThat(event.getUsername()).isEqualTo("testuser");
    assertThat(event.getAuthFailureCode()).isEqualTo(AuthFailureCode.BAD_CREDENTIALS);
    assertThat(event.getOccurredAt()).isNotNull();
  }

  @Test
  @DisplayName("recordLoginFailed fails closed when audit store is unavailable")
  void recordLoginFailed_throwsAuditStoreUnavailable_whenRepositoryFails() {
    given(securityAuditEventRepository.saveAndFlush(any(SecurityAuditEvent.class)))
        .willThrow(new DataAccessResourceFailureException("db down"));

    assertThatThrownBy(() -> securityAuditService.recordLoginFailed("testuser"))
        .isInstanceOfSatisfying(AuthFailureException.class, failure ->
            assertThat(failure.getCode()).isEqualTo(AuthFailureCode.AUDIT_STORE_UNAVAILABLE));
  }
}

