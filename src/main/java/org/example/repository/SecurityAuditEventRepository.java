package org.example.repository;

import org.example.security.audit.SecurityAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 보안 감사 이벤트 영속성 저장소.
 */
public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {
}

