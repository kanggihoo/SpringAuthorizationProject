# Phase 4 Account Lock & Admin Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Phase 4 evidence for username-based Account Lock and ADMIN recovery.

**Architecture:** Login failure count is stored in Redis as a short-lived counter, while final Account Lock state is stored in the `users.accountNonLocked` DB field. `AuthServiceImpl` coordinates login state transitions, `LoginFailureCounter` owns threshold policy, Redis repository owns Redis mechanics, and `AdminService` owns recovery.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security 7, Spring Data JPA, Spring Data Redis, JUnit, Mockito, Testcontainers Redis/PostgreSQL, AssertJ, MockMvcTester.

---

## Source Spec

- Spec: `docs/superpowers/specs/2026-05-26-phase4-account-lock-admin-recovery-design.md`
- Evidence matrix: `docs/evidence.md`
- Relevant ADRs:
  - `docs/adr/0006-authenticated-user-module-for-account-lock.md`
  - `docs/adr/0007-auth-failure-module.md`
  - `docs/adr/0009-testcontainers-for-integration-tests.md`

## Implementation Order

1. `01-domain-and-counter.md`
   - Add `User.lock()` / `User.unlock()`.
   - Add Redis repository and counter policy for `auth:login:fail:user:{username}`.

2. `02-auth-service-lock-flow.md`
   - Wire Account Lock into `AuthServiceImpl.login(...)`.
   - Add service tests for 5 failures, locked login rejection, and success counter clearing.

3. `03-admin-recovery.md`
   - Add `AdminService` and `AdminController`.
   - Add ADMIN unlock service evidence.

4. `04-admin-controller-security.md`
   - Add controller security evidence that USER cannot unlock.
   - Add ADMIN happy-path controller evidence if useful.

5. `05-evidence-and-verification.md`
   - Run Phase 4 target tests.
   - Update `docs/evidence.md` from pending status to PASS only for passing tests.

## File Structure

Create:

- `src/main/java/org/example/repository/LoginFailureRedisRepository.java`
- `src/main/java/org/example/security/account/LoginFailureCounter.java`
- `src/main/java/org/example/service/AdminService.java`
- `src/main/java/org/example/service/AdminServiceImpl.java`
- `src/main/java/org/example/controller/AdminController.java`
- `src/test/java/org/example/repository/LoginFailureRedisRepositoryTest.java`
- `src/test/java/org/example/security/account/LoginFailureCounterTest.java`
- `src/test/java/org/example/service/AdminServiceImplTest.java`
- `src/test/java/org/example/controller/AdminControllerSecurityTest.java`

Modify:

- `src/main/java/org/example/domain/entity/User.java`
- `src/main/java/org/example/service/AuthServiceImpl.java`
- `src/test/java/org/example/service/AuthServiceImplTest.java`
- `docs/evidence.md`

No production code should use IP-based throttling in this Phase 4 implementation. That is a follow-up hardening item.

## Evidence Target

Phase 4 is complete only when these evidence rows pass:

- `AuthServiceImplTest.login_locksUser_afterFiveFailures`
- `AuthServiceImplTest.login_throwsLockedException_whenUserLocked`
- `JwtAuthenticationFilterTest.doFilter_doesNotAuthenticate_whenUserIsLocked`
- `AdminServiceImplTest.unlockUser_unlocksLockedUser`
- `AdminControllerSecurityTest.userCannotUnlockAccount`

## Commit Guidance

Use small commits after each numbered plan file:

```bash
git add <files>
git commit -m "feat: add login failure counter"
git add <files>
git commit -m "feat: lock users after repeated login failures"
git add <files>
git commit -m "feat: add admin account unlock"
git add <files>
git commit -m "test: prove admin unlock authorization"
git add docs/evidence.md
git commit -m "docs: mark phase4 account lock evidence"
```
