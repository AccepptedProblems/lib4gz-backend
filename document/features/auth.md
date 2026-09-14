# Authentication Feature

## 1. Overview

Authentication feature handling login, account registration, Google sign-in, the caller's own profile, and JWT token issuance.

- **Login** takes a username **or** an email address plus a password and returns a JWT access token.
- **Registration** has no username/password form. There are two entry points, both ending on the same "complete" step:
  1. **Email + one-time code (OTP):** the person proves an email address with a 6-digit code, then picks a username, display name, and password.
  2. **Google:** the browser posts a Google ID token; the server either logs an existing account in or starts a registration that finishes with a username and display name (no password).
- **Profile** (`/v1/users/me`) lets the caller read their account, change their display name, and change or confirm their email with a one-time code.

`/v1/auth/login`, `/v1/auth/register/**`, and `/v1/auth/google` are listed in `security.json` `permittedEndpoints` (no JWT). Every `/v1/users/**` endpoint requires the `access_token` header.

---

## 2. Models

> Full model definitions: [models/auth.yaml](../models/auth.yaml)

### Entities
- **User** — `users` table, soft delete via `@SQLDelete` (no `@Where`). Now has `pendingEmail` and `googleId` (unique); `email` is `var`.
- **EmailOtp** — `email_otps` table, **hard-deleted**. One live code per `(email, purpose)` (unique constraint).
- **PendingRegistration** — `pending_registrations` table, **hard-deleted**. A sign-up that proved its email but has no username yet. Its id (`reg_…`) is the only credential the browser holds.

### Enums
- **UserRole**: ADMIN, USER, TEACHER
- **UserStatus**: ACTIVE, INACTIVE, BANNED
- **OtpPurpose**: REGISTER, EMAIL_CHANGE, LOGIN (reserved), PASSWORD_RESET (reserved)

### DTOs
- **UserResponse** — id, username, email, name, avatarUrl, role, emailVerified, pendingEmail
- **UpdateMeRequest** — name?, email?
- **MeCoursesResponse** — enrolled (CourseResponse[]), created (CourseResponse[])
- **LoginReq** — username (username or email), password
- **Token** — token, expiredAt
- **LoginResponse** — user (UserResponse) + accessToken (Token)
- **StartEmailRegistrationRequest** — email
- **VerifyEmailRegistrationRequest** — email, code
- **CompleteRegistrationRequest** — registrationId, username, name?, password?
- **OtpSentResponse** — email, cooldownSeconds
- **RegistrationStartedResponse** — registrationId, email, name?, avatarUrl?, requiresPassword
- **VerifyEmailCodeRequest** — code
- **GoogleSignInRequest** — idToken
- **GoogleSignInResponse** — login (LoginResponse?) + registration (RegistrationStartedResponse?); exactly one is non-null

`UserCreationRequest` was removed together with `POST /v1/auth/register`.

### Mappers
- **UserMapper** — `User.toResponse(): UserResponse`. `name` falls back to `username` when blank; `role` is the single highest global role, ADMIN > TEACHER > USER.
- **RegistrationMapper** — `PendingRegistration.toStartedResponse(): RegistrationStartedResponse`; `requiresPassword = googleId == null`.

---

## 3. Repositories

**Package:** `com.example.lib4gz.auth.repo`

```kotlin
interface UserRepo : JpaRepository<User, String> {
    fun findByUsername(username: String): User?
    fun findByEmail(email: String): User?
    fun findByEmailIgnoreCase(email: String): User?
    fun findByGoogleId(googleId: String): User?
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun existsByEmailIgnoreCase(email: String): Boolean
    fun existsByEmailIgnoreCaseAndIdNot(email: String, id: String): Boolean
}

interface EmailOtpRepo : JpaRepository<EmailOtp, String> {
    fun findByEmailAndPurpose(email: String, purpose: OtpPurpose): EmailOtp?
    @Transactional @Modifying fun deleteByExpiresAtBefore(now: Long): Long
}

interface PendingRegistrationRepo : JpaRepository<PendingRegistration, String> {
    @Transactional @Modifying fun deleteByEmail(email: String): Long
    @Transactional @Modifying fun deleteByExpiresAtBefore(now: Long): Long
}
```

Derived `deleteBy…` methods carry `@Transactional @Modifying` because they are called from inside `Mono.fromCallable` bodies, where the service's own transaction does not reach (see §4 "Transactions").

---

## 4. Services

**Package:** `com.example.lib4gz.auth.service`

```kotlin
interface AuthService {
    fun auth(loginReq: LoginReq): Mono<LoginResponse>
}

interface RegistrationService {
    fun startEmail(email: String): Mono<OtpSentResponse>          // calling again is the resend
    fun verifyEmail(email: String, code: String): Mono<RegistrationStartedResponse>
    fun complete(request: CompleteRegistrationRequest): Mono<LoginResponse>
}

interface GoogleAuthService {
    fun signIn(idToken: String): Mono<GoogleSignInResponse>
}

interface UserService {
    fun grantTeacherRole(requesterId: String, targetUserId: String): Mono<UserResponse>
    fun getMe(userId: String): Mono<UserResponse>
    fun updateMe(userId: String, request: UpdateMeRequest): Mono<UserResponse>
    fun getMyCourses(userId: String): Mono<MeCoursesResponse>
    fun verifyEmail(userId: String, code: String): Mono<UserResponse>
    fun resendEmailCode(userId: String): Mono<OtpSentResponse>
}

// Blocking; called inside the callers' transaction blocks
interface OtpService {
    fun issue(email: String, purpose: OtpPurpose)
    fun verify(email: String, purpose: OtpPurpose, code: String)
}
```

`MeService` was folded into `UserService`. `GoogleTokenVerifier` (`common/config/security/`) and `EmailSender` (`common/mail/`) are described in [security.md](../infrastructure/security.md) and [configuration.md](../infrastructure/configuration.md).

### Transactions

- Services return `Mono.fromCallable { … }.subscribeOn(Schedulers.boundedElastic())`. A class-level `@Transactional` only wraps building the `Mono`, not the callable, so multi-write bodies run inside `transactionTemplate.execute { … }` (injected `TransactionTemplate`). A failure (including a 503 from the mail sender) rolls back every write in the block.
- `OtpService.issue` uses the default `@Transactional` and joins the caller's transaction.
- `OtpService.verify` is `@Transactional(propagation = REQUIRES_NEW, noRollbackFor = [BusinessException::class])`, so a wrong guess commits `attempts++` even though it throws and the outer block rolls back. Callers call `verify` before any other write.

### Login — `AuthServiceImpl.auth()`

- Trims `loginReq.username`. If it contains `@`, looks up `findByEmailIgnoreCase`, else `findByUsername`.
- Unknown user, a user with no password (Google-only account), or a password mismatch → 401 `"Invalid username or password"` (`ResponseStatusException`).
- Returns `LoginResponse(user.toResponse(), jwtProvider.generateAccessToken(user))`.

### One-time codes — `OtpServiceImpl`

- Code: `IdGenerator.generateNumericCode(6)`. Stored hash: SHA-256 hex of `"$pepper:$email:$purpose:$code"`, pepper = `application.security.jwt.secret-key`.
- `issue(email, purpose)`: an existing row younger than `application.otp.resend-cooldown-ms` (60 s) → 400 `"Please wait a minute before requesting another code"`. Otherwise the existing row is **overwritten in place** (new hash, `attempts = 0`, `createdAt = now`, `expiresAt = now + ttl-ms` (10 min)), or a new row is inserted. The row is not deleted and re-inserted: Hibernate flushes inserts before deletes, which would violate the `(email, purpose)` unique constraint. The mail is sent inside the transaction.
- `verify(email, purpose, code)`: no row → 400 `"Code expired or not found. Request a new one"`; expired → delete + same message; `attempts >= max-attempts` (5) → delete + 400 `"Too many attempts. Request a new code"`; mismatch (constant-time compare) → `attempts++` + 400 `"Invalid code"`; match → delete the row.

### Email registration — `RegistrationServiceImpl`

- `startEmail(email)`: `trim().lowercase()`; no `@` → 400 `"Invalid email"` (`ValidationException`); an account with that email (case-insensitive) → 409 `"Email already in use"`; `otpService.issue(email, REGISTER)`. Returns `OtpSentResponse(email, cooldownSeconds = resend-cooldown-ms / 1000)`.
- `verifyEmail(email, code)`: normalize; `otpService.verify(email, REGISTER, code)`; `pendingRepo.deleteByEmail(email)`; save `PendingRegistration(email, emailVerified = true, expiresAt = now + application.registration.pending-ttl-ms (15 min))`. Returns `RegistrationStartedResponse` with `requiresPassword = true`.
- `complete(request)`:
  - Pending row missing → 404 `"Registration not found or expired"`; expired → delete + same 404.
  - Username trimmed; fewer than 3 chars or not `^[a-zA-Z0-9]+$` → 400 `"Username must be at least 3 letters or digits"`.
  - Email path (`googleId == null`): password missing or shorter than 8 → 400 `"Password must be at least 8 characters"`; BCrypt-encoded. Google path: password ignored, stored as `null`.
  - In one transaction: username taken → 409 `"Username already taken"`; email taken meanwhile → 409 `"Email already in use"`; save `User(email, username, name = name.trim() or username, password, googleId, avatarUrl, emailVerified, roles = [USER])`; delete the pending row.
  - Returns `LoginResponse` (the person is logged in immediately).

### Google sign-in — `GoogleAuthServiceImpl.signIn()`

1. `GoogleTokenVerifier.verify(idToken)` runs **outside** the transaction (it may fetch Google's keys). Invalid token → 401 `"Invalid Google token"`.
2. Google says the email is not verified → 401 `"Google account email is not verified"`.
3. Inside one transaction, with the Google email lowercased:
   - **Match by `google_id`** → `GoogleSignInResponse(login = …)`.
   - Else **match by email** (case-insensitive):
     - account email **verified** → link: set `googleId` (and `avatarUrl` from the Google picture if the account has none), save, `login`.
     - account email **not verified** → 400 `"Sign in with your password and verify your email in Profile first"` (`BusinessException`).
     - account already linked to a **different** Google id → 400 `"This account is linked to a different Google account"` (never re-linked silently).
   - **No match** → `deleteByEmail`, save `PendingRegistration(email, googleId, name, avatarUrl = picture, emailVerified = true, 15 min)` → `GoogleSignInResponse(registration = …)` with `requiresPassword = false`. The client then calls `/v1/auth/register/complete`.

### Profile — `UserServiceImpl`

- `getMe(userId)`: `toResponse()`; missing → 404 `"User not found with id: …"`.
- `updateMe(userId, request)` (one transaction):
  - `name` present: trimmed; blank → 400 `"Name must not be blank"`.
  - `email` present: trimmed and lowercased; blank → 400 `"Email must not be blank"`; no `@` → 400 `"Invalid email"`. Then:
    - same as the current email → clears `pendingEmail`;
    - same as the current `pendingEmail` → no change, no new code (use resend);
    - used by another account → 409 `"Email already in use"`;
    - otherwise `pendingEmail = email` and `otpService.issue(email, EMAIL_CHANGE)` (cooldown → 400; mail failure → 503 and `pendingEmail` rolled back).
  - Returns the saved `UserResponse` (with `pendingEmail`).
- `verifyEmail(userId, code)`: target = `pendingEmail ?: email`; `otpService.verify(target, EMAIL_CHANGE, code)`; if a pending email exists, re-check it is not used by another account (409 `"Email already in use"`) and swap it into `email`, clearing `pendingEmail`; set `emailVerified = true`.
- `resendEmailCode(userId)`: no pending email and already verified → 400 `"Email is already verified"`; otherwise `otpService.issue(pendingEmail ?: email, EMAIL_CHANGE)` → `OtpSentResponse`. This is also how an existing unverified account gets its first code.
- `getMyCourses(userId)`: zips `courseService.listEnrolledCourses` and `listUserCreatedCourses` into `MeCoursesResponse`.
- `grantTeacherRole(requesterId, targetUserId)`: requester must be ADMIN (403); target missing → 404; already TEACHER → 400.

### Cleanup — `auth/job/RegistrationCleanupJob`

`@Scheduled(fixedDelay = 600_000)` `@Transactional sweep()` deletes expired rows from `email_otps` and `pending_registrations` every 10 minutes and logs the counts when either is non-zero. Scheduling is enabled by `@EnableScheduling` on `Lib4gzApplication`.

---

## 5. Controllers

**Package:** `com.example.lib4gz.auth.controller`

### AuthController — `@RequestMapping("/v1/auth")` (public)

| Method | Path                           | Function                | Request Body                   | Response                             | Errors |
|--------|--------------------------------|-------------------------|--------------------------------|--------------------------------------|--------|
| POST   | /v1/auth/login                 | login                   | LoginReq                       | Mono\<LoginResponse\>                | 401 |
| POST   | /v1/auth/google                | googleSignIn            | GoogleSignInRequest            | Mono\<GoogleSignInResponse\>         | 401 invalid token / unverified Google email; 400 unverified existing account |
| POST   | /v1/auth/register/email        | startEmailRegistration  | StartEmailRegistrationRequest  | Mono\<OtpSentResponse\>              | 400 invalid email / cooldown; 409 email in use; 503 mail |
| POST   | /v1/auth/register/email/verify | verifyEmailRegistration | VerifyEmailRegistrationRequest | Mono\<RegistrationStartedResponse\>  | 400 invalid / expired / too many attempts |
| POST   | /v1/auth/register/complete     | completeRegistration    | CompleteRegistrationRequest    | Mono\<LoginResponse\>                | 404 registration; 400 username / password rules; 409 username / email |
| POST   | /v1/auth/refresh-token         | refreshToken            | -                              | Unit                                 | Not implemented (not in `permittedEndpoints`) |

### UserController — `@RequestMapping("/v1/users")` (JWT required)

All `/me` handlers take `@RequestHeader(PZRequestHeader.USER_ID)`. Spring matches the literal `me` segment before `/{userId}`.

| Method | Path                          | Function         | Request Body            | Response                    | Errors |
|--------|-------------------------------|------------------|-------------------------|-----------------------------|--------|
| GET    | /v1/users/me                  | getMe            | -                       | Mono\<UserResponse\>        | 404 |
| PATCH  | /v1/users/me                  | updateMe         | UpdateMeRequest         | Mono\<UserResponse\>        | 400 blank name / invalid email / cooldown; 409 email in use; 503 mail |
| POST   | /v1/users/me/email/verify     | verifyEmail      | VerifyEmailCodeRequest  | Mono\<UserResponse\>        | 400 invalid / expired / too many attempts; 409 email taken meanwhile |
| POST   | /v1/users/me/email/resend     | resendEmailCode  | `{}`                    | Mono\<OtpSentResponse\>     | 400 already verified / cooldown; 503 mail |
| GET    | /v1/users/me/courses          | getMyCourses     | -                       | Mono\<MeCoursesResponse\>   | - |
| POST   | /v1/users/{userId}/grant-teacher | grantTeacherRole | -                    | Mono\<UserResponse\>        | 403 not ADMIN; 404; 400 already TEACHER |

`MeController` (`/v1/me/courses`) was removed; use `GET /v1/users/me/courses`.

Error status mapping: `ValidationException` / `BusinessException` → 400, `ResourceNotFoundException` → 404, `ResponseStatusException` → its own status (401, 409, 503). See [error-handling.md](../infrastructure/error-handling.md).

---

## 6. Business Rules

1. **Login by username or email:** the `username` field of `LoginReq` is treated as an email when it contains `@` (case-insensitive lookup), otherwise as a username. Every failure, including an account without a password, returns 401 `"Invalid username or password"`.
2. **No open registration:** accounts are created only by `POST /v1/auth/register/complete`, after the email was proven by a code or by Google. An unfinished registration cannot log in and can be restarted; a new start for the same email replaces the older pending row.
3. **Username rules:** at least 3 characters, letters and digits only, unique (409). The username is chosen only at registration and cannot be changed later.
4. **Password rules:** at least 8 characters on the email path, BCrypt-encoded. Accounts created through Google have no password and can only sign in with Google.
5. **Email uniqueness:** case-insensitive across all users (409). New emails are stored lowercased. Soft-deleted users still reserve their username and email.
6. **One-time codes:** 6 digits, stored hashed, valid 10 minutes, 5 wrong attempts invalidate the code, 60-second cooldown between sends, one live code per email and purpose.
7. **Pending registrations:** expire after 15 minutes; deleted on completion; expired rows of both tables are swept every 10 minutes.
8. **Google linking:** match by Google id, then by an email the account has **verified** (the Google id is then linked). An existing account whose email is not verified must log in with its password and verify the email in Profile first. An account already linked to another Google id is never re-linked. Existing accounts start unverified; there is no data migration.
9. **Email change:** the new address is held in `pendingEmail` until its code is confirmed, then swapped in with `emailVerified = true`. The email can never be cleared. An email change does not invalidate issued tokens.
10. **UserResponse:** `name` falls back to `username`; `role` is ADMIN > TEACHER > USER. The JWT `user` claim embeds `UserResponse`, so a name in an old token can be stale; clients read `GET /v1/users/me`.
11. **Soft delete:** `User` uses `@SQLDelete` with inline SQL timestamp `(EXTRACT(EPOCH FROM NOW()) * 1000)::bigint` and has no `@Where(clause = "deleted_at IS NULL")`. `email_otps` and `pending_registrations` are hard-deleted (transient credentials).
12. **Default roles and status:** new users get `mutableSetOf(UserRole.USER)` and `UserStatus.ACTIVE`.
13. **TEACHER role:** the global `UserRole.TEACHER` grants course creation; users without it receive 403 on `POST /v1/courses`. This is separate from the per-course `EnrollmentRole.TEACHER`.
14. **Refresh token endpoint:** `/v1/auth/refresh-token` exists but is not implemented.

---

## 7. Tests

Plain JUnit 5 + Mockito unit tests (no Spring context, `.block()` on the `Mono`) under `src/test/kotlin/com/example/lib4gz/auth/`: `service/AuthServiceImplTest`, `service/UserServiceImplTest`, `service/OtpServiceImplTest`, `service/RegistrationServiceImplTest`, `service/GoogleAuthServiceImplTest`, `job/RegistrationCleanupJobTest`. `TransactionTemplate` is a mock whose `execute` runs the callback, so transaction behaviour itself is only checked against a real database.
