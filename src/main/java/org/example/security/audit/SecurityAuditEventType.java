package org.example.security.audit;

/**
 * 감사 이벤트 종류.
 */
public enum SecurityAuditEventType {
  LOGIN_FAILED,
  ACCOUNT_LOCKED,
  REFRESH_TOKEN_REUSED
}

