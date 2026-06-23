# 01. 감사 도메인과 서비스 기반 추가

## 목적

Security Audit Event를 DB에 저장할 수 있는 최소 도메인, repository, service를 만든다. 이 단계는 아직 인증 흐름에 연결하지 않고, 감사 저장 자체와 실패 변환만 검증한다.

## Task 1: Auth Failure Code 추가

**Files:**

- Modify: `src/main/java/org/example/security/failure/AuthFailureCode.java`
- Test: `src/test/java/org/example/security/audit/SecurityAuditServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/org/example/security/audit/SecurityAuditServiceTest.java`를 만들고, 저장소 장애가 `AUDIT_STORE_UNAVAILABLE`로 변환되는 테스트를 먼저 작성한다.

```java
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

    ArgumentCaptor<SecurityAuditEvent> captor =
        ArgumentCaptor.forClass(SecurityAuditEvent.class);
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
```

- [ ] **Step 2: RED 확인**

실행:

```bash
rtk gradlew test --tests org.example.security.audit.SecurityAuditServiceTest
```

기대 결과:

- 컴파일 실패
- `SecurityAuditEvent`, `SecurityAuditEventType`, `SecurityAuditServiceImpl`, `SecurityAuditEventRepository`, `AUDIT_STORE_UNAVAILABLE` 중 하나 이상이 없다는 오류가 나온다.

- [ ] **Step 3: `AUDIT_STORE_UNAVAILABLE` 추가**

`AuthFailureCode`에 503 코드를 추가한다.

```java
AUDIT_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
```

권장 위치는 `TOKEN_STORE_UNAVAILABLE` 근처다. `GlobalExceptionHandler`는 이미 `AuthFailureException`의 code status를 사용하므로 별도 handler 수정은 필요 없다.

## Task 2: Security Audit Event 도메인 추가

**Files:**

- Create: `src/main/java/org/example/security/audit/SecurityAuditEventType.java`
- Create: `src/main/java/org/example/security/audit/SecurityAuditEvent.java`
- Create: `src/main/java/org/example/repository/SecurityAuditEventRepository.java`

- [ ] **Step 1: 이벤트 타입 enum 작성**

```java
package org.example.security.audit;

public enum SecurityAuditEventType {
  LOGIN_FAILED,
  ACCOUNT_LOCKED,
  REFRESH_TOKEN_REUSED
}
```

- [ ] **Step 2: append-only JPA entity 작성**

```java
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
```

- [ ] **Step 3: repository 작성**

```java
package org.example.repository;

import org.example.security.audit.SecurityAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {
}
```

## Task 3: SecurityAuditService 추가

**Files:**

- Create: `src/main/java/org/example/security/audit/SecurityAuditService.java`
- Create: `src/main/java/org/example/security/audit/SecurityAuditServiceImpl.java`
- Test: `src/test/java/org/example/security/audit/SecurityAuditServiceTest.java`

- [ ] **Step 1: service interface 작성**

```java
package org.example.security.audit;

public interface SecurityAuditService {

  void recordLoginFailed(String username);

  void recordAccountLocked(String username);

  void recordRefreshTokenReused(String username);
}
```

- [ ] **Step 2: service implementation 작성**

```java
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
```

- [ ] **Step 3: 서비스 테스트 통과 확인**

`SecurityAuditServiceTest`에 나머지 이벤트 생성 검증도 추가한다. 이 테스트는 Phase 5의 세 이벤트가 모두 올바른 `AuthFailureCode`로 저장되는지를 보장한다.

```java
@Test
@DisplayName("recordAccountLocked stores ACCOUNT_LOCKED audit event")
void recordAccountLocked_savesAccountLockedEvent() {
  securityAuditService.recordAccountLocked("testuser");

  ArgumentCaptor<SecurityAuditEvent> captor =
      ArgumentCaptor.forClass(SecurityAuditEvent.class);
  verify(securityAuditEventRepository).saveAndFlush(captor.capture());
  SecurityAuditEvent event = captor.getValue();
  assertThat(event.getEventType()).isEqualTo(SecurityAuditEventType.ACCOUNT_LOCKED);
  assertThat(event.getUsername()).isEqualTo("testuser");
  assertThat(event.getAuthFailureCode()).isEqualTo(AuthFailureCode.ACCOUNT_LOCKED);
  assertThat(event.getOccurredAt()).isNotNull();
}

@Test
@DisplayName("recordRefreshTokenReused stores REFRESH_TOKEN_REUSED audit event")
void recordRefreshTokenReused_savesRefreshTokenReusedEvent() {
  securityAuditService.recordRefreshTokenReused("testuser");

  ArgumentCaptor<SecurityAuditEvent> captor =
      ArgumentCaptor.forClass(SecurityAuditEvent.class);
  verify(securityAuditEventRepository).saveAndFlush(captor.capture());
  SecurityAuditEvent event = captor.getValue();
  assertThat(event.getEventType()).isEqualTo(SecurityAuditEventType.REFRESH_TOKEN_REUSED);
  assertThat(event.getUsername()).isEqualTo("testuser");
  assertThat(event.getAuthFailureCode()).isEqualTo(AuthFailureCode.REFRESH_TOKEN_REUSED);
  assertThat(event.getOccurredAt()).isNotNull();
}
```

실행:

```bash
rtk gradlew test --tests org.example.security.audit.SecurityAuditServiceTest
```

기대 결과:

- `recordLoginFailed_savesLoginFailedEvent` PASS
- `recordAccountLocked_savesAccountLockedEvent` PASS
- `recordRefreshTokenReused_savesRefreshTokenReusedEvent` PASS
- `recordLoginFailed_throwsAuditStoreUnavailable_whenRepositoryFails` PASS

## Task 4: JPA persistence 검증 추가

**Files:**

- Create: `src/test/java/org/example/repository/SecurityAuditEventRepositoryTest.java`

- [ ] **Step 1: PostgreSQL Testcontainers JDBC 기반 repository 테스트 작성**

`src/test/resources/application-test.yml`은 이미 `jdbc:tc:postgresql:16:///testdb`를 사용한다. H2를 추가하지 않는다.

```java
package org.example.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.security.audit.SecurityAuditEvent;
import org.example.security.audit.SecurityAuditEventType;
import org.example.security.failure.AuthFailureCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class SecurityAuditEventRepositoryTest {

  @Autowired
  private SecurityAuditEventRepository repository;

  @Test
  @DisplayName("SecurityAuditEvent is persisted as an append-only record")
  void save_persistsSecurityAuditEvent() {
    SecurityAuditEvent saved = repository.save(SecurityAuditEvent.loginFailed("testuser"));

    SecurityAuditEvent found = repository.findById(saved.getId()).orElseThrow();

    assertThat(found.getEventType()).isEqualTo(SecurityAuditEventType.LOGIN_FAILED);
    assertThat(found.getUsername()).isEqualTo("testuser");
    assertThat(found.getAuthFailureCode()).isEqualTo(AuthFailureCode.BAD_CREDENTIALS);
    assertThat(found.getOccurredAt()).isNotNull();
    assertThat(found.getDescription()).isNotBlank();
  }
}
```

- [ ] **Step 2: repository 테스트 RED/GREEN 확인**

실행:

```bash
rtk gradlew test --tests org.example.repository.SecurityAuditEventRepositoryTest
```

기대 결과:

- 구현 전에는 entity/repository 없음으로 실패한다.
- 구현 후 `BUILD SUCCESSFUL`.

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/org/example/security/failure/AuthFailureCode.java \
  src/main/java/org/example/security/audit \
  src/main/java/org/example/repository/SecurityAuditEventRepository.java \
  src/test/java/org/example/security/audit/SecurityAuditServiceTest.java \
  src/test/java/org/example/repository/SecurityAuditEventRepositoryTest.java
git commit -m "feat: add security audit event model"
```
