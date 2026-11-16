# race

Owns: Lightning Kart's race questions (one bank per course, each question in a module), the
races the server deals and checks, finished-race results, the leaderboard per course or per
module, and the racer's look stored in `users.race_appearance`.

Entry points: `service/RaceService.kt`, `repo/RaceRepo.kt` (the answer statement),
`repo/RaceResultRepo.kt` (the leaderboard queries).

## Rules

- Access uses the same two helpers as `courses/`: `isEnrolledInCourse` reads the bank, starts a
  race and reads the leaderboard; `isTeacherInCourse` replaces the bank.
- A key leaves the server in three places only: `GET .../race-questions` to a teacher of the
  course, the reply to that PUT, and the reply to a successful finish of the caller's own race.
  That reply's `answerKey` holds the key of the first `MAX_SHOWN_KEYS` questions judged wrong
  only, null at every other question number. Anyone else's question list has no options and no
  `correctIndex`, since the options in typed order show the key. Start and answer replies carry
  none.
- `MAX_SHOWN_KEYS` copies the game's `missedShown`. Change both together, or the result screen
  lists missed questions without their right answer.
- Answer and finish check no enrolment: the race row names its user, and start checked once.
  Another user's race, or one under another course, is a 404.
- An answer is one SQL statement, `RaceRepo.answer`, run in autocommit. Keep it one statement:
  no read before it, no enrolment check, no second write. A second query runs only when it
  refuses, to tell a resend from a refusal. This is statements of this module only: the token
  filter makes reads of its own on every request.
- In that statement: a number below `next_seq` is refused; a number at or above it is taken and
  the numbers skipped count neither way (the browser voided them); a finished race takes no
  answer. A wrong number, a timeout included, is appended to `wrong_seqs`, so that column stays
  in race order. In the service: a refused `next_seq - 1` is a resend and gets the stored verdict, and
  a null choice becomes -1, which counts as wrong.
- A finish refuses a time over the wall time since start or under `RaceProperties.floorMs`,
  then saves the counts its own finishing update returns, never the browser's. It builds the
  reply's key from the wrong numbers that same update returns, never from the read before it.
- The floor's numbers in `application.race` copy the game's constants in the frontend. When
  the game's laps, top speed, boost length, Double Boost multiplier or lap length change,
  change them here too, or real finishes get refused.
- `PUT /v1/courses/{courseId}/race-questions` replaces the whole bank in one transaction: an
  item with an id updates that question, an item without one creates it, and a question whose
  id is absent from the request is hidden. An id the course no longer holds is a 400, not a
  create. `orderIndex` is the item's position in the request; the client never sends one.
- A question needs 2 to 6 non-blank options, a `correctIndex` inside them, and a module of the
  same course. The checks run before the transaction, so a bad item changes nothing.
- `RaceQuestion`, `Race` and `RaceResult` hold `courseId`, `moduleId` and `userId` as plain
  ids, not relations. A course or module delete in `courses/` hides the race questions under it
  with `hideByCourseId` or `hideByModuleId`, in the parent's own transaction.
- Races and results have no `deleted_at` and are never deleted by the code. A result is never
  updated. A person's best time is computed from every result they saved.
- The leaderboard scope is `(courseId, moduleId)` with `moduleId` null for the whole course.
  A course-wide result and a module result are different scopes and never mix.
- The bank bumps no freshness token; `common/freshness/` does not know these entities. Do not
  add them.
- `users.race_appearance` is written by `PATCH /v1/users/me` in `auth/`, which accepts only
  `{"characterId", "kartId"}` with non-blank strings, and is read here for the leaderboard.

## Gotchas

- A browser that skips its own debuff can still claim the fastest time its right answers allow.
- The first answer on a pooled connection is followed by one driver query for column details;
  the driver caches it for that connection.

## Verify

`./gradlew test --tests "com.example.lib4gz.race.*"`. `race/service/RaceDatabaseTest.kt` runs the
answer and finish SQL against Postgres, and only inside the migration check of
`src/main/resources/db/migration/CLAUDE.md`; otherwise it is skipped.
