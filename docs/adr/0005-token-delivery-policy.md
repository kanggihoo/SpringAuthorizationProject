# Token Delivery Policy

We decided to separate Token Delivery from Token Store. Token Store owns server-side token state, while Token Delivery owns how tokens move between the service and a browser client.

**Decision**

- Access Tokens are browser-readable credentials and must not be stored in `localStorage` or `sessionStorage`.
- The frontend stores the current Access Token only in process memory, such as app-level state.
- Refresh Tokens are delivered only through an HttpOnly cookie.
- Frontend JavaScript must not read or write the Refresh Token.
- On app startup or browser refresh, the frontend calls `POST /refresh`; if the Refresh Token cookie is valid, the service returns a new Access Token in the response body and rotates the Refresh Token cookie.
- Protected API requests use `Authorization: Bearer {accessToken}`.
- Logout calls `POST /logout`, clears frontend memory, expires the Refresh Token cookie, removes the active Refresh Token from the Token Store, and blacklists the current Access Token.

**OAuth2 Delivery**

The current OAuth2 redirect still uses a URL fragment that contains the Access Token. This is an interim delivery mechanism and should be replaced with One-time Code Exchange:

- OAuth2 success sets the Refresh Token HttpOnly cookie.
- OAuth2 success redirects to the frontend with a short-lived one-time code, not an Access Token.
- The frontend exchanges that code through a backend endpoint.
- The backend consumes the code once and returns the Access Token in the response body.
- The frontend stores that Access Token only in memory.

**Consequences**

- A page reload loses the in-memory Access Token by design.
- The browser session can recover by calling `POST /refresh` because the Refresh Token cookie is HttpOnly and sent by the browser.
- XSS can still issue requests as the current page, but it cannot directly read a persisted Access Token from browser storage.
- Token Delivery cookie attributes and bearer-token extraction should move behind a Token Delivery Module before changing OAuth2 to One-time Code Exchange.
- CSRF policy must be revisited before moving Access Tokens into cookies; the current policy keeps Access Tokens in the `Authorization` header.

**Implementation Notes**

- `TokenDeliveryService` owns Refresh Token cookie creation, expiration, and reading.
- `TokenDeliveryService` owns Bearer Access Token extraction from the `Authorization` header.
- `AuthController`, `OAuth2AuthenticationSuccessHandler`, and `JwtAuthenticationFilter` use `TokenDeliveryService` instead of duplicating delivery policy.
- The OAuth2 URL fragment delivery remains unchanged until the One-time Code Exchange work starts.
