# Lib4GZ backend

The server for Lib4GZ, a learning platform for small teaching groups. Kotlin, Spring Boot
3.5, Java 21, Gradle, Postgres on Supabase, deployed to Google Cloud Run.

What the product does: `../docs/README.md`. Contract with the frontend: `../CLAUDE.md`.

## Run

- `./deploy/run-local.sh` pulls the secrets from Google Secret Manager and runs `bootRun` on
  `:8080`. It needs `gcloud auth login` with access to project `library4gz`, and no file.
- Or copy `.env.example` to `.env`, fill it in, and run `./gradlew bootRun`. `.env.example`
  explains every variable. Never commit `.env`.
- The servlet context-path is `/api`, so endpoints are `http://localhost:8080/api/v1/...`.
- With `MAIL_PROVIDER` unset, one-time codes are printed to the console, not emailed.
- `bootRun` connects to the real Supabase database and applies pending migrations to it.

## API reference

Generated from the controllers; there is no hand-written API document. With
`API_DOCS_ENABLED=true` (set by `run-local.sh` and `.env.example`, never in production):
`http://localhost:8080/api/swagger-ui.html`, JSON at `/api/v3/api-docs`.

## Verify

- `./gradlew test` runs everything and touches no database. The two tests that boot the
  whole application read the same settings as the app, so the required variables must come
  from `.env` or the shell (`./deploy/run-local.sh test` supplies them).
- `./gradlew test --tests "com.example.lib4gz.auth.service.AuthServiceImplTest"` runs one class.
- A new migration needs the check in `src/main/resources/db/migration/CLAUDE.md`.
- `scripts/check-docs.sh` fails when a `CLAUDE.md` names a file that no longer exists, and
  lists folders whose code changed without their doc.
- Tests cover `auth`, `common`, `race` and freshness. `courses` and `exercise` have almost
  none, so a change there is not verified by `./gradlew test`; say so instead of reporting it
  checked.

## Map

Feature packages live under `src/main/kotlin/com/example/lib4gz/`, each with
`controller / service / repo / model/{entity,mapper,payload}`. Every folder below has its
own `CLAUDE.md`, which loads when you open a file there. Read it before changing that folder.

| Folder | Owns |
| --- | --- |
| `auth/` | Accounts, sign-in, sign-up, own profile |
| `courses/` | Courses, enrollment, modules, lessons, summaries, syllabus, progress |
| `exercise/` | Exercises, questions, submissions, teacher review read |
| `race/` | Lightning Kart: race questions, results, leaderboard, the racer's look |
| `common/` | Ids, expand tokens, exceptions and the error body |
| `common/notification/` | Telling people things; mail providers |
| `common/freshness/` | Cache tokens bumped from entity writes |
| `common/config/security/` | `security.json`, the token filter, `X-User-Id` |
| `src/main/resources/db/migration/` | Flyway migrations and how to check one |
| `deploy/` | Cloud Run deploy, secrets, local run with real secrets |

Design documents for work in this repository alone live in a plans folder at the root; a plan
is deleted when its work is done and its lasting rules have moved to a module `CLAUDE.md`.
`.claude/skills/check-boilerplate/SKILL.md` scans for repeated patterns.

## Rules that cross modules

- An entity change ships with a migration. So does a new or renamed enum value.
- Authorize inside the service, before any write, with
  `enrollmentService.isTeacherInCourse()` or `isEnrolledInCourse()`.
- Take the caller's id from `@RequestHeader(PZRequestHeader.USER_ID)` in every controller.
- Throw the exceptions in `common/exception/`; never build an error response in a service.
  The message is shown to the person.
- Wrap blocking JPA work in `Mono.fromCallable { }.subscribeOn(Schedulers.boundedElastic())`.
  `@Transactional` does not cover it; use `transactionTemplate.execute` inside the callable.
- Signal a missing row with `.orElseThrow { ResourceNotFoundException(...) }`.
- New entity ids come from `IdGenerator.generate("<prefix>")`. Never raw UUIDs.
- Soft delete with `@SQLDelete` plus `@Where(clause = "deleted_at IS NULL")`. The entities
  that are hard-deleted instead are named in each module's `CLAUDE.md`.
- To make a route public or role-restricted, edit `src/main/resources/security.json`.
- Optional data on a read goes behind an `?expand=` token, not a new endpoint.
- Never bump a freshness token by hand.

## Before you finish

- `./gradlew test` passes.
- The `CLAUDE.md` in every folder you changed still tells the truth, and
  `scripts/check-docs.sh` is clean.
- `../docs/` is updated if a user-visible feature changed.
- No commit, push, or deploy unless the user asked for one.
