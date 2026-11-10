# race

Owns: Lightning Kart's race questions (one bank per course, each question in a module),
finished-race results, the leaderboard per course or per module, and the racer's look stored
in `users.race_appearance`.

Entry points: `service/RaceService.kt`, `repo/RaceResultRepo.kt` (the leaderboard queries).

## Rules

- Access uses the same two helpers as `courses/`: `isEnrolledInCourse` reads the bank, saves
  a result and reads the leaderboard; `isTeacherInCourse` replaces the bank.
- `PUT /v1/courses/{courseId}/race-questions` replaces the whole bank in one transaction: an
  item with an id updates that question, an item without one creates it, and a question whose
  id is absent from the request is hidden. An id the course no longer holds is a 400, not a
  create. `orderIndex` is the item's position in the request; the client never sends one.
- A question needs 2 to 6 non-blank options, a `correctIndex` inside them, and a module of the
  same course. The checks run before the transaction, so a bad item changes nothing.
- `RaceQuestion` and `RaceResult` hold `courseId`, `moduleId` and `userId` as plain ids, not
  relations. A course or module delete in `courses/` hides the race questions under it with
  `hideByCourseId` or `hideByModuleId`, in the parent's own transaction.
- Results are append-only: a result is never updated or deleted, and the entity has no
  `deleted_at`. A person's best time is computed from every result they saved.
- The leaderboard scope is `(courseId, moduleId)` with `moduleId` null for the whole course.
  A course-wide result and a module result are different scopes and never mix.
- The bank bumps no freshness token; `common/freshness/` does not know these entities. Do not
  add them.
- `users.race_appearance` is written by `PATCH /v1/users/me` in `auth/`, which accepts only
  `{"characterId", "kartId"}` with non-blank strings, and is read here for the leaderboard.

## Gotchas

- The correct index is sent to every enrolled caller in `GET .../race-questions`; the browser
  checks answers.

## Verify

`./gradlew test --tests "com.example.lib4gz.race.*"`
