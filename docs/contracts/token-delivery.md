# Token Delivery Contract

This contract records the browser-facing token behavior for the next frontend implementation.

## Storage

| Token | Browser storage | JavaScript access | Notes |
| --- | --- | --- | --- |
| Access Token | App memory only | Yes | Do not persist in `localStorage` or `sessionStorage`. |
| Refresh Token | HttpOnly cookie | No | Set, rotated, and expired by the backend. |

## Local Login

`POST /login`

- Request body: username and password.
- Response body: `accessToken`, `tokenType`.
- Response cookie: `Refresh-Token`.
- Frontend action: store `accessToken` in memory only.

## Refresh

`POST /refresh`

- Request cookie: `Refresh-Token`.
- Response body: new `accessToken`, `tokenType`.
- Response cookie: rotated `Refresh-Token`.
- Frontend action: replace the in-memory Access Token.

## Protected API Requests

- Send `Authorization: Bearer {accessToken}`.
- Do not send Access Tokens through query parameters, URL fragments, or persistent browser storage.

## Logout

`POST /logout`

- Request header: `Authorization: Bearer {accessToken}` when available.
- Backend action: remove active Refresh Token, blacklist Access Token, expire `Refresh-Token` cookie.
- Frontend action: clear in-memory Access Token.

## App Startup

- Start with no Access Token in memory.
- Call `POST /refresh`.
- If successful, store the returned Access Token in memory.
- If rejected, show unauthenticated state.

## OAuth2 Login

Current interim behavior:

- OAuth2 success redirects with `#accessToken=...`.
- Refresh Token is set as an HttpOnly cookie.

Target behavior:

- OAuth2 success redirects with a one-time code, not an Access Token.
- Frontend exchanges the code through a backend endpoint.
- Backend returns `accessToken`, `tokenType` in the response body.
- Frontend stores the Access Token in memory only.
