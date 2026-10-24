# auth

Owns: accounts, sign-in by password or Google, the two-step sign-up, and the caller's own
profile (`/v1/users/me`).

Entry points: `service/RegistrationService.kt`, `service/GoogleAuthService.kt`,
`service/OtpService.kt`.

## Rules

- A sign-up creates no `User` until `RegistrationService.complete`. Until then there is only
  a `PendingRegistration`, and its id is the only credential the browser holds. The
  email-code path and the Google path both finish in `complete`.
- A Google account is matched by its Google id (`sub`), never by email. Sign-in with a
  Google account whose email already belongs to a user is refused, never joined.
- `GoogleAuthService.connect` is the only place a Google id is linked to a user, and the
  only way a user's email changes. The profile edits the display name and nothing else.
- A password is optional. An account created through Google has none and cannot sign in at
  `/v1/auth/login`.
- `OtpService.verify` commits in its own transaction so a wrong guess still counts against
  the attempt limit. Call it before any other write in the caller's transaction.
- A one-time code is sent inside the transaction that stores it, so a failed send rolls the
  code back. A notice about something already done (email changed) is sent after the commit
  and a failure is only logged.
- A caller never logs a one-time code. Only the `log` mail provider prints it.
- `OtpPurpose` values are listed in a database check constraint. Adding or renaming one
  needs a migration.
- The username is the JWT subject and is immutable.
- A new public route here must be added to `src/main/resources/security.json`.
  `google:connect` is deliberately not public: it changes the caller's own account.

## Gotchas

- `email_otps` and `pending_registrations` are hard-deleted. `job/RegistrationCleanupJob.kt`
  removes expired rows every ten minutes.
- `users` is soft-deleted on delete but has no `@Where` filter, and nothing deletes a user
  today.
- `POST /v1/auth/refresh-token` is an empty stub.
- Granting the teacher role is admin-only (`UserService.grantTeacherRole`). Nothing in the
  app creates an admin; that is done in the database by hand.

## Verify

`./gradlew test --tests "com.example.lib4gz.auth.*"`
