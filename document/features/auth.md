# Authentication Feature

## 1. Overview

Authentication feature handling login, account registration, Google sign-in, the caller's own profile, and JWT token issuance.

- **Login** takes a username **or** an email address plus a password and returns a JWT access token.
- **Registration** has no username/password form. There are two entry points, both ending on the same "complete" step:
  1. **Email + one-time code (OTP):** the person proves an email address with a 6-digit code, then picks a username, display name, and password.
  2. **Google:** the browser posts a Google ID token; the server either logs an existing account in or starts a registration that finishes with a username and display name (no password).
- **Profile** (`/v1/users/me`) lets the caller read their account, change their display name, and change or confirm their email with a one-time code (the email change is its own request, not part of the profile update).

`/v1/auth/login`, `/v1/auth/register/**`, and `/v1/auth/google` are listed in `security.json` `permittedEndpoints` (no JWT). Every `/v1/users/**` endpoint requires the `access_token` header.

---

## 2. Models

> Full model definitions: [models/auth.yaml](../models/auth.yaml)

### Entities
- **User** — `users` table, soft delete via `@SQLDelete` (no `@Where`). Has `googleId` (unique); `email`, `emailVerified`, `googleId` and `avatarUrl` are `var` because connecting a Google account rewrites them. No `pendingEmail` any more (dropped by V3).
- **EmailOtp** — `email_otps` table, **hard-deleted**. One live code per `(email, purpose)` (unique constraint).
- **PendingRegistration** — `pending_registrations` table, **hard-deleted**. A sign-up that proved its email but has no username yet. Its id (`reg_…`) is the only credential the browser holds.

### Enums
- **UserRole**: ADMIN, USER, TEACHER
- **UserStatus**: ACTIVE, INACTIVE, BANNED
- **OtpPurpose**: REGISTER, LOGIN (reserved), PASSWORD_RESET (reserved)

> **Schema.** The purpose is stored as text in `email_otps.purpose`, guarded by the check constraint
> `email_otps_purpose_check` that lists the enum values. Adding or renaming a value needs a Flyway
> migration: V2 renamed `EMAIL_CHANGE` to `EMAIL_VERIFICATION`, V3 removed `EMAIL_VERIFICATION` again
> when the profile stopped issuing codes (see [database.md](../architecture/database.md)).

### DTOs
- **UserResponse** — id, username, email, name, avatarUrl, role, emailVerified, googleConnected (`googleId != null`)
- **UpdateMeRequest** — name?
- **MeCoursesResponse** — enrolled (CourseResponse[]), created (CourseResponse[])
- **LoginReq** — username (username or email), password
- **Token** — token, expiredAt
- **LoginResponse** — user (UserResponse) + accessToken (Token)
- **StartEmailRegistrationRequest** — email
- **VerifyEmailRegistrationRequest** — email, code
- **CompleteRegistrationRequest** — registrationId, username, name?, password?
- **OtpSentResponse** — email, cooldownSeconds
- **RegistrationStartedResponse** — registrationId, email, name?, avatarUrl?, requiresPassword
- **GoogleSignInRequest** — idToken (body of both `google:sign-in` and `google:connect`)
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
}

// Blocking; called inside the callers' transaction blocks
interface OtpService {
    val ttlMinutes: Long                                   // the configured code lifetime, in minutes
    fun issue(email: String, purpose: OtpPurpose): String  // stores the hash, returns the plain code
    fun verify(email: String, purpose: OtpPurpose, code: String)
}

// com.example.lib4gz.common.notification — blocking too, called in the same blocks.
// It owns the wording of every message; there is no generic notify().
interface NotifierService {
    fun sendRegistrationOtp(email: String, code: String, ttlMinutes: Long)       // "Your Lib4GZ sign-up code"
    fun sendEmailVerificationOtp(email: String, code: String, ttlMinutes: Long)  // "Confirm your Lib4GZ email"
}
```

`MeService` was folded into `UserService`. `NotifierService` (`common/notification/`) is a collaborator of `RegistrationServiceImpl` and `UserServiceImpl`: it words the message and hands it to the single `EmailSender` bean (`common/notification/email/`), picked by `application.mail.provider` — `log`, which writes the mail and its code to the console and is the default, or `brevo`, which posts to Brevo's HTTP API. `GoogleTokenVerifier` (`common/config/security/`) is described in [security.md](../infrastructure/security.md); the mail providers and their environment variables in [configuration.md](../infrastructure/configuration.md).

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
- `issue(email, purpose)`: an existing row younger than `application.otp.resend-cooldown-ms` (60 s) → 400 `"Please wait a minute before requesting another code"`. Otherwise the existing row is **overwritten in place** (new hash, `attempts = 0`, `createdAt = now`, `expiresAt = now + ttl-ms` (10 min)), or a new row is inserted. The row is not deleted and re-inserted: Hibernate flushes inserts before deletes, which would violate the `(email, purpose)` unique constraint. `issue` sends nothing: it returns the plain code for the caller to hand to `NotifierService` inside the same transaction.
- `verify(email, purpose, code)`: no row → 400 `"Code expired or not found. Request a new one"`; expired → delete + same message; `attempts >= max-attempts` (5) → delete + 400 `"Too many attempts. Request a new code"`; mismatch (constant-time compare) → `attempts++` + 400 `"Invalid code"`; match → delete the row.

### Email registration — `RegistrationServiceImpl`

- `startEmail(email)`: `trim().lowercase()`; no `@` → 400 `"Invalid email"` (`ValidationException`); an account with that email (case-insensitive) → 409 `"Email already in use"`; then `otpService.issue(email, REGISTER)` followed by `notifierService.sendRegistrationOtp(email, code, otpService.ttlMinutes)`, both inside one `transactionTemplate.execute` block, so a delivery failure rolls the stored code back. Returns `OtpSentResponse(email, cooldownSeconds = resend-cooldown-ms / 1000)`.
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
   - Else **any account with that email** (case-insensitive, verified or not) → 400 `"An account with this email already exists. Sign in with your password, then connect Google on your profile."` (`BusinessException`). Sign-in never writes `google_id`; joining is the owner's explicit action (`connect`).
   - **No match** → `deleteByEmail`, save `PendingRegistration(email, googleId, name, avatarUrl = picture, emailVerified = true, 15 min)` → `GoogleSignInResponse(registration = …)` with `requiresPassword = false`. The client then calls `/v1/auth/register/complete`.

### Connect a Google account — `GoogleAuthServiceImpl.connect(userId, idToken)`

`POST /v1/auth/google:connect`, the only authenticated endpoint under `/v1/auth`. The only way an account's email changes.

1. Token verified outside the transaction, same 401s as sign-in.
2. In one transaction: caller missing → 404; another user has this `google_id` → 409 `"That Google account is already connected to another account"`; another user has this email → 409 `"Email already in use"`. Otherwise `googleId = sub`, `email = <lowercased Google address>`, `emailVerified = true`, `avatarUrl = picture` (unchanged when Google sends none). Name, password, roles untouched. Returns the saved `UserResponse`.
3. After the commit, if the address changed: `notifierService.sendEmailChanged(old, new)` to the **old** address. A failure is logged (`warn`) and does not fail the request. Connecting the same account again is a success that refreshes address and avatar and sends nothing.

### Profile — `UserServiceImpl`

- `getMe(userId)`: `toResponse()`; missing → 404 `"User not found with id: …"`.
- `updateMe(userId, request)` (one transaction): `name` present: trimmed; blank → 400 `"Name must not be blank"`. Returns the saved `UserResponse`. The email is not accepted here.
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
| POST   | /v1/auth/google:sign-in        | googleSignIn            | GoogleSignInRequest            | Mono\<GoogleSignInResponse\>         | 401 invalid token / unverified Google email; 400 an account with this email already exists |
| POST   | /v1/auth/google:connect        | googleConnect (JWT required, `X-User-Id`) | GoogleSignInRequest | Mono\<UserResponse\>              | 401 invalid token / unverified Google email; 404; 409 Google account or email owned by another account |
| POST   | /v1/auth/register/email        | startEmailRegistration  | StartEmailRegistrationRequest  | Mono\<OtpSentResponse\>              | 400 invalid email / cooldown; 409 email in use; 503 mail |
| POST   | /v1/auth/register/email/verify | verifyEmailRegistration | VerifyEmailRegistrationRequest | Mono\<RegistrationStartedResponse\>  | 400 invalid / expired / too many attempts |
| POST   | /v1/auth/register/complete     | completeRegistration    | CompleteRegistrationRequest    | Mono\<LoginResponse\>                | 404 registration; 400 username / password rules; 409 username / email |
| POST   | /v1/auth/refresh-token         | refreshToken            | -                              | Unit                                 | Not implemented (not in `permittedEndpoints`) |

The two Google actions are custom methods on one resource, hence the colon. `google:connect` is the only
handler here that needs a token: `security.json` permits `/v1/auth/google:sign-in`, not `/v1/auth/google:connect`.

### UserController — `@RequestMapping("/v1/users")` (JWT required)

All `/me` handlers take `@RequestHeader(PZRequestHeader.USER_ID)`. Spring matches the literal `me` segment before `/{userId}`.

| Method | Path                          | Function         | Request Body            | Response                    | Errors |
|--------|-------------------------------|------------------|-------------------------|-----------------------------|--------|
| GET    | /v1/users/me                  | getMe            | -                       | Mono\<UserResponse\>        | 404 |
| PATCH  | /v1/users/me                  | updateMe         | UpdateMeRequest (name)  | Mono\<UserResponse\>        | 400 blank name |
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
8. **Google is matched by id only:** sign-in looks up `google_id` and never writes it. A Google account whose email belongs to an existing user is refused with one message, whatever the account's verified flag. Joining or switching a Google account is `POST /v1/auth/google:connect` by the signed-in owner. Existing accounts start unverified; there is no data migration.
9. **The email is the connected Google account's address:** the profile cannot edit it. `connect` rewrites `email`, `emailVerified = true`, `google_id` and `avatarUrl` in one transaction, and the old address is mailed afterwards when it changed. The email can never be cleared. An email change does not invalidate issued tokens.
10. **UserResponse:** `name` falls back to `username`; `role` is ADMIN > TEACHER > USER. The JWT `user` claim embeds `UserResponse`, so a name in an old token can be stale; clients read `GET /v1/users/me`.
11. **Soft delete:** `User` uses `@SQLDelete` with inline SQL timestamp `(EXTRACT(EPOCH FROM NOW()) * 1000)::bigint` and has no `@Where(clause = "deleted_at IS NULL")`. `email_otps` and `pending_registrations` are hard-deleted (transient credentials).
12. **Default roles and status:** new users get `mutableSetOf(UserRole.USER)` and `UserStatus.ACTIVE`.
13. **TEACHER role:** the global `UserRole.TEACHER` grants course creation; users without it receive 403 on `POST /v1/courses`. This is separate from the per-course `EnrollmentRole.TEACHER`.
14. **Refresh token endpoint:** `/v1/auth/refresh-token` exists but is not implemented.

---

## 7. Tests

Plain JUnit 5 + Mockito unit tests (no Spring context, `.block()` on the `Mono`) under `src/test/kotlin/com/example/lib4gz/auth/`: `service/AuthServiceImplTest`, `service/UserServiceImplTest`, `service/OtpServiceImplTest`, `service/RegistrationServiceImplTest`, `service/GoogleAuthServiceImplTest`, `job/RegistrationCleanupJobTest`. `TransactionTemplate` is a mock whose `execute` runs the callback, so transaction behaviour itself is only checked against a real database.

The mail layer these services lean on is covered outside the auth package, under `src/test/kotlin/com/example/lib4gz/common/notification/`: `NotifierServiceImplTest` (subjects and body), `email/MailProviderSelectionTest` (`application.mail.provider` picks exactly one `EmailSender` bean, and an unknown name fails the boot), `email/brevo/BrevoEmailSenderTest` (request shape, `messageId`, 503 on any failure).
