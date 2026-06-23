package org.example.security.audit;

/**
 * 보안 감사 이벤트 기록 서비스.
 */
public interface SecurityAuditService {

  void recordLoginFailed(String username);

  void recordAccountLocked(String username);

  void recordRefreshTokenReused(String username);
}

