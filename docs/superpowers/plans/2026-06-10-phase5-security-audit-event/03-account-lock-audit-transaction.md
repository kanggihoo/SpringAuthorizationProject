# 03. 계정 잠금 감사와 트랜잭션 일관성

## 목적

로그인 실패 임계값에 도달했을 때 User lock 상태 변경과 `ACCOUNT_LOCKED` Security Audit Event 저장을 같은 DB 트랜잭션으로 묶는다. 감사 저장 실패 시 User lock도 rollback되고 `AUDIT_STORE_UNAVAILABLE` 503으로 fail-closed 되어야 한다.

## Task 1: AccountLockService 단위 테스트 작성

**Files:**

- Create: `src/main/java/org/example/security/account/AccountLockService.java`
- Create: `src/main/java/org/example/security/account/AccountLockServiceImpl.java`
- Create: `src/test/java/org/example/security/account/AccountLockServiceImplTest.java`

- [ ] **Step 1: 실패하는 서비스 테스트 작성**

```java
package org.example.security.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.example.domain.entity.User;
import org.example.repository.UserRepository;
import org.example.security.audit.SecurityAuditService;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountLockServiceImplTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private SecurityAuditService securityAuditService;

  @InjectMocks
  private AccountLockServiceImpl accountLockService;

  @Test
  @DisplayName("lockForLoginFailure locks User and records ACCOUNT_LOCKED audit")
  void lockForLoginFailure_locksUserAndRecordsAudit() {
    User user = user("testuser");
    given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));

    accountLockService.lockForLoginFailure("testuser");

    assertThat(user.isAccountNonLocked()).isFalse();
    verify(securityAuditService).recordAccountLocked("testuser");
  }

  @Test
  @DisplayName("lockForLoginFailure throws USER_NOT_FOUND when User is missing")
  void lockForLoginFailure_throwsUserNotFound_whenUserIsMissing() {
    given(userRepository.findByUsername("missing")).willReturn(Optional.empty());

    assertThatThrownBy(() -> accountLockService.lockForLoginFailure("missing"))
        .isInstanceOfSatisfying(AuthFailureException.class, failure ->
            assertThat(failure.getCode()).isEqualTo(AuthFailureCode.USER_NOT_FOUND));
  }

  private User user(String username) {
    return User.builder()
        .username(username)
        .password("encoded")
        .nickname("tester")
        .build();
  }
}
```

- [ ] **Step 2: RED 확인**

실행:

```bash
rtk gradlew test --tests org.example.security.account.AccountLockServiceImplTest
```

기대 결과:

- `AccountLockServiceImpl` 없음으로 컴파일 실패한다.

## Task 2: AccountLockService 구현

**Files:**

- Create: `src/main/java/org/example/security/account/AccountLockService.java`
- Create: `src/main/java/org/example/security/account/AccountLockServiceImpl.java`

- [ ] **Step 1: interface 작성**

```java
package org.example.security.account;

public interface AccountLockService {

  void lockForLoginFailure(String username);
}
```

- [ ] **Step 2: implementation 작성**

```java
package org.example.security.account;

import lombok.RequiredArgsConstructor;
import org.example.domain.entity.User;
import org.example.repository.UserRepository;
import org.example.security.audit.SecurityAuditService;
import org.example.security.failure.AuthFailureCode;
import org.example.security.failure.AuthFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountLockServiceImpl implements AccountLockService {

  private final UserRepository userRepository;
  private final SecurityAuditService securityAuditService;

  @Override
  @Transactional
  public void lockForLoginFailure(String username) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new AuthFailureException(
            AuthFailureCode.USER_NOT_FOUND,
            "User not found."));

    user.lock();
    securityAuditService.recordAccountLocked(username);
  }
}
```

`recordAccountLocked(...)`는 `SecurityAuditServiceImpl`에서 `MANDATORY` 트랜잭션으로 설정했으므로 이 메서드의 트랜잭션에 참여해야 한다.

- [ ] **Step 3: 단위 테스트 GREEN 확인**

실행:

```bash
rtk gradlew test --tests org.example.security.account.AccountLockServiceImplTest
```

기대 결과:

- `BUILD SUCCESSFUL`

## Task 3: 트랜잭션 commit 통합 테스트 작성

**Files:**

- Create: `src/test/java/org/example/security/account/AccountLockServiceIntegrationTest.java`

- [ ] **Step 1: User lock과 ACCOUNT_LOCKED audit가 함께 저장되는 테스트 작성**

```java
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
```

- [ ] **Step 2: commit 검증 GREEN 확인**

실행:

```bash
rtk gradlew test --tests org.example.security.account.AccountLockServiceIntegrationTest
```

기대 결과:

- User lock과 audit event가 모두 저장되면 `BUILD SUCCESSFUL`.
- `recordAccountLocked(...)`가 transaction 없이 호출되면 `MANDATORY` 정책 때문에 실패한다.

## Task 4: 트랜잭션 rollback 통합 테스트 작성

**Files:**

- Create: `src/test/java/org/example/security/account/AccountLockServiceRollbackIntegrationTest.java`

- [ ] **Step 1: 감사 실패 시 User lock rollback 테스트 작성**

```java
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
```

- [ ] **Step 2: rollback 검증 GREEN 확인**

실행:

```bash
rtk gradlew test --tests org.example.security.account.AccountLockServiceRollbackIntegrationTest
```

기대 결과:

- `@Transactional`이 없거나 audit 예외를 삼키면 rollback 검증이 실패한다.
- 구현 후 `BUILD SUCCESSFUL`.

## Task 5: AuthServiceImpl의 threshold lock 흐름 연결

**Files:**

- Modify: `src/main/java/org/example/service/AuthServiceImpl.java`
- Modify: `src/test/java/org/example/service/AuthServiceImplTest.java`

- [ ] **Step 1: `AuthServiceImplTest`에 AccountLockService mock 추가**

```java
@Mock
private AccountLockService accountLockService;
```

필요 import:

```java
import org.example.security.account.AccountLockService;
```

- [ ] **Step 2: 기존 threshold 테스트를 서비스 위임 검증으로 수정**

`login_locksUser_afterFiveFailures`는 User 객체 직접 mutation 대신 lock service 호출을 검증한다.

```java
@Test
@DisplayName("login locks User after fifth bad credential failure")
void login_locksUser_afterFiveFailures() {
  User user = user("testuser", true);
  BadCredentialsException badCredentials = new BadCredentialsException("Bad credentials");
  given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
  given(authenticationManager.authenticate(any())).willThrow(badCredentials);
  given(loginFailureCounter.recordFailure("testuser")).willReturn(true);

  assertThatThrownBy(() ->
      authServiceImpl.login(createLoginRequest("testuser", "wrong-password")))
      .isInstanceOfSatisfying(AuthFailureException.class, failure -> {
        assertThat(failure.getCode()).isEqualTo(AuthFailureCode.ACCOUNT_LOCKED);
        assertThat(failure.getCause()).isSameAs(badCredentials);
      });

  verify(securityAuditService).recordLoginFailed("testuser");
  verify(accountLockService).lockForLoginFailure("testuser");
  verify(userRepository, never()).save(any());
  verify(tokenLifecycleService, never()).issue(any(), any());
}
```

- [ ] **Step 3: `AuthServiceImpl` threshold 분기 수정**

```java
if (loginFailureCounter.recordFailure(username)) {
  accountLockService.lockForLoginFailure(username);
  throw new AuthFailureException(
      AuthFailureCode.ACCOUNT_LOCKED,
      "User account is locked after too many login failures.",
      e);
}
```

`AuthServiceImpl`에서 직접 `user.lock()`과 `userRepository.save(user)`를 호출하지 않는다.

- [ ] **Step 4: GREEN 확인**

실행:

```bash
rtk gradlew test --tests org.example.service.AuthServiceImplTest \
  --tests org.example.security.account.AccountLockServiceImplTest \
  --tests org.example.security.account.AccountLockServiceIntegrationTest \
  --tests org.example.security.account.AccountLockServiceRollbackIntegrationTest
```

기대 결과:

- `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/org/example/security/account/AccountLockService.java \
  src/main/java/org/example/security/account/AccountLockServiceImpl.java \
  src/main/java/org/example/service/AuthServiceImpl.java \
  src/test/java/org/example/security/account/AccountLockServiceImplTest.java \
  src/test/java/org/example/security/account/AccountLockServiceIntegrationTest.java \
  src/test/java/org/example/security/account/AccountLockServiceRollbackIntegrationTest.java \
  src/test/java/org/example/service/AuthServiceImplTest.java
git commit -m "feat: audit account lock events"
```
