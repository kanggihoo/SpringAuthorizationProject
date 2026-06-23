# Service-owned JWT after OAuth2 Login

We decided that OAuth2 login should end by issuing the same service-owned Access Token and Refresh Token pair used by local login. This keeps authorization, refresh, logout, and future account lock checks under one service policy instead of letting downstream APIs depend directly on provider tokens.

**Consequences**

The OAuth2 Success Handler is part of the token boundary and must follow the same Token Store rules as local login. The current fragment-based Access Token redirect is an interim delivery mechanism; the roadmap's Refresh Bootstrap Delivery should replace it because URL-visible tokens are easy for client-side tooling to leak.

**Implementation Notes**

- `OAuth2AuthenticationSuccessHandler` must not store Refresh Tokens through a PostgreSQL `RefreshTokenRepository`.
- `OAuth2AuthenticationSuccessHandler` issues service-owned JWTs through `TokenLifecycleService.issue(...)`.
- OAuth2 Refresh Tokens are stored by the Redis-backed Token Store policy, the same as Local login Refresh Tokens.
- OAuth2 success should set the Refresh Token HttpOnly cookie and redirect to the frontend root `/` without placing the Access Token in the redirect URL.
- The frontend recovers the in-memory Access Token by calling `POST /refresh` during app bootstrap.
- The PostgreSQL `RefreshToken` entity and repository are no longer part of the active token policy.
- Existing deployed databases may still contain an old `refresh_tokens` table. That table is historical data only and should be removed by an explicit DB cleanup or migration when database migrations are introduced.
