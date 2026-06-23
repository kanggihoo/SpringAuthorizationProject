package org.example.security.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.security.failure.AuthFailureCode;

/**
 * 보안 감사 이벤트를 저장하는 엔티티.
 */
@Entity
@Table(name = "security_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityAuditEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SecurityAuditEventType eventType;

  @Column(nullable = false)
  private String username;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuthFailureCode authFailureCode;

  @Column(nullable = false)
  private Instant occurredAt;

  @Column(nullable = false)
  private String description;

  private SecurityAuditEvent(SecurityAuditEventType eventType, String username,
      AuthFailureCode authFailureCode, String description) {
    this.eventType = eventType;
    this.username = username;
    this.authFailureCode = authFailureCode;
    this.occurredAt = Instant.now();
    this.description = description;
  }

  public static SecurityAuditEvent loginFailed(String username) {
    return new SecurityAuditEvent(
        SecurityAuditEventType.LOGIN_FAILED,
        username,
        AuthFailureCode.BAD_CREDENTIALS,
        "Local User login failed.");
  }

  public static SecurityAuditEvent accountLocked(String username) {
    return new SecurityAuditEvent(
        SecurityAuditEventType.ACCOUNT_LOCKED,
        username,
        AuthFailureCode.ACCOUNT_LOCKED,
        "User account locked after repeated login failures.");
  }

  public static SecurityAuditEvent refreshTokenReused(String username) {
    return new SecurityAuditEvent(
        SecurityAuditEventType.REFRESH_TOKEN_REUSED,
        username,
        AuthFailureCode.REFRESH_TOKEN_REUSED,
        "Refresh Token reuse detected.");
  }
}

