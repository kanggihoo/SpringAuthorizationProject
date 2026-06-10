# Phase 5 Security Audit Event Design

## Purpose

Phase 5 proves that security-significant Auth Failure scenarios are recorded as
Security Audit Events before the service returns the final Auth Failure response.
The evidence source of truth remains `docs/evidence.md`.

This design focuses on failure and risk signals, not on general activity
tracking. Normal login success, Refresh Token Rotation success, logout success,
and audit event query APIs are follow-up items.

## Scope

In scope:

- Record a `LOGIN_FAILED` Security Audit Event when Local User credential
  authentication fails. Phase 5 completion evidence targets wrong-password
  failure for an existing User; unknown username attempts may use the same event
  type without becoming a separate completion criterion.
- Record an `ACCOUNT_LOCKED` Security Audit Event when repeated login failure
  transitions a User into Account Lock.
- Record a `REFRESH_TOKEN_REUSED` Security Audit Event when a presented Refresh
  Token differs from the active Refresh Token stored for the same JWT Subject.
- Fail closed with `AUDIT_STORE_UNAVAILABLE` and HTTP 503 when a required audit
  write cannot be completed.
- Keep audit writes synchronous for Phase 5.
- Keep Security Audit Event persistence in the application database as a
  dedicated table.

Out of scope:

- Asynchronous audit handling with `@Async`.
- Kafka, RabbitMQ, Redis Stream, or another durable event pipeline.
- A physically separate audit database.
- Admin audit search or export APIs.
- Normal activity events such as `LOGIN_SUCCESS`, `REFRESH_TOKEN_ROTATED`, or
  `LOGOUT_SUCCESS`.
- Auditing invalid Refresh Token requests when no active Token Store value
  exists.

## Policy

Security Audit Event policy:

- Audit events are structured records, not plain application log messages.
- The first Phase 5 storage target is the current application DB in a
  `security_audit_events` table.
- Audit event writes are synchronous.
- Audit write failure is a security-relevant dependency failure.
- If a required audit write fails, the service returns
  `AUDIT_STORE_UNAVAILABLE` with HTTP 503 instead of the original Auth Failure.
- The external response message must stay generic and must not expose database
  or audit infrastructure details.

Recommended user-facing response mapping:

- `BAD_CREDENTIALS` -> HTTP 401, "Invalid username or password."
- `ACCOUNT_LOCKED` -> HTTP 423, "User account is locked."
- `REFRESH_TOKEN_REUSED` -> HTTP 401, "Refresh Token reuse detected."
- `AUDIT_STORE_UNAVAILABLE` -> HTTP 503, "Authentication service is temporarily unavailable."

## Event Types

Required Phase 5 event types:

- `LOGIN_FAILED`
  - A Local User login attempt failed credential validation.
  - This event stores the attempted username. If no User row is resolved, the
    same event type can still be used while the external response remains
    indistinguishable from wrong password.

- `ACCOUNT_LOCKED`
  - A User's account state changed to locked because the login failure threshold
    was reached.
  - This is a state-change event and must stay consistent with the User lock
    update.

- `REFRESH_TOKEN_REUSED`
  - A Refresh Token request used a signed token whose JWT Subject has an active
    Token Store value, but the presented token is not the active value.

Do not use `REFRESH_TOKEN_REUSED` when `RT:{username}` is missing from Redis.
That case remains `REFRESH_TOKEN_INVALID` because the service cannot distinguish
logout, expiration, deletion, Redis data loss, and a never-active token from the
presented token alone.

## Data Model

Create `SecurityAuditEvent` as an append-only entity.

Recommended fields:

- `id`
- `eventType`
- `username`
- `authFailureCode`
- `occurredAt`
- `description`

Optional fields that can be added if request context is available without
spreading servlet APIs into domain services:

- `ipAddress`
- `userAgent`

Do not store raw Access Tokens or raw Refresh Tokens in audit records. If a
future investigation needs token correlation, use a one-way token fingerprint
instead of the token value.

## Components

### SecurityAuditEvent

Owns the persisted audit record.

Responsibilities:

- Represent one security-significant event.
- Store event type, username or attempted username, failure code, timestamp, and
  a short internal description.
- Avoid storing raw secrets.

### SecurityAuditEventType

Enum values:

- `LOGIN_FAILED`
- `ACCOUNT_LOCKED`
- `REFRESH_TOKEN_REUSED`

### SecurityAuditEventRepository

Spring Data repository for appending audit events.

It should not contain policy decisions. It only persists and retrieves audit
records for tests or future query features.

### SecurityAuditService

Service facade used by authentication and token lifecycle code.

Responsibilities:

- Build audit events from security scenario inputs.
- Persist audit events.
- Convert audit persistence failures into
  `AuthFailureException(AuthFailureCode.AUDIT_STORE_UNAVAILABLE)`.
- Provide explicit methods for each required event:
  - `recordLoginFailed(...)`
  - `recordAccountLocked(...)`
  - `recordRefreshTokenReused(...)`

`AuthServiceImpl` and `TokenLifecycleServiceImpl` must depend on
`SecurityAuditService`, not directly on `SecurityAuditEventRepository`.

### AuthFailureCode

Add:

- `AUDIT_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE)`

This code represents required audit persistence being unavailable. It is
separate from `TOKEN_STORE_UNAVAILABLE`, which is specific to Redis-backed Token
Store and Logout Blacklist operations.

## Transaction Boundaries

### LOGIN_FAILED

`LOGIN_FAILED` records a failed attempt, not a User state transition.

Recommended transaction behavior:

- Persist in a separate transaction with `REQUIRES_NEW`.
- If the audit write fails, stop before incrementing the login failure counter.
- Return `AUDIT_STORE_UNAVAILABLE` with HTTP 503.

Flow:

```text
BadCredentialsException
-> SecurityAuditService.recordLoginFailed(...)
-> LoginFailureCounter.recordFailure(username)
-> BAD_CREDENTIALS / 401 or ACCOUNT_LOCKED / 423
```

If `recordLoginFailed(...)` fails:

```text
BadCredentialsException
-> SecurityAuditService.recordLoginFailed(...)
-> audit DB write fails
-> AUDIT_STORE_UNAVAILABLE / 503
```

### ACCOUNT_LOCKED

`ACCOUNT_LOCKED` records a DB state transition. The User lock update and the
audit insert should be committed or rolled back together.

Recommended transaction behavior:

- Move the lock transition into a transactional boundary such as
  `AccountLockService.lockForLoginFailure(username)`.
- Inside that transaction:
  - Load or attach the User.
  - Call `user.lock()`.
  - Persist an `ACCOUNT_LOCKED` Security Audit Event.
- The `ACCOUNT_LOCKED` audit write should join the current transaction, not
  start `REQUIRES_NEW`.
- If the audit write fails, rollback the User lock update and return
  `AUDIT_STORE_UNAVAILABLE` with HTTP 503.

Flow:

```text
LOGIN_FAILED audit saved
-> LoginFailureCounter.recordFailure(username)
-> threshold reached
-> AccountLockService.lockForLoginFailure(username)
   -> user.lock()
   -> SecurityAuditService.recordAccountLocked(...)
-> ACCOUNT_LOCKED / 423
```

If `recordAccountLocked(...)` fails:

```text
threshold reached
-> user.lock()
-> ACCOUNT_LOCKED audit write fails
-> rollback user.lock()
-> AUDIT_STORE_UNAVAILABLE / 503
```

### REFRESH_TOKEN_REUSED

Refresh Token reuse detection compares the presented token with the active token
stored under `RT:{username}`. This branch reads Redis but does not modify Redis.

Recommended transaction behavior:

- Persist `REFRESH_TOKEN_REUSED` in a separate DB transaction with
  `REQUIRES_NEW`.
- No distributed transaction is required because the reuse branch performs no
  Redis write.
- If the audit write fails, return `AUDIT_STORE_UNAVAILABLE` with HTTP 503
  instead of `REFRESH_TOKEN_REUSED`.

Flow:

```text
POST /refresh
-> parse Refresh Token subject
-> read RT:{username} from Redis
-> stored token exists and differs from presented token
-> SecurityAuditService.recordRefreshTokenReused(...)
-> REFRESH_TOKEN_REUSED / 401
```

If `recordRefreshTokenReused(...)` fails:

```text
stored token exists and differs from presented token
-> SecurityAuditService.recordRefreshTokenReused(...)
-> audit DB write fails
-> AUDIT_STORE_UNAVAILABLE / 503
```

## Data Flow

Failed login before threshold:

```text
POST /login
-> AuthController
-> AuthServiceImpl
-> UserRepository.findByUsername(username)
-> AuthenticationManager.authenticate(...)
-> BadCredentialsException
-> SecurityAuditService.recordLoginFailed(...)
-> LoginFailureCounter.recordFailure(username)
-> count < 5
-> BAD_CREDENTIALS / 401
```

Failed login at threshold:

```text
POST /login
-> AuthServiceImpl
-> BadCredentialsException
-> SecurityAuditService.recordLoginFailed(...)
-> LoginFailureCounter.recordFailure(username)
-> count == 5
-> AccountLockService.lockForLoginFailure(username)
   -> User.lock()
   -> SecurityAuditService.recordAccountLocked(...)
-> ACCOUNT_LOCKED / 423
```

Refresh Token reuse:

```text
POST /refresh
-> AuthController
-> AuthServiceImpl.refresh(refreshToken)
-> TokenLifecycleServiceImpl.rotate(refreshToken)
-> JwtTokenProvider.parseClaims(refreshToken)
-> TokenRedisRepository.findRefreshToken(jwtSubject)
-> stored token exists but differs
-> SecurityAuditService.recordRefreshTokenReused(...)
-> REFRESH_TOKEN_REUSED / 401
```

Invalid Refresh Token with no active Token Store value:

```text
POST /refresh
-> TokenRedisRepository.findRefreshToken(jwtSubject)
-> RT:{username} missing
-> REFRESH_TOKEN_INVALID / 401
```

This path is not a Phase 5 audit requirement.

## Failure Handling

Use existing Auth Failure response mechanics through `GlobalExceptionHandler`.

Expected Auth Failure codes:

- `BAD_CREDENTIALS`
- `ACCOUNT_LOCKED`
- `REFRESH_TOKEN_REUSED`
- `AUDIT_STORE_UNAVAILABLE`

Audit persistence failures should be caught at the Security Audit Service
boundary and rethrown as `AuthFailureException` with
`AUDIT_STORE_UNAVAILABLE`.

Do not leak internal persistence failure details to the client. Log the root
cause on the server side.

## Tests And Evidence

Required Phase 5 evidence:

- `SecurityAuditEventTest.loginFailure_isRecorded`
  - Wrong password for a Local User records `LOGIN_FAILED`.
  - The event includes username, `BAD_CREDENTIALS`, and `occurredAt`.

- `SecurityAuditEventTest.accountLock_isRecorded`
  - The threshold login failure that locks a User records `ACCOUNT_LOCKED`.
  - The User lock state and audit record are committed together.

- `SecurityAuditEventTest.refreshReuse_isRecorded`
  - A signed Refresh Token whose JWT Subject has a different active Redis token
    records `REFRESH_TOKEN_REUSED`.
  - The response remains the existing reuse Auth Failure when audit succeeds.

Required fail-closed tests:

- Login failure returns `AUDIT_STORE_UNAVAILABLE` with HTTP 503 when
  `LOGIN_FAILED` audit persistence fails.
- Account Lock rolls back and returns `AUDIT_STORE_UNAVAILABLE` with HTTP 503
  when `ACCOUNT_LOCKED` audit persistence fails.
- Refresh Token reuse returns `AUDIT_STORE_UNAVAILABLE` with HTTP 503 when
  `REFRESH_TOKEN_REUSED` audit persistence fails.

Supporting tests:

- `SecurityAuditServiceTest` verifies event construction and failure conversion.
- Repository tests verify persistence for each event type if the project keeps
  slice tests for repositories.
- Existing Phase 3 and Phase 4 tests must remain passing.

## Documentation Updates

After implementation:

- Update Phase 5 rows in `docs/evidence.md` from `TODO` to `PASS` only after
  the required tests pass.
- Add command output and verification date to Phase 5 notes.
- Do not mark Phase 5 complete based only on the presence of entity and service
  code.

## Open Decisions

No blocking design decisions remain for Phase 5.

Follow-up hardening decisions:

- Whether to add a separate evidence row for unknown username credential
  failures.
- Whether to add request metadata such as IP address and User-Agent.
- Whether to add async audit delivery after synchronous evidence passes.
- Whether to audit normal events such as successful login or successful Refresh
  Token Rotation.
