# 02. 로그인 실패 감사 기록 연결

## 목적

`AuthServiceImpl.login(...)`에서 비밀번호 검증 실패가 발생하면 실패 카운터를 증가시키기 전에 `LOGIN_FAILED` Security Audit Event를 저장한다. 감사 저장 실패 시 fail-closed로 `AUDIT_STORE_UNAVAILABLE`을 반환하고, 실패 카운터와 토큰 발급은 실행하지 않는다.

## Task 1: 로그인 실패 감사 저장 테스트 추가

**Files:**

- Modify: `src/test/java/org/example/service/AuthServiceImplTest.java`
- Modify: `src/main/java/org/example/service/AuthServiceImpl.java`

- [ ] **Step 1: `SecurityAuditService` mock 추가**

`AuthServiceImplTest`에 mock을 추가한다.

```java
@Mock
private SecurityAuditService securityAuditService;
```

필요 import:

```java
import org.example.security.audit.SecurityAuditService;
```

- [ ] **Step 2: 로그인 실패 감사 저장 검증 테스트 작성**

`login_throwsBadCredentials_whenFailureThresholdNotReached` 테스트에 검증을 추가하거나, 별도 테스트로 분리한다. 검증이 명확하도록 별도 테스트를 권장한다.

```java
@Test
@DisplayName("login records LOGIN_FAILED audit before increasing failure counter")
void login_recordsLoginFailedAudit_beforeFailureCounter() {
  User user = user("testuser", true);
  BadCredentialsException badCredentials = new BadCredentialsException("Bad credentials");
  given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
  given(authenticationManager.authenticate(any())).willThrow(badCredentials);
  given(loginFailureCounter.recordFailure("testuser")).willReturn(false);

  assertThatThrownBy(() ->
      authServiceImpl.login(createLoginRequest("testuser", "wrong-password")))
      .isInstanceOfSatisfying(AuthFailureException.class, failure ->
          assertThat(failure.getCode()).isEqualTo(AuthFailureCode.BAD_CREDENTIALS));

  InOrder inOrder = inOrder(securityAuditService, loginFailureCounter);
  inOrder.verify(securityAuditService).recordLoginFailed("testuser");
  inOrder.verify(loginFailureCounter).recordFailure("testuser");
  verify(tokenLifecycleService, never()).issue(any(), any());
}
```

필요 import:

```java
import static org.mockito.Mockito.inOrder;
import org.mockito.InOrder;
```

- [ ] **Step 3: RED 확인**

실행:

```bash
rtk gradlew test --tests org.example.service.AuthServiceImplTest.login_recordsLoginFailedAudit_beforeFailureCounter
```

기대 결과:

- `Wanted but not invoked: securityAuditService.recordLoginFailed("testuser")` 형태로 실패한다.

## Task 2: 감사 저장 실패 시 fail-closed 테스트 추가

**Files:**

- Modify: `src/test/java/org/example/service/AuthServiceImplTest.java`

- [ ] **Step 1: 감사 저장 실패가 실패 카운터를 막는 테스트 작성**

```java
@Test
@DisplayName("login fails closed before failure counter when LOGIN_FAILED audit cannot be saved")
void login_throwsAuditStoreUnavailable_whenLoginFailedAuditFails() {
  User user = user("testuser", true);
  given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
  given(authenticationManager.authenticate(any()))
      .willThrow(new BadCredentialsException("Bad credentials"));
  doThrow(new AuthFailureException(
      AuthFailureCode.AUDIT_STORE_UNAVAILABLE,
      "Authentication service is temporarily unavailable."))
      .when(securityAuditService)
      .recordLoginFailed("testuser");

  assertThatThrownBy(() ->
      authServiceImpl.login(createLoginRequest("testuser", "wrong-password")))
      .isInstanceOfSatisfying(AuthFailureException.class, failure ->
          assertThat(failure.getCode()).isEqualTo(AuthFailureCode.AUDIT_STORE_UNAVAILABLE));

  verify(loginFailureCounter, never()).recordFailure(any());
  verify(userRepository, never()).save(any());
  verify(tokenLifecycleService, never()).issue(any(), any());
}
```

필요 import:

```java
import static org.mockito.Mockito.doThrow;
```

- [ ] **Step 2: RED 확인**

실행:

```bash
rtk gradlew test --tests org.example.service.AuthServiceImplTest.login_throwsAuditStoreUnavailable_whenLoginFailedAuditFails
```

기대 결과:

- 구현 전에는 `BAD_CREDENTIALS` 또는 `ACCOUNT_LOCKED`가 반환되어 실패한다.

## Task 3: AuthServiceImpl에 감사 호출 추가

**Files:**

- Modify: `src/main/java/org/example/service/AuthServiceImpl.java`

- [ ] **Step 1: 의존성 추가**

```java
private final SecurityAuditService securityAuditService;
```

필요 import:

```java
import org.example.security.audit.SecurityAuditService;
```

- [ ] **Step 2: BadCredentialsException 분기에 감사 호출 추가**

`catch (BadCredentialsException e)`의 첫 줄에서 감사 저장을 수행한다.

```java
} catch (BadCredentialsException e) {
  securityAuditService.recordLoginFailed(username);
  if (loginFailureCounter.recordFailure(username)) {
    user.lock();
    userRepository.save(user);
    throw new AuthFailureException(
        AuthFailureCode.ACCOUNT_LOCKED,
        "User account is locked after too many login failures.",
        e);
  }
  throw new AuthFailureException(
      AuthFailureCode.BAD_CREDENTIALS,
      "Invalid username or password.",
      e);
}
```

이 위치가 중요하다. 감사 저장이 실패하면 `recordFailure(username)`이 호출되지 않아야 한다.

- [ ] **Step 3: GREEN 확인**

실행:

```bash
rtk gradlew test --tests org.example.service.AuthServiceImplTest
```

기대 결과:

- 새 로그인 실패 감사 테스트 PASS
- 기존 로그인 성공, locked User, unknown User 테스트 PASS

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/org/example/service/AuthServiceImpl.java \
  src/test/java/org/example/service/AuthServiceImplTest.java
git commit -m "feat: audit login failures"
```

