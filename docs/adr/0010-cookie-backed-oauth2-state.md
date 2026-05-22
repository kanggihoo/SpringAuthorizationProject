# Cookie-backed OAuth2 State

We decided to keep OAuth2 login stateless by storing the OAuth2 authorization request and `state` verification data in HttpOnly cookies instead of Spring Security's default HTTP session. This preserves the service-wide `SessionCreationPolicy.STATELESS` boundary while still defending the OAuth2 callback against CSRF/state mismatch attacks.

**Consequences**

OAuth2 login must not reintroduce `HttpSession` as a state store. The cookie-backed authorization request repository is part of the authentication boundary and must clear state cookies after success or failure.
