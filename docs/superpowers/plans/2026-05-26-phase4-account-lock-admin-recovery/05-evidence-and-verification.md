# 05. Evidence Update And Verification

## Task 1: Run Phase 4 Evidence Tests

**Files:**

- Read: `docs/evidence.md`
- Existing tests:
  - `src/test/java/org/example/service/AuthServiceImplTest.java`
  - `src/test/java/org/example/security/jwt/JwtAuthenticationFilterTest.java`
  - `src/test/java/org/example/service/AdminServiceImplTest.java`
  - `src/test/java/org/example/controller/AdminControllerSecurityTest.java`

- [ ] **Step 1: Run focused Phase 4 tests**

Run:

```bash
./gradlew.bat test --tests org.example.service.AuthServiceImplTest --tests org.example.security.jwt.JwtAuthenticationFilterTest --tests org.example.service.AdminServiceImplTest --tests org.example.controller.AdminControllerSecurityTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run Redis counter tests**

Run:

```bash
./gradlew.bat test --tests org.example.repository.LoginFailureRedisRepositoryTest --tests org.example.security.account.LoginFailureCounterTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run broader affected tests**

Run:

```bash
./gradlew.bat test --tests org.example.controller.AuthControllerTest --tests org.example.service.AuthServiceImplTest --tests org.example.repository.LoginFailureRedisRepositoryTest --tests org.example.security.account.LoginFailureCounterTest --tests org.example.service.AdminServiceImplTest --tests org.example.controller.AdminControllerSecurityTest
```

Expected: `BUILD SUCCESSFUL`.

## Task 2: Update Evidence Matrix

**Files:**

- Modify: `docs/evidence.md`

- [ ] **Step 1: Update Phase 4 rows only after tests pass**

Change Phase 4 statuses:

```markdown
| 잘못된 비밀번호가 반복되면 계정이 잠긴다 | username 기준 30분 안에 로그인 5회 실패 | 423 Locked와 DB 계정 잠금 | `AuthServiceImplTest.login_locksUser_afterFiveFailures` | PASS | 2026-05-26에 Phase 4 focused tests로 검증 |
| 잠긴 계정은 로그인할 수 없다 | 잠금 후 올바른 비밀번호로 로그인 | 423 Locked | `AuthServiceImplTest.login_throwsLockedException_whenUserLocked` | PASS | 2026-05-26에 Phase 4 focused tests로 검증 |
| 잠긴 계정은 기존 Access Token을 사용할 수 없다 | 잠금 후 기존 토큰으로 Protected API 호출 | `SecurityContext`가 인증 상태가 되지 않는다 | `JwtAuthenticationFilterTest.doFilter_doesNotAuthenticate_whenUserIsLocked` | PASS | 2026-05-26에 Phase 4 focused tests로 재검증 |
| ADMIN은 계정 잠금을 해제할 수 있다 | ADMIN이 사용자의 잠금을 해제한다 | 계정 잠금이 해제된다 | `AdminServiceImplTest.unlockUser_unlocksLockedUser` | PASS | 2026-05-26에 Phase 4 focused tests로 검증 |
| USER는 계정 잠금을 해제할 수 없다 | USER가 admin endpoint를 호출한다 | 403 Forbidden | `AdminControllerSecurityTest.userCannotUnlockAccount` | PASS | 2026-05-26에 Phase 4 focused tests로 검증 |
```

Use the actual date and command output from the run. Do not mark a row `PASS` if its named test did not execute.

- [ ] **Step 2: Verify evidence names match test names**

Run:

```bash
./gradlew.bat test --tests org.example.service.AuthServiceImplTest.login_locksUser_afterFiveFailures --tests org.example.service.AuthServiceImplTest.login_throwsLockedException_whenUserLocked --tests org.example.service.AdminServiceImplTest.unlockUser_unlocksLockedUser --tests org.example.controller.AdminControllerSecurityTest.userCannotUnlockAccount
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Inspect test XML if Gradle reports success unexpectedly**

Run:

```bash
Get-ChildItem -Recurse -Path build/test-results/test -Filter "*.xml" | Select-String -Pattern "login_locksUser_afterFiveFailures|login_throwsLockedException_whenUserLocked|unlockUser_unlocksLockedUser|userCannotUnlockAccount"
```

Expected: each target test name appears in XML output.

- [ ] **Step 4: Commit evidence update**

```bash
git add docs/evidence.md
git commit -m "docs: mark phase4 account lock evidence"
```

## Task 3: Final Quality Gate

- [ ] **Step 1: Run all tests**

Run:

```bash
./gradlew.bat test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run checkstyle if the project requires it**

Run:

```bash
./gradlew.bat checkstyleMain checkstyleTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Inspect git status**

Run:

```bash
git status --short
```

Expected: no unstaged implementation or evidence changes.
