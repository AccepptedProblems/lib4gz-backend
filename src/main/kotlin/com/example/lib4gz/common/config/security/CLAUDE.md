# security

Owns: who may reach an endpoint, and how the caller's identity gets to a controller.

## Rules

- `src/main/resources/security.json` is the whole access policy: `permittedEndpoints` (no
  token needed), `authorizedEndpoints` (path to roles) and CORS. `SecurityConfig` reads it
  at startup. To open or restrict a route, edit the JSON. Do not add `requestMatchers` in
  Kotlin. Anything not listed needs a valid token.
- The token arrives in the `access_token` header, not `Authorization`.
- `JwtAuthFilter` removes any `X-User-Id` header a client sends and sets it from the token.
  Controllers take the caller from `@RequestHeader(PZRequestHeader.USER_ID)` and never
  parse the token.
- A permitted endpoint called without a token has no `X-User-Id`, so a controller method
  that declares that header cannot be made public.
- The filter skips itself, header removal included, for any path containing `/v1/register`
  or `/v1/login`. No route matches today (the real ones are under `/v1/auth/`). Never add a
  route whose path contains either string.
- The filter does not reject a bad or expired token. The request continues without a caller
  and Spring Security answers 401 unless the path is permitted.
- `common/config/common/OpenApiConfig.kt` hides `X-User-Id` from the generated API document
  and shows `access_token` instead.

## True today (2026-10-01)

- `authorizedEndpoints` is empty: every role check is in service code.
- CORS allows every origin.
- An access token lasts one day. Refresh is not implemented.
- With `GOOGLE_CLIENT_ID` unset, every Google token is rejected with 401.
