# Auth Failure Module

We decided to represent authentication and account failure modes with a stable Auth Failure Module instead of raw `RuntimeException` or `IllegalArgumentException`.

**Decision**

- Authentication, token, and User-state failures should carry an `AuthFailureCode`.
- HTTP status mapping for Auth Failure responses belongs in one global handler.
- Refresh Token missing, invalid, and reused cases must be distinguishable.
- Spring Security login failures such as bad credentials, locked User, and disabled User should be converted to Auth Failure responses.
- JWT parsing, expiry, and token policy failures should be delegated to the global handler instead of writing ad hoc JSON in filters.
- Authentication-required and access-denied failures that belong to Spring Security's 401/403 flow should remain in the configured `AuthenticationEntryPoint` and `AccessDeniedHandler`.
- Generic runtime exceptions should not be used for expected authentication failure modes.

**Consequences**

- Security Scenario tests can assert a stable failure code instead of matching exception classes or localized messages.
- Refresh Token reuse can later become a Security Audit Event without changing every caller.
- Account Lock can return a stable `ACCOUNT_LOCKED` failure even before the full lock counter and Admin Recovery flow are implemented.
- The Token Store Module can report token failure modes without deciding HTTP response details.
- The exception boundary is explicit: not every security failure goes through `GlobalExceptionHandler`, but filters should not invent one-off response formats.

**Implementation Notes**

- `AuthFailureCode` owns the stable code names and their HTTP status.
- `AuthFailureException` carries one `AuthFailureCode` and a user-facing message.
- `GlobalExceptionHandler` converts both `AuthFailureException` and Spring Security `AuthenticationException` subclasses into `AuthFailureResponse`.
- `ExceptionHandlerFilter` delegates JWT and token-policy exceptions through `HandlerExceptionResolver` so `GlobalExceptionHandler` can produce the standard response.
- `CustomAuthenticationEntryPoint` owns unauthenticated access responses.
- `CustomAccessDeniedHandler` owns authenticated-but-forbidden access responses.
- Existing raw exceptions should be replaced incrementally when they represent expected Auth Failure modes.
