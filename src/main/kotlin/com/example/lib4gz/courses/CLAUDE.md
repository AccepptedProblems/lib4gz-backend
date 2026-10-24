# courses

Owns: courses, enrollment, modules, lessons, summaries, the syllabus read, resume position
and progress, and the read side of the freshness tokens.

Entry points: `service/EnrollmentService.kt` (who may do what), `service/SyllabusService.kt`
(the one read every course screen starts from), `service/ProgressService.kt`.

## Rules

- Access is decided in the service, before any write, in three tiers:
  - the course creator only: update and delete the course;
  - `isTeacherInCourse` (creator, or an ACTIVE enrollment with role TEACHER): every other
    write, and listing a course's enrollments;
  - `isEnrolledInCourse` (creator, or any ACTIVE enrollment): reading modules, lessons and
    summaries.
- PENDING and REJECTED enrollments grant nothing.
- A PUBLIC course lets any signed-in user read the course, its syllabus and its
  last-updates. Its modules, lessons and summaries still need an ACTIVE enrollment.
- `FreshnessService` must authorize exactly as `SyllabusService` does. If they differ,
  someone can read content they can never validate a cache for.
- Creating a course needs the global TEACHER role and enrolls the creator as an ACTIVE
  TEACHER. Anyone else who enrolls starts PENDING until a teacher approves.
- Progress is derived, never stored. A lesson is done when each of its exercises has a
  SUBMITTED or APPROVED submission from the caller; a lesson without exercises does not
  count. The only stored state is the last visited lesson, on the enrollment.
- `ProgressService.recordVisit` does nothing, without an error, when the caller has no
  ACTIVE enrollment: the client calls it on every lesson open.
- A last-visited lesson that has been deleted is returned as null, so the client never
  links to a missing lesson.
- The syllabus and the `?expand=` reads issue a fixed number of queries whatever the course
  size. Do not add a per-module or per-lesson query inside them. New optional data goes
  behind an `expand` token, not a new endpoint.
- `compute*` in `ProgressService` and `is*InCourse` in `EnrollmentService` are blocking on
  purpose: they are called from inside other services' `Mono.fromCallable`.
- A delete hides its whole subtree in the service, in one `transactionTemplate` block: the
  `hide*` bulk updates of every level below run before the parent's own delete, down to
  questions, whether the delete starts at a course, a module or a lesson. A new child table
  needs the `deleted_at` column, the `@Where` filter and a `hide*` line in each ancestor's
  delete.
- No uniqueness rule on `order_index`. Lists order by position, then `createdAt`. A reorder
  takes every visible id of the parent, rejects any other list with a 400, and rewrites the
  positions as entity writes so freshness sees them.
- `Module` and `Lesson` carry `@DynamicUpdate`, so an update writes only the columns it
  changed. A full-row update from an entity loaded before a concurrent delete would write
  `deleted_at` back to null and bring the row back.

## Gotchas

- Enrollments and summaries are hard-deleted. Courses, modules and lessons are soft-deleted.
- A lesson delete hard-deletes its summary through the lesson's JPA cascade. A module or
  course delete leaves its lessons' summaries in place: the lessons are already hidden when
  the cascade runs, so it finds none.
- A new child takes the highest visible position plus one, which a hidden row may also hold.
- An enroll request carries the role the client asked for, and approval does not change it.
- Looking a course up by its join code uses the same access check as reading it, so the
  lookup is refused for a PRIVATE or UNLISTED course the caller is not enrolled in. The web
  app's create form defaults to PUBLIC; the server's default is PRIVATE.

## Verify

`service/FreshnessServiceImplTest` is the only test for this module. Enrollment, course,
lesson and progress logic have none.
