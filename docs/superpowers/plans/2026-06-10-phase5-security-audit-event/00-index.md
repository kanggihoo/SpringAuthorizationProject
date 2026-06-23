# Phase 5 Security Audit Event Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phase 5의 `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `REFRESH_TOKEN_REUSED` Security Audit Event를 DB에 동기식으로 저장하고, 감사 저장 실패 시 fail-closed로 `AUDIT_STORE_UNAVAILABLE` 503을 반환한다.

**Architecture:** `SecurityAuditService`가 감사 이벤트 생성과 저장 실패 변환을 담당하고, 인증/토큰 흐름은 이 서비스에만 의존한다. `LOGIN_FAILED`와 `REFRESH_TOKEN_REUSED`는 별도 트랜잭션으로 저장하고, `ACCOUNT_LOCKED`는 User lock 상태 변경과 같은 트랜잭션에서 저장한다.

**Tech Stack:** Java 21, Spring Boot 4.0.2, Spring Security 7, Spring Data JPA, Spring Data Redis, PostgreSQL Testcontainers JDBC, JUnit, Mockito, AssertJ, MockMvcTester.

---

## 기준 문서

- 설계 문서: `docs/superpowers/specs/2026-06-10-phase5-security-audit-event-design.md`
- Evidence Matrix: `docs/evidence.md`
- 관련 ADR:
  - `docs/adr/0001-test-evidence-as-auth-security-boundary.md`
  - `docs/adr/0007-auth-failure-module.md`
  - `docs/adr/0009-testcontainers-for-integration-tests.md`

## 구현 순서

1. `01-audit-domain-and-service.md`
   - `SecurityAuditEvent`, `SecurityAuditEventType`, `SecurityAuditEventRepository`, `SecurityAuditService`를 추가한다.
   - `AuthFailureCode.AUDIT_STORE_UNAVAILABLE`을 추가한다.
   - 감사 저장 성공/실패 변환을 단위 테스트와 JPA persistence 테스트로 검증한다.

2. `02-login-failure-audit.md`
   - `AuthServiceImpl.login(...)`의 `BadCredentialsException` 분기에 `LOGIN_FAILED` 감사 저장을 추가한다.
   - 감사 저장 실패 시 실패 카운터 증가 전에 `AUDIT_STORE_UNAVAILABLE`로 중단되는지 검증한다.

3. `03-account-lock-audit-transaction.md`
   - 계정 잠금 확정 로직을 `AccountLockService`로 분리한다.
   - User lock과 `ACCOUNT_LOCKED` 감사 저장을 같은 DB 트랜잭션으로 묶는다.
   - 감사 저장 실패 시 User lock이 rollback되는지 검증한다.

4. `04-refresh-token-reuse-audit.md`
   - `/refresh` 흐름에서 Redis active RT와 요청 RT가 다른 경우 `REFRESH_TOKEN_REUSED` 감사 저장을 추가한다.
   - `RT:{username}` key가 없는 `REFRESH_TOKEN_INVALID` 경로는 감사하지 않음을 검증한다.

5. `05-evidence-and-verification.md`
   - controller의 503 응답 매핑을 검증한다.
   - Phase 5 evidence 테스트를 실행한다.
   - 테스트가 통과한 뒤에만 `docs/evidence.md`의 Phase 5 상태를 `PASS`로 갱신한다.

## 파일 구조

생성:

- `src/main/java/org/example/security/audit/SecurityAuditEventType.java`
- `src/main/java/org/example/security/audit/SecurityAuditEvent.java`
- `src/main/java/org/example/security/audit/SecurityAuditService.java`
- `src/main/java/org/example/security/audit/SecurityAuditServiceImpl.java`
- `src/main/java/org/example/security/account/AccountLockService.java`
- `src/main/java/org/example/security/account/AccountLockServiceImpl.java`
- `src/main/java/org/example/repository/SecurityAuditEventRepository.java`
- `src/test/java/org/example/security/audit/SecurityAuditServiceTest.java`
- `src/test/java/org/example/repository/SecurityAuditEventRepositoryTest.java`
- `src/test/java/org/example/security/account/AccountLockServiceImplTest.java`
- `src/test/java/org/example/security/account/AccountLockServiceIntegrationTest.java`
- `src/test/java/org/example/security/account/AccountLockServiceRollbackIntegrationTest.java`

수정:

- `src/main/java/org/example/security/failure/AuthFailureCode.java`
- `src/main/java/org/example/service/AuthServiceImpl.java`
- `src/main/java/org/example/security/token/TokenLifecycleServiceImpl.java`
- `src/test/java/org/example/service/AuthServiceImplTest.java`
- `src/test/java/org/example/security/token/TokenLifecycleServiceImplTest.java`
- `src/test/java/org/example/controller/AuthControllerTest.java`
- `docs/evidence.md`

## 완료 기준

Phase 5는 아래 테스트가 실제로 실행되고 통과한 뒤에만 완료로 본다.

- `SecurityAuditEventTest.loginFailure_isRecorded`
- `SecurityAuditEventTest.accountLock_isRecorded`
- `SecurityAuditEventTest.refreshReuse_isRecorded`

이 계획에서는 테스트 파일명을 실제 구현 구조에 맞춰 `SecurityAuditServiceTest`, `AccountLockServiceIntegrationTest`, `TokenLifecycleServiceImplTest`로 나누되, `docs/evidence.md`에는 기존 Evidence Matrix의 대상 테스트 이름을 구현 후 실제 테스트명으로 정합성 있게 갱신한다.

필수 검증 포인트:

- 로그인 실패 시 `LOGIN_FAILED` 이벤트가 저장된다.
- `LOGIN_FAILED` 감사 저장 실패 시 실패 카운터가 증가하지 않고 503으로 중단된다.
- 계정 잠금 시 User lock과 `ACCOUNT_LOCKED` 이벤트가 함께 commit된다.
- `ACCOUNT_LOCKED` 감사 저장 실패 시 User lock이 rollback된다.
- Refresh Token 재사용 시 `REFRESH_TOKEN_REUSED` 이벤트가 저장된다.
- `REFRESH_TOKEN_REUSED` 감사 저장 실패 시 기존 401 대신 503을 반환한다.
- `RT:{username}` key가 없는 invalid refresh는 재사용 감사 이벤트를 만들지 않는다.

## 커밋 가이드

각 번호 파일 완료 후 작은 커밋을 만든다.

```bash
git add <files>
git commit -m "feat: add security audit event model"

git add <files>
git commit -m "feat: audit login failures"

git add <files>
git commit -m "feat: audit account lock events"

git add <files>
git commit -m "feat: audit refresh token reuse"

git add docs/evidence.md src/test/java/org/example/controller/AuthControllerTest.java
git commit -m "docs: mark phase5 audit evidence"
```
