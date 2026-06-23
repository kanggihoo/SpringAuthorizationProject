# 04. Refresh Token 재사용 감사 기록

## 목적

`/refresh` 흐름에서 Redis에 `RT:{username}` active 값이 존재하지만 요청 Refresh Token과 값이 다를 때 `REFRESH_TOKEN_REUSED` Security Audit Event를 저장한다. 감사 저장 실패 시 기존 `REFRESH_TOKEN_REUSED` 401 대신 `AUDIT_STORE_UNAVAILABLE` 503을 반환한다.

## Task 1: Refresh Token 재사용 감사 저장 테스트 작성

**Files:**

- Modify: `src/test/java/org/example/security/token/TokenLifecycleServiceImplTest.java`
- Modify: `src/main/java/org/example/security/token/TokenLifecycleServiceImpl.java`

- [ ] **Step 1: `SecurityAuditService` mock 추가**

`TokenLifecycleServiceImplTest`에 mock을 추가한다.

```java
@Mock
private SecurityAuditService securityAuditService;
```

필요 import:

```java
import org.example.security.audit.SecurityAuditService;
```

- [ ] **Step 2: 재사용 감사 저장 검증 테스트 작성**

기존 `rotate_rejectsReusedRefreshToken`에 verify를 추가하거나 아래처럼 이름을 더 명확히 바꾼다.

```java
@Test
@DisplayName("rotate records REFRESH_TOKEN_REUSED audit before rejecting reused Refresh Token")
void rotate_recordsAuditAndRejects_whenRefreshTokenIsReused() {
  given(jwtTokenProvider.parseClaims("old-refresh-token"))
      .willReturn(claims("testuser"));
  given(tokenRedisRepository.findRefreshToken("testuser"))
      .willReturn(Optional.of("current-refresh-token"));

  assertThatThrownBy(() -> tokenLifecycleService.rotate("old-refresh-token"))
      .isInstanceOfSatisfying(AuthFailureException.class, failure ->
          assertThat(failure.getCode()).isEqualTo(AuthFailureCode.REFRESH_TOKEN_REUSED));

  verify(securityAuditService).recordRefreshTokenReused("testuser");
}
```

- [ ] **Step 3: RED 확인**

실행:

```bash
rtk gradlew test --tests org.example.security.token.TokenLifecycleServiceImplTest.rotate_recordsAuditAndRejects_whenRefreshTokenIsReused
```

기대 결과:

- `Wanted but not invoked: securityAuditService.recordRefreshTokenReused("testuser")`로 실패한다.

## Task 2: 감사 저장 실패 시 503 테스트 작성

**Files:**

- Modify: `src/test/java/org/example/security/token/TokenLifecycleServiceImplTest.java`

- [ ] **Step 1: fail-closed 테스트 작성**

```java
@Test
@DisplayName("rotate fails closed when REFRESH_TOKEN_REUSED audit cannot be saved")
void rotate_throwsAuditStoreUnavailable_whenRefreshReuseAuditFails() {
  given(jwtTokenProvider.parseClaims("old-refresh-token"))
      .willReturn(claims("testuser"));
  given(tokenRedisRepository.findRefreshToken("testuser"))
      .willReturn(Optional.of("current-refresh-token"));
  doThrow(new AuthFailureException(
      AuthFailureCode.AUDIT_STORE_UNAVAILABLE,
      "Authentication service is temporarily unavailable."))
      .when(securityAuditService)
      .recordRefreshTokenReused("testuser");

  assertThatThrownBy(() -> tokenLifecycleService.rotate("old-refresh-token"))
      .isInstanceOfSatisfying(AuthFailureException.class, failure ->
          assertThat(failure.getCode()).isEqualTo(AuthFailureCode.AUDIT_STORE_UNAVAILABLE));
}
```

- [ ] **Step 2: key 없음 경로는 감사하지 않는 테스트 작성**

```java
@Test
@DisplayName("rotate does not audit reuse when active Refresh Token is missing")
void rotate_doesNotRecordReuseAudit_whenRefreshTokenIsMissingFromStore() {
  given(jwtTokenProvider.parseClaims("missing-refresh-token"))
      .willReturn(claims("testuser"));
  given(tokenRedisRepository.findRefreshToken("testuser"))
      .willReturn(Optional.empty());

  assertThatThrownBy(() -> tokenLifecycleService.rotate("missing-refresh-token"))
      .isInstanceOfSatisfying(AuthFailureException.class, failure ->
          assertThat(failure.getCode()).isEqualTo(AuthFailureCode.REFRESH_TOKEN_INVALID));

  verify(securityAuditService, never()).recordRefreshTokenReused(anyString());
}
```

필요 import:

```java
import static org.mockito.Mockito.never;
```

- [ ] **Step 3: RED 확인**

실행:

```bash
rtk gradlew test --tests org.example.security.token.TokenLifecycleServiceImplTest
```

기대 결과:

- 감사 호출이 없어 첫 테스트가 실패한다.
- 감사 실패 변환 테스트는 기존 `REFRESH_TOKEN_REUSED`가 반환되어 실패한다.

## Task 3: TokenLifecycleServiceImpl에 감사 호출 추가

**Files:**

- Modify: `src/main/java/org/example/security/token/TokenLifecycleServiceImpl.java`

- [ ] **Step 1: 의존성 추가**

```java
private final SecurityAuditService securityAuditService;
```

필요 import:

```java
import org.example.security.audit.SecurityAuditService;
```

- [ ] **Step 2: value mismatch 분기에 감사 저장 추가**

```java
if (!storedToken.equals(refreshToken)) {
  securityAuditService.recordRefreshTokenReused(jwtSubject);
  throw new AuthFailureException(
      AuthFailureCode.REFRESH_TOKEN_REUSED,
      "Refresh Token이 일치하지 않습니다. (탈취 의심)");
}
```

이 호출은 `storedToken`이 존재하는 경우에만 실행되어야 한다. `Optional.empty()`에서 발생하는 `REFRESH_TOKEN_INVALID` 경로에는 감사 호출을 넣지 않는다.

- [ ] **Step 3: GREEN 확인**

실행:

```bash
rtk gradlew test --tests org.example.security.token.TokenLifecycleServiceImplTest
```

기대 결과:

- 재사용 감사 저장 테스트 PASS
- 감사 저장 실패 시 503 변환 테스트 PASS
- missing RT 경로 no-audit 테스트 PASS
- 기존 rotate/issue/logout 테스트 PASS

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/org/example/security/token/TokenLifecycleServiceImpl.java \
  src/test/java/org/example/security/token/TokenLifecycleServiceImplTest.java
git commit -m "feat: audit refresh token reuse"
```

