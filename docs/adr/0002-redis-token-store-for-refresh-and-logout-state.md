# Redis Token Store for Refresh and Logout State

We decided to use Redis as the Token Store for active Refresh Tokens and logout Access Token blacklist entries. JWT remains the API credential format, but Redis holds the state that cannot be represented safely by stateless JWT alone: Refresh Token replacement and immediate logout revocation.

**Considered Options**

- PostgreSQL Refresh Token rows: durable and familiar, but TTL cleanup and frequent token checks are a poor fit.
- Stateless JWT only: simpler, but cannot immediately revoke a logged-out Access Token.
- Redis Token Store: matches TTL-heavy token state and keeps logout/refresh checks cheap.

**Consequences**

Redis becomes part of the authentication availability boundary, so Phase 9 must define the Redis Failure Policy. Local login, refresh, and OAuth2 success handling now issue tokens through `TokenLifecycleService`, which stores the active Refresh Token in `TokenRedisRepository`. The PostgreSQL `RefreshToken` model is no longer part of the active token policy.

**Implementation Notes**

- `TokenLifecycleService` is the Token Store Module interface used by callers.
- `TokenLifecycleService.issue(...)` issues an Access Token and Refresh Token, then stores the active Refresh Token in Redis.
- `TokenLifecycleService.rotate(...)` verifies the supplied Refresh Token against the Redis value before replacing it.
- `TokenLifecycleService.logout(...)` removes the active Refresh Token and adds the current Access Token to the Logout Blacklist.
- `JwtAuthenticationFilter` must check `TokenLifecycleService.isAccessTokenAllowed(...)` before authenticating a Protected API request.
- A Blacklisted Access Token must not authenticate a Protected API request even if its JWT signature and expiry are valid.
