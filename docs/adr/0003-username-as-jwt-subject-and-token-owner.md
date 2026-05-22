# Username as JWT Subject and Token Owner

We decided to use username as the JWT Subject and as the Redis token owner key, represented by `RT:{username}`. This matches the current `JwtTokenProvider`, `AuthServiceImpl`, and Step 4-A handover, and avoids an extra database lookup before finding the active Refresh Token.

**Considered Options**

- `userId`: stable database identity, but requires mapping from JWT subject or changing token claims and tests.
- `username`: already the JWT subject in the code and sufficient while username remains unique and immutable for authentication.

**Consequences**

Username changes would be a token identity migration, not a simple profile edit. If the project later supports username changes or account linking, this ADR should be revisited before adding those flows.
