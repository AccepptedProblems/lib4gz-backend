# exercise

Owns: exercises, their questions, student submissions and answers, and the teacher's
per-lesson review read.

Entry points: `service/SubmissionService.kt`, `model/entity/Exercise.kt` (the status
transitions live on `Submission`), `service/LessonSubmissionsService.kt`.

## Rules

- One submission per (exercise, student), enforced by a unique key. Saving answers is
  create-or-update and is what the student's autosave calls; it keeps one answer per
  question.
- Status goes DRAFT, then SUBMITTED, then APPROVED or NEEDS_REVISION. Approve and
  request-revision are allowed only from SUBMITTED; the check is on the entity and the
  service turns a refusal into a 400. Submit is allowed from any status.
- SUBMITTED and APPROVED are what `courses/service/ProgressService.kt` counts as done. A new
  status must be placed on one side of that line, and added to the
  `submissions_status_check` constraint by a migration.
- A DRAFT write must not bump the SUBMISSION freshness token, or autosave would empty the
  teacher's cache every two seconds. `common/freshness/` enforces this.
- Access uses the same two helpers as `courses/`. The owning course is found with
  `ExerciseService.getCourseIdByExercise`.
- A teacher sees every submission of an exercise and a learner sees only their own, from
  the same endpoint.
- `listLessonExercises` and `LessonSubmissionsService` issue a fixed number of queries.
  Do not add a per-exercise or per-submission query inside them.
- `Question` holds `exerciseId` as a plain id, not a relation.
- An exercise delete hides its questions in the service, in the same transaction, before
  its own delete. The course, module and lesson deletes in `courses/` reach exercises and
  questions the same way.
- Submissions and answers have no hidden flag. Every read reaches them through their
  exercise, and Hibernate puts `Exercise`'s `@Where` in the ON clause of JPQL joins too, so
  a submission under a hidden exercise drops out of progress and counts with no filter of
  its own.
- No uniqueness rule on an exercise's `order_index`. Lists order by position, then
  `createdAt`.

## Gotchas

- Submissions and answers are hard-deleted. Exercises and questions are soft-deleted.
- HIDDEN questions are filtered out for learners only in
  `QuestionService.getQuestionsByExercise`. The `expand=questions` reads return every
  question to every enrolled caller.
- Saving answers does not check the status, so answers can still change after SUBMITTED or
  APPROVED, and each such save bumps the SUBMISSION token.
- Request-revision accepts feedback text and does not store it.

## Verify

No tests cover this module.
