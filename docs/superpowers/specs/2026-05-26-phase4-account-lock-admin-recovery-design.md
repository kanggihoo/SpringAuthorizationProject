# Phase 4 Account Lock & Admin Recovery Design

## Purpose

Phase 4 proves that repeated credential failure can disable a User's authentication state, and that only ADMIN recovery can restore it. The evidence source of truth remains `docs/evidence.md`.

This design keeps Phase 4 focused on Account Lock and Admin Recovery. IP-based throttling, login delay, CAPTCHA, and Gmail-based self-service unlock are follow-up hardening items.

## Scope

In scope:

- Count login failures for an existing username in Redis.
- Lock the User in DB when the failure threshold is reached.
- Reject login for a locked User.
- Keep rejecting Protected API access for a locked User even when the Access Token is still valid.
- Allow ADMIN to unlock the User.
- Deny USER access to the unlock endpoint.

Out of scope:

- IP-based abuse throttling with `auth:login:fail:ip-user:{ip}:{username}`.
- Gmail or email-code based self-service unlock.
- Login delay, CAPTCHA, device reputation, or Security Audit Event persistence.

## Policy

Account Lock counter:

- Redis key: `auth:login:fail:user:{username}`
- Threshold: 5 failed login attempts
- Window: 30 minutes
- TTL behavior: set TTL only when the counter is first created
- Redis command behavior: increment and first TTL setup run atomically with Lua
- Final lock state: `users.accountNonLocked=false` in DB

Response policy:

- Attempts 1 through 4 return `BAD_CREDENTIALS` with HTTP 401.
- The 5th failed attempt locks the User and returns `ACCOUNT_LOCKED` with HTTP 423.
- Already locked Users return `ACCOUNT_LOCKED` with HTTP 423.
- Unknown username and wrong password must not expose different external responses.
- Failed login responses must not expose remaining attempts.

Recovery policy:

- ADMIN unlock sets `users.accountNonLocked=true`.
- ADMIN unlock deletes `auth:login:fail:user:{username}`.
- USER access to `/admin/**` remains forbidden by Spring Security.

## Components

### User

Add domain methods to `User` instead of opening public setters:

- `lock()`
- `unlock()`

These methods are the only application-level write path for `accountNonLocked`.

### LoginFailureRedisRepository

Create `org.example.repository.LoginFailureRedisRepository`.

Responsibilities:

- Build the account failure key.
- Increment a failure counter.
- Set TTL on first failure in the same Redis Lua script as the increment.
- Delete the account failure counter.

It owns Redis mechanics only. It should not decide whether a User should be locked.

### LoginFailureCounter

Create `org.example.security.account.LoginFailureCounter`.

Responsibilities:

- Own the Account Lock threshold and window.
- Record a failed login and return whether the threshold has been reached.
- Clear a username's failure counter after successful login or ADMIN unlock.

Recommended constants:

- `LOCK_THRESHOLD = 5`
- `LOCK_WINDOW = Duration.ofMinutes(30)`

### AuthServiceImpl

Extend login flow to enforce Account Lock:

1. Look up the User by username.
2. If the User does not exist, fail with credential failure without creating an account lock counter.
3. If the User is locked, throw an account locked failure before token issuing.
4. Authenticate with `AuthenticationManager`.
5. On success, clear the username failure counter and issue tokens.
6. On bad credentials, record the failure.
7. If the counter reaches 5, lock the User and return `ACCOUNT_LOCKED`.
8. Otherwise return `BAD_CREDENTIALS`.

`TokenLifecycleService` remains focused on token issue, rotation, logout, and blacklist policy. It must not own Account Lock state.

### AdminService and AdminController

Create:

- `org.example.service.AdminService`
- `org.example.service.AdminServiceImpl`
- `org.example.controller.AdminController`

Endpoint:

- `POST /admin/users/{username}/unlock`

Admin unlock flow:

1. Find User by username.
2. Unlock the User.
3. Clear `auth:login:fail:user:{username}`.
4. Return a simple success response.

`SecurityConfig` already restricts `/admin/**` to `ROLE_ADMIN`, so the controller should rely on the configured authorization boundary.

## Data Flow

Failed login before threshold:

```text
POST /login
-> AuthController
-> AuthServiceImpl
-> UserRepository.findByUsername(username)
-> AuthenticationManager.authenticate(...)
-> BadCredentialsException
-> LoginFailureCounter.recordFailure(username)
-> count < 5
-> BAD_CREDENTIALS / 401
```

Failed login at threshold:

```text
POST /login
-> AuthServiceImpl
-> BadCredentialsException
-> LoginFailureCounter.recordFailure(username)
-> count == 5
-> User.lock()
-> ACCOUNT_LOCKED / 423
```

Successful login:

```text
POST /login
-> AuthServiceImpl
-> AuthenticationManager.authenticate(...)
-> LoginFailureCounter.clear(username)
-> TokenLifecycleService.issue(...)
```

Admin unlock:

```text
POST /admin/users/{username}/unlock
-> AdminController
-> AdminServiceImpl.unlockUser(username)
-> User.unlock()
-> LoginFailureCounter.clear(username)
```

Protected API with existing Access Token:

```text
Bearer Access Token
-> JwtAuthenticationFilter
-> AuthenticatedUserServiceImpl.findActiveUserByJwtSubject(...)
-> accountNonLocked=false
-> no SecurityContext authentication
```

## Failure Handling

Use existing Auth Failure vocabulary:

- Wrong credentials: `BAD_CREDENTIALS`
- Locked User: `ACCOUNT_LOCKED`
- Missing User during admin unlock: `USER_NOT_FOUND`

Redis failures are not Phase 4 evidence. The implementation should still fail closed for security-critical failure counter operations rather than silently allowing unlimited attempts.

## Tests And Evidence

Required Phase 4 evidence:

- `AuthServiceImplTest.login_locksUser_afterFiveFailures`
  - Existing User fails login 5 times within the counter window.
  - User is locked in DB.
  - 5th failure returns account locked behavior.

- `AuthServiceImplTest.login_throwsLockedException_whenUserLocked`
  - Locked User cannot log in even with the correct password.
  - Token issuing is not called.

- `JwtAuthenticationFilterTest.doFilter_doesNotAuthenticate_whenUserIsLocked`
  - Existing valid Access Token cannot authenticate a locked User.
  - This evidence already passes and should remain passing.

- `AdminServiceImplTest.unlockUser_unlocksLockedUser`
  - ADMIN recovery unlocks the User.
  - Account lock Redis counter is deleted.

- `AdminControllerSecurityTest.userCannotUnlockAccount`
  - ROLE_USER receives 403 when calling the admin unlock endpoint.
  - Admin service is not invoked.

Supporting tests:

- `LoginFailureRedisRepositoryTest`
  - Increment creates the Redis key.
  - First increment sets TTL.
  - Clear deletes the key.

- `LoginFailureCounterTest`
  - Attempts below threshold do not lock.
  - Threshold attempt reports lock required.

## Documentation Updates

`docs/evidence.md` has been updated so Phase 4 states:

- username 기준 30분 안에 로그인 5회 실패
- DB 계정 잠금
- Redis account counter
- Gmail self-service unlock and IP throttling as follow-up hardening

## Open Decisions

No open implementation decisions remain for Phase 4. Follow-up hardening can be designed later as a separate phase or step.
