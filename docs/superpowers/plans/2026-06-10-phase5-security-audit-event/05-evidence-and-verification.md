# 05. Evidence 갱신과 최종 검증

## 목적

Phase 5의 HTTP 응답 매핑과 Evidence Matrix를 검증한다. `docs/evidence.md`는 실제 테스트가 통과한 뒤에만 갱신한다.

## Task 1: Controller 503 응답 매핑 테스트

**Files:**

- Modify: `src/test/java/org/example/controller/AuthControllerTest.java`

- [ ] **Step 1: 로그인 감사 저장 실패 HTTP 응답 테스트 작성**

`POST /login` nested class에 추가한다.

```java
@Test
@DisplayName("감사 저장소를 사용할 수 없으면 AUDIT_STORE_UNAVAILABLE 코드와 503을 반환한다")
void returns503WithAuditStoreUnavailableCode_whenAuditStoreUnavailableOnLogin() {
  given(authService.login(any()))
      .willThrow(new AuthFailureException(
          AuthFailureCode.AUDIT_STORE_UNAVAILABLE,
          "Authentication service is temporarily unavailable."));

  assertThat(mvc.post().uri("/login")
          .contentType(MediaType.APPLICATION_JSON)
          .content(writeJson(loginRequest("testuser", "wrong-password"))))
      .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
      .bodyJson()
      .extractingPath("$.code")
      .isEqualTo("AUDIT_STORE_UNAVAILABLE");
}
```

- [ ] **Step 2: Refresh Token 재사용 감사 저장 실패 HTTP 응답 테스트 작성**

`POST /refresh` nested class에 추가한다.

```java
@Test
@DisplayName("refresh 감사 저장소를 사용할 수 없으면 AUDIT_STORE_UNAVAILABLE 코드와 503을 반환한다")
void returns503WithAuditStoreUnavailableCode_whenAuditStoreUnavailableOnRefresh() {
  given(authService.refresh("old-refresh-token"))
      .willThrow(new AuthFailureException(
          AuthFailureCode.AUDIT_STORE_UNAVAILABLE,
          "Authentication service is temporarily unavailable."));

  assertThat(mvc.post().uri("/refresh")
          .cookie(new Cookie("Refresh-Token", "old-refresh-token")))
      .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
      .bodyJson()
      .extractingPath("$.code")
      .isEqualTo("AUDIT_STORE_UNAVAILABLE");
}
```

- [ ] **Step 3: RED/GREEN 확인**

실행:

```bash
rtk gradlew test --tests org.example.controller.AuthControllerTest
```

기대 결과:

- `AuthFailureCode.AUDIT_STORE_UNAVAILABLE`이 추가된 뒤에는 별도 controller 구현 없이 PASS해야 한다.
- 실패한다면 `GlobalExceptionHandler.handleAuthFailure(...)`가 `AuthFailureException`을 처리하는지 먼저 확인한다.

## Task 2: Phase 5 focused tests 실행

**Files:**

- Read: `docs/evidence.md`
- Existing or new tests:
  - `src/test/java/org/example/security/audit/SecurityAuditServiceTest.java`
  - `src/test/java/org/example/repository/SecurityAuditEventRepositoryTest.java`
  - `src/test/java/org/example/service/AuthServiceImplTest.java`
  - `src/test/java/org/example/security/account/AccountLockServiceImplTest.java`
  - `src/test/java/org/example/security/account/AccountLockServiceIntegrationTest.java`
  - `src/test/java/org/example/security/account/AccountLockServiceRollbackIntegrationTest.java`
  - `src/test/java/org/example/security/token/TokenLifecycleServiceImplTest.java`
  - `src/test/java/org/example/controller/AuthControllerTest.java`

- [ ] **Step 1: 감사 도메인과 service 테스트 실행**

```bash
rtk gradlew test --tests org.example.security.audit.SecurityAuditServiceTest \
  --tests org.example.repository.SecurityAuditEventRepositoryTest
```

기대 결과: `BUILD SUCCESSFUL`.

- [ ] **Step 2: 로그인 실패와 계정 잠금 감사 테스트 실행**

```bash
rtk gradlew test --tests org.example.service.AuthServiceImplTest \
  --tests org.example.security.account.AccountLockServiceImplTest \
  --tests org.example.security.account.AccountLockServiceIntegrationTest \
  --tests org.example.security.account.AccountLockServiceRollbackIntegrationTest
```

기대 결과: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Refresh Token 재사용 감사 테스트 실행**

```bash
rtk gradlew test --tests org.example.security.token.TokenLifecycleServiceImplTest
```

기대 결과: `BUILD SUCCESSFUL`.

- [ ] **Step 4: HTTP 응답 매핑 테스트 실행**

```bash
rtk gradlew test --tests org.example.controller.AuthControllerTest
```

기대 결과: `BUILD SUCCESSFUL`.

## Task 3: Evidence Matrix 갱신

**Files:**

- Modify: `docs/evidence.md`

- [ ] **Step 1: Phase 5 rows만 갱신**

아래 기준으로 상태를 `PASS`로 바꾼다. 실제 검증일은 실행한 날짜를 사용한다.

```markdown
| 로그인 실패가 감사된다 | 잘못된 비밀번호로 로그인 | `LOGIN_FAILED` Security Audit Event 저장 | `SecurityAuditServiceTest.recordLoginFailed_savesLoginFailedEvent`, `AuthServiceImplTest.login_recordsLoginFailedAudit_beforeFailureCounter` | PASS | 2026-06-10에 Phase 5 focused command run(...)으로 검증 |
| 계정 잠금이 감사된다 | 실패 임계값 도달 | User lock과 `ACCOUNT_LOCKED` Security Audit Event가 같은 트랜잭션으로 저장된다 | `AccountLockServiceImplTest.lockForLoginFailure_locksUserAndRecordsAudit`, `AccountLockServiceIntegrationTest.lockForLoginFailure_commitsUserLockAndAuditTogether`, `AccountLockServiceRollbackIntegrationTest.lockForLoginFailure_rollsBackUserLock_whenAuditFails` | PASS | 2026-06-10에 Phase 5 focused command run(...)으로 검증 |
| Refresh Token 재사용이 감사된다 | 이전 Refresh Token 재사용 | `REFRESH_TOKEN_REUSED` Security Audit Event 저장 | `TokenLifecycleServiceImplTest.rotate_recordsAuditAndRejects_whenRefreshTokenIsReused` | PASS | 2026-06-10에 Phase 5 focused command run(...)으로 검증 |
```

주의:

- 해당 테스트가 실제로 실행되지 않았으면 `PASS`로 바꾸지 않는다.
- audit 저장 실패 503 테스트는 비고에 함께 적어도 되지만, Phase 5의 핵심 대상 테스트명은 반드시 포함한다.
- Phase 6 이후 행은 수정하지 않는다.

- [ ] **Step 2: Evidence 이름과 실제 테스트 이름 정합성 확인**

실행:

```bash
rtk gradlew test --tests org.example.security.audit.SecurityAuditServiceTest.recordLoginFailed_savesLoginFailedEvent \
  --tests org.example.service.AuthServiceImplTest.login_recordsLoginFailedAudit_beforeFailureCounter \
  --tests org.example.security.account.AccountLockServiceImplTest.lockForLoginFailure_locksUserAndRecordsAudit \
  --tests org.example.security.account.AccountLockServiceIntegrationTest.lockForLoginFailure_commitsUserLockAndAuditTogether \
  --tests org.example.security.account.AccountLockServiceRollbackIntegrationTest.lockForLoginFailure_rollsBackUserLock_whenAuditFails \
  --tests org.example.security.token.TokenLifecycleServiceImplTest.rotate_recordsAuditAndRejects_whenRefreshTokenIsReused
```

기대 결과: `BUILD SUCCESSFUL`.

## Task 4: 전체 회귀 검증

- [ ] **Step 1: Phase 3, Phase 4 회귀 테스트 실행**

```bash
rtk gradlew test --tests org.example.security.token.TokenLifecycleServiceImplTest \
  --tests org.example.service.AuthServiceImplTest \
  --tests org.example.security.jwt.JwtAuthenticationFilterTest \
  --tests org.example.service.AdminServiceImplTest \
  --tests org.example.controller.AdminControllerSecurityIntegrationTest
```

기대 결과: `BUILD SUCCESSFUL`.

- [ ] **Step 2: 전체 테스트 실행**

```bash
rtk gradlew test
```

기대 결과: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Checkstyle 실행**

```bash
rtk gradlew checkstyleMain checkstyleTest
```

기대 결과: `BUILD SUCCESSFUL`.

- [ ] **Step 4: git 상태 확인**

```bash
rtk git status
```

기대 결과:

- 구현 파일과 `docs/evidence.md`만 변경되어 있다.
- 계획 문서 외의 의도하지 않은 파일 변경이 없다.

- [ ] **Step 5: Evidence 커밋**

```bash
git add src/test/java/org/example/controller/AuthControllerTest.java docs/evidence.md
git commit -m "docs: mark phase5 audit evidence"
```
