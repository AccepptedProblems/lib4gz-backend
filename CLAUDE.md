# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in the Lib4GZ backend: Kotlin, Spring Boot
3.5, Java 21, Gradle.

Product context for the whole system lives in `../docs/`. Cross-cutting contracts with the
frontend live in `../CLAUDE.md`.

## Commands

- `./gradlew bootRun` — run locally on `:8080`. The servlet context-path is `/api`, so
  endpoints live under `http://localhost:8080/api/v1/...`
- `./gradlew bootJar` — produce the runnable jar in `build/libs/`
- `./gradlew test` — JUnit 5 + kotlinx-coroutines-test
- `./gradlew test --tests "com.example.lib4gz.auth.service.AuthServiceImplTest"` — single test
- `deploy/deploy.sh` — Cloud Build → Artifact Registry → Cloud Run (project `library4gz`,
  service `libforgzbe`, region `asia-southeast1`). Pulls `SUPABASE_PROJECT_REF`,
  `SUPABASE_DB_PASSWORD`, `JWT_SECRET_KEY`, `BREVO_API_KEY`, and `GOOGLE_CLIENT_ID` from
  Google Secret Manager at runtime, and passes `MAIL_PROVIDER`, `MAIL_FROM_NAME` and
  `MAIL_FROM_ADDRESS` as plain environment variables (defaults `brevo`, `Lib4GZ`,
  `no-reply@library4gz.com`; the domain is authenticated at Brevo, so any address on it may send). `deploy/setup-secrets.sh` only
  adds versions: run `gcloud secrets create <NAME> --replication-policy=automatic
  --project=library4gz` first for any secret that does not exist yet.

## Required env (local)

Two ways to supply them locally:

- `./deploy/run-local.sh [gradle task]` pulls all five secrets from Google Secret Manager (needs
  a `gcloud auth login` with access to project `library4gz`) and then runs `bootRun`. No file needed.
- Or put values in `lib4gz/.env` (git-, gcloud-, and docker-ignored; template `.env.example`).
  Spring loads it via `spring.config.import`; real environment variables override it. Never commit it.

`SUPABASE_PROJECT_REF`, `SUPABASE_DB_PASSWORD`, `JWT_SECRET_KEY` (required; no default in
`application.yaml`).

Optional:

- `MAIL_PROVIDER` — who sends the one-time codes: `log` or `brevo`. Unset or blank means `log`, so a
  local run prints the mail and the code to the `bootRun` console instead of sending it.
- `MAIL_FROM_NAME` (defaults to `Lib4GZ`), `MAIL_FROM_ADDRESS`, `BREVO_API_KEY` — needed by every
  real provider. A sending provider checks them in its constructor and fails the boot with one line
  naming every missing variable, rather than returning a 503 on somebody's first sign-up.
- `GOOGLE_CLIENT_ID` — needed for Google sign-in. Public value
  `7006272624-f8n2024mnhstsk6cmtmfss1a1j4ffaip.apps.googleusercontent.com`. Unset means every
  Google token is rejected with 401.

The database is Supabase Postgres over SSL. `src/main/resources/application-dev.yaml` holds a `dev` profile
alongside the default `application.yaml`.

## Schema migrations

Flyway owns the schema: `src/main/resources/db/migration/V<n>__<what>.sql`, applied on boot. JPA runs
with `ddl-auto=validate`, so Hibernate never changes the database and refuses to start on a mismatch.

- Any entity change ships with a new migration: a column added, renamed, retyped or dropped, and
  any value added to or renamed in an `@Enumerated(EnumType.STRING)` enum. Each enum column has a
  `<table>_<column>_check` constraint, and `validate` does not check it. Copy
  `V2__rename_otp_purpose_email_change_to_email_verification.sql`.
- Never edit a migration that has been deployed; add the next version.
- `./gradlew test` touches no database: `Lib4gzApplicationTests` pins `spring.flyway.enabled=false`
  and `ddl-auto=none`, because that test otherwise reads the same `.env` the app does and would run
  V2/V3 (which delete rows and drop a column) against Supabase. Check a new migration explicitly,
  against a throwaway Postgres:

  ```
  docker run -d --name pg -e POSTGRES_PASSWORD=pg -p 55432:5432 postgres:17
  MIGRATION_CHECK=true MIGRATION_SCHEMA_CHECK=validate \
    SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/postgres \
    SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=pg ./gradlew test --rerun-tasks
  ```

  Run it twice: once on an empty database (V1 runs) and once on one loaded with the already-deployed
  migrations (baseline, then only the new one runs).

## Architecture

Feature-sliced packages under `com.example.lib4gz.{auth,courses,exercise}`, each with its own
`controller / service / repo / model/{entity,mapper,payload}` subtree. `common/` holds the
cross-cutting pieces.

- `common/utils/IdGenerator.kt` — TypeID-style `prefix_<26-char Crockford base32>` IDs derived
  from UUIDv7. Use it for every new entity ID. Never raw UUIDs.
- `common/utils/ExpandTokens.kt` — parses the `?expand=` query parameter used by composite
  read endpoints. Add a new expand value here rather than adding a parallel endpoint.
- `common/freshness/` — a Hibernate event listener bumps a per-course, per-resource-type
  freshness token on every entity write, backing the frontend's client-side cache. Services
  must never bump tokens by hand; the listener structurally cannot miss a write.
- `common/config/error/GlobalExceptionHandler.kt` — maps the exceptions in
  `common/exception/` to responses. Throw those, do not build error responses in services. The
  message of those exceptions is shown to the person, so word it for them. Anything unhandled is a
  500 whose body is a fixed sentence; the exception and its stack go to the log under the request id,
  never onto the wire.
- `common/notification/` — everything about telling people things, in one folder. Only
  `NotifierService` is used from outside it: callers name a business event and it owns the wording.
  Below it, `email/EmailSender` is transport only (to, subject, body) and one provider class per
  vendor sits under it, each a `@Component` guarded by `@ConditionalOnProperty` on
  `application.mail.provider`. Exactly one provider is in the context; switching is an environment
  variable and a redeploy, never a code edit. A new message is a method on `NotifierService`;
  a new vendor is a class in `email/<vendor>/`; a new channel is a sibling of `email/`.

Security is **whitelist-driven from a JSON file**, not code. `src/main/resources/security.json`
declares `permittedEndpoints`, `authorizedEndpoints` (path → roles), and CORS. `SecurityConfig`
reads it via `AppSecurityConfigLoader` at startup. To expose a new public route or restrict
one by role, edit the JSON. Do not add `requestMatchers(...)` calls in Kotlin. Note that
`authorizedEndpoints` is currently empty and CORS allows all origins, so in practice every
role check happens in service code.

Auth is stateless JWT. `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`,
validates the `access_token` header, and on success injects the user id into the `X-User-Id`
request header, consumed in controllers via `@RequestHeader(PZRequestHeader.USER_ID)`.
Controllers rely on that header rather than re-parsing the token.

The stack mixes Spring MVC and WebFlux. Controllers return `Mono<T>` / `Flux<T>` even though
the servlet container is in use, with `open-in-view: false`.

## Conventions to follow

These patterns repeat across the codebase. Match them in new code rather than introducing a
competing style. `.claude/skills/check-boilerplate/SKILL.md` scans for where they have gone
too far.

- Wrap blocking JPA work in `Mono.fromCallable { … }.subscribeOn(Schedulers.boundedElastic())`.
- Soft delete entities with `@SQLDelete` plus `@Where(clause = "deleted_at IS NULL")`.
  Submissions, one-time codes (`email_otps`), and unfinished sign-ups (`pending_registrations`)
  are the deliberate exceptions and are hard-deleted.
- Signal a missing row with `.orElseThrow { ResourceNotFoundException(...) }`.
- Authorize inside the service with `enrollmentService.isTeacherInCourse()` or
  `isEnrolledInCourse()` before mutating anything.
- Take the caller's id from `@RequestHeader(PZRequestHeader.USER_ID)` in every controller.

## Documentation

- `document/index.md` is the technical reference: architecture, per-feature docs,
  infrastructure, plus `document/spec/openapi.yaml` and `document/models/*.yaml`. Update it
  when behavior changes.
- `requirement.md` and `document/doc_phase1.md` are phase-1 snapshots. Read-only history.
- `plans/` holds engineering design documents. Their plain-language status summary is in
  `../docs/roadmap.md`.

## Before you finish

- `./gradlew test` passes.
- `document/` updated if behavior changed.
- `../docs/` updated if a user-visible feature changed.
- No commit, push, or deploy unless the user asked for one.

## Documentation debt

Apart from the auth feature (login by email, email/Google registration, `/v1/users/me`),
`document/` has not been updated since 2026-05-11 and is missing everything added since:

- The course syllabus endpoint and the `?expand=` mechanism.
- The freshness / last-updates system in `common/freshness/`.
- Lesson-visit progress and the derived course completion percentage.
- The Quick Start in `document/index.md` still describes a local Docker Postgres and a `dev`
  profile, not the Supabase environment variables above.

Test coverage is also thin: the application context, freshness, and the `auth` package
(plain JUnit 5 + Mockito unit tests for the auth, user, OTP, registration, and Google sign-in
services and the registration cleanup job). There are no course or submission tests.
