# Authenticated User Module for Account Lock

We decided to place Local User and OAuth2 User principal adaptation behind an Authenticated User Module. The module owns the service-facing representation of the authenticated User used by Protected API requests.

**Decision**

- Local and OAuth2 authentication adapters should expose the same Authenticated User language to callers.
- Protected API authentication must verify the Authenticated User state after validating the Access Token.
- Account Lock and disabled User checks belong to the Authenticated User Module, not to the Token Store Module.
- An unexpired Access Token is necessary but not sufficient for Protected API access.
- JwtAuthenticationFilter should delegate Authenticated User loading and state checks instead of directly encoding Account Lock policy.

**Rejected Options**

- Putting Account Lock checks directly in JwtAuthenticationFilter would work quickly, but it would spread User state policy into the filter implementation.
- Putting Account Lock checks into TokenLifecycleService would mix Token Store state with User state and make the Token Store Module know too much about account policy.

**Consequences**

- Account Lock can invalidate Protected API access even when the caller still has an unexpired Access Token.
- Local User and OAuth2 User adapters can vary behind the same seam while controllers and filters depend on the Authenticated User interface.
- Role conversion, enabled checks, and Account Lock checks become one test surface instead of repeated caller knowledge.
- The Token Store Module remains focused on Refresh Token Rotation, logout revocation, and Logout Blacklist state.

**Implementation Notes**

- Introduce an Authenticated User interface that exposes the JWT Subject, User id, nickname, enabled state, Account Lock state, and Roles.
- Let the existing Local and OAuth2 principal adapters satisfy the Authenticated User interface before adding more abstraction.
- Introduce a loader or resolver such as `AuthenticatedUserService.loadActiveUserByJwtSubject(...)` for Access Token authentication.
- The loader must reject disabled or locked Users before a Spring Security Authentication is written to the SecurityContext.
- Keep One-time Code Exchange and Redis Failure Policy outside this ADR.
