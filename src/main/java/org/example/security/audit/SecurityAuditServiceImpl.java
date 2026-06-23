package org.example.security.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.repository.SecurityAuditEventRepository;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 이벤트 감사 저장 책임을 담당하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditServiceImpl implements SecurityAuditService {

  private final SecurityAuditEventRepository securityAuditEventRepository;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordLoginFailed(String username) {
    save(SecurityAuditEvent.loginFailed(username));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordAccountLocked(String username) {
    save(SecurityAuditEvent.accountLocked(username));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordRefreshTokenReused(String username) {
    save(SecurityAuditEvent.refreshTokenReused(username));
  }

  private void save(SecurityAuditEvent event) {
    try {
      securityAuditEventRepository.saveAndFlush(event);
    } catch (DataAccessException e) {
      log.error("Security Audit Event persistence failed. eventType={}, username={}",
          event.getEventType(), event.getUsername(), e);
      throw new AuthFailureException(
          AuthFailureCode.AUDIT_STORE_UNAVAILABLE,
          "Authentication service is temporarily unavailable.",
          e);
    }
  }
}

