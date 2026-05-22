# Redis Failure Policy

We decided to treat Redis Token Store failures as explicit Auth Failure outcomes instead of leaking Redis exceptions to callers.

**Decision**

- Redis-backed Token Store operations are required for token issue, Refresh Token Rotation, logout revocation, and Logout Blacklist checks.
- Token issue fails closed when the active Refresh Token cannot be stored.
- Refresh Token Rotation fails closed when the active Refresh Token cannot be read or replaced.
- Protected API authentication fails closed when the Logout Blacklist cannot be checked.
- Logout expires the browser Refresh Token cookie even when Redis revocation fails, but the server-side revocation failure is still returned as `TOKEN_STORE_UNAVAILABLE`.
- Redis availability failures are exposed through `AuthFailureCode.TOKEN_STORE_UNAVAILABLE` with HTTP 503.

**Consequences**

- A Redis outage can reduce authentication availability, but it does not silently bypass Refresh Token Rotation or logout revocation policy.
- Callers use Token Store policy outcomes instead of knowing Redis exception types.
- Security Scenario tests can assert a stable Auth Failure code for Token Store outages.

**Implementation Notes**

- `RedisFailurePolicy` is an internal Module used by `TokenLifecycleServiceImpl`.
- `TokenLifecycleServiceImpl` wraps Redis reads and writes through `RedisFailurePolicy`.
- `AuthController.logout(...)` expires the Refresh Token cookie in a `finally` block so browser Token Delivery cleanup is not skipped by Redis failure.
