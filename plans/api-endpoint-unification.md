# API Endpoint Unification Plan — Lib4GZ LMS

**Author:** Senior Architecture Review
**Date:** 2026-05-06
**Scope:** Backend (`lib4gz/`) + Frontend (`lib4gz-fe/`)
**Status:** Implemented — the syllabus endpoint, `?expand=`, and `/me/courses` shipped as of 2026-07-04.
The open questions in section 12 are still undecided. Moved here from the repository root on 2026-09-11.

---

## Executive Summary

### The problem
The Lib4GZ frontend currently issues 2× to 13× HTTP requests per screen because every level of the `course → module → lesson → exercise → question/submission` hierarchy is fetched in a separate round-trip, often with **N+1 fan-out** (e.g., 1 list call + N per-row enrichment calls). This costs money on Cloud Run (per-request billing + cold-start amplification) and degrades perceived latency on slow networks.

### Three root causes
1. **Resource-tree fan-out** — every hierarchy level is a separate GET, even when the screen always wants the full sub-tree.
2. **Trivial cross-resource lookups** — calling a sibling resource just to read one field (e.g., fetching a Module solely for its title on the lesson page).
3. **Per-row N+1 enrichment** — looping `for each exercise: GET /exercises/{id}/my-submission`. Cost scales with content; gets worse over time.

### The fix in one sentence
**One coarse composite GET per page view + opt-in `?expand=` on existing endpoints + denormalized cheap fields in list rows.** Mutations stay granular.

### Projected impact
| Screen | RTTs before | RTTs after | Cut |
|---|---|---|---|
| Course shell (Layout + Home) | 7 | 1 | -86% |
| Exercise Attempt (5 exercises) | ~11 | 1 | -91% |
| Lesson Submissions tab (5 exercises, 10 subs) | ~26 | 1 | -96% |
| Course Material (teacher) | 1 + N | 1 | massive |
| Lesson Detail Management | 4 | 1 | -75% |
| Dashboard | 2 | 1 | -50% |

### Migration approach
Additive, four phases, zero breaking changes:
1. Add denormalized fields to existing responses (no API surface change)
2. Add `?expand=` to existing collection/item endpoints
3. Add new composite aggregate endpoints (`/syllabus`, `/lessons/{id}/submissions`, `/me/courses`)
4. Migrate frontend screens one at a time, then deprecate unused legacy paths

---

## Table of Contents

1. [Current State — Cost Map](#1-current-state--cost-map)
2. [Root-Cause Analysis](#2-root-cause-analysis)
3. [Design Principles Applied](#3-design-principles-applied)
4. [Proposed Changes — Tier 1: Composite Endpoints](#4-tier-1--composite-read-endpoints)
5. [Proposed Changes — Tier 2: Field Denormalization](#5-tier-2--field-denormalization)
6. [Proposed Changes — Tier 3: Dashboard](#6-tier-3--dashboard)
7. [Per-Screen Migration Mapping](#7-per-screen-migration-mapping)
8. [Implementation Sequencing](#8-implementation-sequencing)
9. [Backend Implementation Notes](#9-backend-implementation-notes)
10. [Frontend Implementation Notes](#10-frontend-implementation-notes)
11. [Anti-Patterns to Avoid](#11-anti-patterns-to-avoid)
12. [Open Questions / Decisions Needed](#12-open-questions--decisions-needed)

---

## 1. Current State — Cost Map

All paths are prefixed by `/api/v1`. Numbers are HTTP round-trips per screen entry (cold load).

| Screen | Endpoints called | RTTs | Pattern |
|---|---|---|---|
| Dashboard (`/`) | `GET /courses?type=enrolled` + `GET /courses?type=created` | 2 (parallel) | Same endpoint, 2× |
| Course Layout (`/course/:id`) | `GET /courses/{id}` + `GET /courses/{id}/modules` + `GET /courses/{id}/my-enrollment` + `GET /modules/{m0}/lessons` | **4** | 3 parallel + 1 chained |
| Course Home (under layout) | `GET /courses/{id}/my-enrollment` + `GET /courses/{id}/modules` + `GET /modules/{m0}/lessons` | **+3** | Re-fetches layout data |
| Course Lesson (under layout) | `GET /lessons/{id}` | 1 | OK |
| Lesson Summary tab | `GET /lessons/{id}/summary` | 1 | OK alone |
| Lesson Exercises tab (student) | `GET /lessons/{id}/exercises` + N × `GET /exercises/{exId}/my-submission` | **1 + N** | N+1 |
| Lesson Submissions tab (teacher) | `GET /lessons/{id}/exercises` + N × `GET /exercises/{exId}/submissions` + on-expand N × `GET /exercises/{exId}/questions` + on-drawer M × `GET /submissions/{id}` | **1 + 3N + M** | N+1 ×3 |
| Exercise Attempt | `GET /lessons/{id}/exercises` + N × `GET /exercises/{exId}/questions` + N × `GET /exercises/{exId}/my-submission` | **1 + 2N** | N+1 ×2 |
| Course Material (teacher) | `GET /courses/{id}/modules` + per-module `GET /modules/{mId}/lessons` (lazy on expand) | **1 + N** | N+1 |
| Lesson Detail Management | `GET /lessons/{id}` → `GET /modules/{mId}` + `GET /lessons/{id}/summary` + `GET /lessons/{id}/exercises` | **4** | 2 parallel + 2 chained |
| Manage Layout | `GET /courses/{id}` + `GET /courses/{id}/enrollments` | 2 (parallel) | Acceptable |
| Exercise Editor | `GET /exercises/{id}` + `GET /exercises/{id}/questions` | 2 (parallel) | Borderline |
| Enrollment Management | `GET /courses/{id}/enrollments` | 1 | OK |

**Worst offenders (in priority order):**
1. Lesson Submissions tab — `1 + 3N + M` (teacher review, gets brutal at scale)
2. Exercise Attempt — `1 + 2N` (student-facing, latency-sensitive)
3. Course Layout + Course Home — duplicated fetches (7 calls where 1 suffices)
4. Course Material — `1 + N` lazy fan-out
5. Lesson Detail Management — 4 calls including a chained module-title lookup

---

## 2. Root-Cause Analysis

### Pattern P1 — Resource-tree fan-out
Every hierarchy level is fetched separately even when the screen always needs the whole sub-tree. The classic "naïve REST" failure mode.

**Examples:** Course Layout fetches `course → modules → lessons[0]`. Course Material does the same. Exercise Attempt does `lesson → exercises → questions per exercise`.

**Why it happens:** Each entity got its own controller (`CourseController`, `ModuleController`, `LessonController`, …) following a one-resource-per-controller convention. Convenient for CRUD; pathological for reads.

### Pattern P2 — Trivial cross-resource lookups
Screens hit a sibling resource just to display one field.

**Examples:**
- `LessonDetailManagementComponent` calls `GET /modules/{moduleId}` only to display `module.title`.
- `CourseLayoutComponent` calls `GET /courses/{id}/my-enrollment` only to gate UI on `enrollment.role`/`status`.

**Cure:** Denormalize the needed field into the parent response. Backend already does this for counters (`lessonCount`, `exerciseCount`, `hasSummary`) — extend the pattern.

### Pattern P3 — Per-row N+1 enrichment
List endpoint returns rows; FE loops with one GET per row.

**Examples:**
- `LessonExercisesComponent`: per-exercise `GET /my-submission` to show a status chip.
- `LessonSubmissionsComponent`: per-exercise `GET /submissions`, per-exercise `GET /questions`, per-submission `GET /submissions/{id}`.
- `ExerciseAttemptComponent`: per-exercise `GET /questions` + per-exercise `GET /my-submission`.

**Cure:** Move the join to the server via `?expand=...` on the list endpoint, and/or include a per-row summary field (`mySubmissionStatus`) computed cheaply in the mapper.

---

## 3. Design Principles Applied

These are the RESTful design rules guiding every recommendation below.

1. **One page = one composite GET; many granular writes.** Reads coarsen; writes stay fine-grained.
2. **Aggregations live on the aggregate root.** `GET /courses/{id}/syllabus` ✅. `GET /course-with-everything` ❌.
3. **`?expand=a,b` for opt-in joining.** Same URL, callers pick richness. Stripe / GitHub / JSON:API "include" idiom.
4. **Sparse fieldsets are opt-in, not default.** Default response stays small to protect bandwidth and cacheability.
5. **Denormalize stable, cheap-to-derive fields into list rows.** Extend the existing pattern (`lessonCount`, `exerciseCount`, `hasSummary`).
6. **Don't invent BFF endpoints per screen.** New endpoints must map to a real domain concept, not a route name.
7. **Composite endpoints are still cacheable.** Support `ETag` / `Last-Modified` like any other resource.
8. **Keep all mutations granular.** Don't merge "create lesson" and "create summary" into one POST — different validation, different failure modes.
9. **Self-scoped collections use `/me/...`.** Standard convention.

### 3a. Code-Quality Rules (HARD)

**Backend (Kotlin / Spring) — SOLID:**

- **S (Single Responsibility).** A class has one reason to change. Mappers map; services orchestrate; controllers translate HTTP. Don't put authorization logic in mappers, don't put DTO assembly in controllers.
- **O (Open / Closed).** Extend behavior via new types, params, or composition — not by editing stable code. The `ExpandTokens` design is OCP: adding a new expand value extends the API without rewriting the controller signature.
- **L (Liskov Substitution).** Subtypes/implementations honor the supertype contract. If a `SyllabusService` interface exists, every impl must satisfy "returns a syllabus or throws a domain exception" — no surprise nulls or side-effects.
- **I (Interface Segregation).** Many small interfaces over one fat one. Readers split from writers; the aggregate services depend only on the read APIs they need.
- **D (Dependency Inversion).** Depend on abstractions. Services depend on repository interfaces (Spring Data already gives us this); aggregate services depend on the smaller domain services rather than tunneling into repos.

**Concrete backend conventions adopted in this refactor:**
- One controller per HTTP resource. Aggregate endpoints get their own controller (`SyllabusController`, etc.) when cross-cutting.
- One mapper file per entity. Mappers are pure functions of (entity, optional context) → DTO. No I/O in mappers.
- Service methods are verbs (`getSyllabus`, `listMyCourses`); repos use Spring Data verbs. No leaking JPA entities past the service boundary.
- No N+1 inside aggregate services — use repository methods with JOINs or batched `findAllById`.
- Authorization checks live in services, not controllers or mappers. Throw domain exceptions; the global handler converts to HTTP.

**Frontend (Angular / TS) — Clean Code:**

- **Meaningful names.** `loadSyllabus()` beats `load()`. Component methods describe outcomes, not mechanics.
- **Small functions.** A function does one thing at one level of abstraction. If a method exceeds ~30 lines, extract.
- **No deep nesting / no callback pyramids.** Flatten with RxJS operators (`switchMap`, `forkJoin`, `pipe`) instead of nested `.subscribe()`.
- **DRY at the service layer, not the component layer.** Components stay independent (don't share state via globals); services own caching and normalization.
- **Strict typing.** No `any` in new code. Prefer discriminated unions for tab states and screen states.
- **Immutability and signals.** Treat signal values as immutable; never mutate in place. Use `.update()` with a returned new object.
- **No fetch-in-template, no logic-in-template.** Templates render computed signals.
- **Error paths are first-class.** Every observable subscription has an error handler; surface user-facing errors through the existing toast service.
- **Service per resource.** Don't merge `lesson.service.ts` and `summary.service.ts` — the URL families are different. Compose at the call site or in a screen-specific facade if needed.
- **No premature abstraction.** Extract a shared helper only after the second duplication. Three similar lines beats a half-fit abstraction.

---

## 4. Tier 1 — Composite Read Endpoints

These are NEW endpoints. They replace the worst fan-outs.

### A. `GET /v1/courses/{courseId}/syllabus`

**Purpose:** Single source of truth for "course table-of-contents" view.

**Response shape:**
```jsonc
{
  "course": {
    "id": "crs_…", "code": "ABC123", "title": "…", "description": "…",
    "visibility": "PRIVATE", "createdBy": { /* UserSummary */ },
    "settings": {}, "createdAt": 0, "updatedAt": 0,
    "moduleCount": 5, "enrollmentCount": 23
  },
  "myEnrollment": {              // null if not enrolled
    "id": "enr_…", "role": "LEARNER", "status": "ACTIVE",
    "joinedAt": 0
  },
  "modules": [
    {
      "id": "mod_…", "title": "Module 1", "orderIndex": 0,
      "lessonCount": 4, "createdAt": 0, "updatedAt": 0,
      "lessons": [
        {
          "id": "lsn_…", "title": "Lesson 1.1", "orderIndex": 0,
          "hasSummary": true, "exerciseCount": 3,
          "createdAt": 0, "updatedAt": 0
        }
      ]
    }
  ]
}
```

**Replaces calls in:**
- `CourseLayoutComponent.loadCourse` — `course` + `modules` + `my-enrollment` + lazy `lessons[0]` (4 → 1)
- `CourseHomeComponent` — same 3 calls (now reads from layout state)
- `CourseMaterialComponent.loadModules` + per-module `loadLessonsForModule` (1 + N → 1)

**Authorization:** Caller must have an enrollment OR the course must be PUBLIC OR caller must be the creator. The `myEnrollment` field is populated based on the caller's user ID.

**Caching:** ETag based on max(course.updatedAt, max(module.updatedAt), max(lesson.updatedAt)). `Cache-Control: private, must-revalidate` (because it includes `myEnrollment`).

**RESTful?** Yes — "syllabus" is a real domain concept and a legitimate aggregate-root view.

---

### B. `GET /v1/lessons/{lessonId}?expand=summary,exercises,module`

**Purpose:** Lesson view page in one request.

**Response shape (with all expands):**
```jsonc
{
  "id": "lsn_…", "moduleId": "mod_…", "title": "…", "orderIndex": 2,
  "hasSummary": true, "exerciseCount": 3,
  "createdAt": 0, "updatedAt": 0,

  "module": {                                    // when expand contains "module"
    "id": "mod_…", "title": "Module 2", "orderIndex": 1
  },
  "summary": {                                   // when expand contains "summary"; null if absent
    "id": "sum_…", "content": "…",
    "editedBy": { /* UserSummary */ }, "version": 3,
    "createdAt": 0, "updatedAt": 0
  },
  "exercises": [                                  // when expand contains "exercises"
    { /* ExerciseResponse — possibly with mySubmissionStatus */ }
  ]
}
```

**Behavior:** `expand` is comma-separated, order-independent, unknown tokens ignored. No expand = base response (today's behavior — fully backwards compatible).

**Replaces calls in:**
- `LessonDetailManagementComponent`: `lesson` + chained `module` + `summary` + `exercises` (4 → 1) — use `?expand=summary,exercises,module`
- `CourseLessonComponent` + `LessonSummaryComponent`: when navigating cold to a lesson — use `?expand=summary` (1 instead of 2 separate calls if user lands on summary tab first)

**RESTful?** Yes — `?expand` is the canonical pattern.

---

### C. `GET /v1/lessons/{lessonId}/exercises?expand=questions,mySubmission`

**Purpose:** Collapse the 2N fan-out on the Exercise Attempt screen and the N+1 on Lesson Exercises tab.

**Response rows (with expands):**
```jsonc
[
  {
    "id": "ex_…", "lessonId": "lsn_…", "title": "…",
    "type": "MULTIPLE_CHOICE", "settings": {},
    "orderIndex": 0, "questionCount": 5,
    "createdAt": 0, "updatedAt": 0,

    "questions": [ /* QuestionResponse[] */ ],     // when expand=questions
    "mySubmission": {                              // when expand=mySubmission; null if none yet
      "id": "sub_…", "status": "DRAFT",
      "answers": [ /* StudentAnswerResponse[] */ ],
      "submittedAt": null, "approvedAt": null,
      "createdAt": 0, "updatedAt": 0
    }
  }
]
```

**Replaces calls in:**
- `ExerciseAttemptComponent.loadQuestionsAndSubmissions`: `?expand=questions,mySubmission` (1 + 2N → 1)
- `LessonExercisesComponent`: `?expand=mySubmission` (1 + N → 1)

**RESTful?** Yes — `?expand` on a collection is standard.

---

### D. `GET /v1/lessons/{lessonId}/submissions?expand=answers,questions`

**Purpose:** Teacher review-queue for one lesson. Replaces the worst N+1 stack (`1 + 3N + M`) on `LessonSubmissionsComponent`.

**Response shape:**
```jsonc
{
  "exercises": [
    {
      "id": "ex_…", "title": "…", "type": "TEXT_ANSWER",
      "orderIndex": 0,

      "questions": [ /* QuestionResponse[] */ ],    // when expand=questions
      "submissions": [
        {
          "id": "sub_…", "user": { /* UserSummary */ },
          "status": "SUBMITTED",
          "submittedAt": 0, "approvedAt": null,
          "createdAt": 0, "updatedAt": 0,
          "answers": [                              // when expand=answers
            { "id": "ans_…", "questionId": "q_…",
              "questionContent": "…", "answer": "…",
              "teacherComment": null,
              "createdAt": 0, "updatedAt": 0 }
          ]
        }
      ]
    }
  ]
}
```

**Replaces calls in `LessonSubmissionsComponent`:**
- 1 exercises call
- N per-exercise submission lists
- N per-exercise question lists (on expand)
- M per-submission re-fetches (the side-drawer)

(1 + 3N + M → **1**)

**Authorization:** Caller must be a TEACHER on the course (via enrollment role).

**RESTful?** Yes — a submissions roll-up scoped to a lesson is a meaningful aggregate ("the review queue for this lesson").

---

## 5. Tier 2 — Field Denormalization

No new endpoints; add fields to existing response DTOs. Cheap, pure-additive, ship first.

### E. `LessonResponse.moduleTitle: String`
- Kills the `lesson → module` chained call in `LessonDetailManagementComponent`.
- Backend: join in mapper. Module title is small, stable, cheap.

### F. `ExerciseResponse.mySubmissionStatus: SubmissionStatus?` + `mySubmissionId: String?`
- Computed per-caller using `userId` from `X-User-Id`.
- Lets `LessonExercisesComponent` render its status chip without any per-row call.
- Even when `?expand=mySubmission` is also supported, this lightweight summary is useful for screens that only need the chip.

### G. `CourseResponse.myEnrollment: EnrollmentSummary?`
- Where `EnrollmentSummary = { id, role, status, joinedAt }` (subset of `EnrollmentResponse`).
- Eliminates the dedicated `my-enrollment` round-trip in layouts.
- Keep `GET /courses/{id}/my-enrollment` for cases that need fresh status after approve/reject mutations.

### H. Skip the call when `hasSummary === false`
- Pure frontend fix; no backend change.
- Some screens still call `GET /lessons/{id}/summary` even when `hasSummary` is false.
- Audit each call site; gate the call on `hasSummary`.

---

## 6. Tier 3 — Dashboard

### I. `GET /v1/me/courses` (optional)

**Response:**
```jsonc
{
  "enrolled": [ /* CourseResponse[] */ ],
  "created": [ /* CourseResponse[] */ ]
}
```

**Replaces** in `UserDashboardComponent.loadCourses`: 2 → 1.

**Tradeoff:** Two parallel calls are cheap; the real win is conceptual ("one source of truth for the dashboard"). If we want to minimize change, leave `?type=` as-is — it's not actually broken.

**Recommendation:** Lower priority. Do this only if/when adding more dashboard data (e.g., pending-approvals badge, recent activity), at which point a `/me/dashboard` aggregate is justified.

---

## 7. Per-Screen Migration Mapping

For each screen, the exact before/after:

### Dashboard (`/`)
- **Before:** `courseService.list('enrolled')` + `courseService.list('created')` (2 parallel)
- **After (optional):** `meService.getCourses()` (1)
- **Component file:** `lib4gz-fe/src/app/components/home/user-dashboard/user-dashboard.component.ts`

### Course Layout (`/course/:id/*`)
- **Before:** `forkJoin({ courseService.get, moduleService.listByCourse, enrollmentService.getMyEnrollment })` + lazy `lessonService.listByModule(modules[0])` (4 RTTs)
- **After:** `courseService.getSyllabus(courseId)` (1 RTT, includes `myEnrollment` and `lessons` per module)
- **Component file:** `lib4gz-fe/src/app/components/course/course-layout/course-layout.component.ts`

### Course Home (`/course/:id`)
- **Before:** Repeats Course Layout's 3 calls (3 RTTs)
- **After:** Reads from the layout component's syllabus signal (0 extra RTTs)
- **Component file:** `lib4gz-fe/src/app/components/course/course-home/course-home.component.ts`
- **Note:** Make Course Layout's syllabus accessible to children via parent component injection (already done for `lesson` and `isTeacher`).

### Course Lesson + Lesson Summary + Lesson Exercises tabs
- **Before:** `lessonService.get` + `summaryService.get` + `exerciseService.listByLesson` + N × `submissionService.getMySubmission` (1 + 1 + 1 + N)
- **After:** `lessonService.get(id, { expand: ['summary', 'exercises'] })` returns lesson, summary, and exercises (each with `mySubmissionStatus` already populated) in one call. (1)
- **Component files:**
  - `course-lesson/course-lesson.component.ts` — orchestrates
  - `lesson-summary/lesson-summary.component.ts` — reads from parent
  - `lesson-exercises/lesson-exercises.component.ts` — reads from parent

### Lesson Submissions tab (teacher)
- **Before:** `exerciseService.listByLesson` + N × `submissionService.listByExercise` + N × `questionService.listByExercise` (on expand) + M × `submissionService.get` (on drawer) — `1 + 3N + M`
- **After:** `submissionService.listByLesson(lessonId, { expand: ['answers', 'questions'] })` (1)
- **Component file:** `lib4gz-fe/src/app/components/course/lesson-submissions/lesson-submissions.component.ts`

### Exercise Attempt (`/course/:id/lesson/:lessonId/attempt`)
- **Before:** `exerciseService.listByLesson` + N × `questionService.listByExercise` + N × `submissionService.getMySubmission` (`1 + 2N`)
- **After:** `exerciseService.listByLesson(lessonId, { expand: ['questions', 'mySubmission'] })` (1)
- **Component file:** `lib4gz-fe/src/app/components/course/exercise-attempt/exercise-attempt.component.ts`

### Course Material (teacher) (`/course/:id/manage/material`)
- **Before:** `moduleService.listByCourse` + per-module `lessonService.listByModule` (1 + N, lazy)
- **After:** `courseService.getSyllabus(courseId)` (1, eager). All modules and their lessons available immediately; no need to lazy-load on expand.
- **Component file:** `lib4gz-fe/src/app/components/course-teacher/course-material/course-material.component.ts`
- **Note:** The "lessonsLoaded" flag becomes unnecessary; all lessons are present.

### Lesson Detail Management (`/course/:id/manage/material/lesson/:lessonId`)
- **Before:** `lessonService.get` → chained `moduleService.get` + `summaryService.get` + `exerciseService.listByLesson` (4 RTTs, 2 chained)
- **After:** `lessonService.get(lessonId, { expand: ['summary', 'exercises', 'module'] })` (1 RTT). Or: with field denormalization E (`moduleTitle` on `LessonResponse`), drop `expand=module`.
- **Component file:** `lib4gz-fe/src/app/components/course-teacher/course-management/lesson-detail-management/lesson-detail-management.component.ts`

### Manage Layout (teacher)
- **Before:** `courseService.get` + `enrollmentService.listByCourse` (2)
- **After:** Unchanged. Acceptable.

### Exercise Editor
- **Before:** `exerciseService.get` + `questionService.listByExercise` (2 parallel)
- **After:** Unchanged for now. Could add `?expand=questions` later for consistency; ROI is small.

---

## 8. Implementation Sequencing

Four phases, each independently shippable. No phase requires the next.

### Phase 1 — Backend additive field denormalization (Tier 2)
**Scope:** Backend only. No API breaking changes. Frontend keeps working.

- [ ] Add `moduleTitle` to `LessonResponse` — populated in `LessonMapper`.
- [ ] Add `mySubmissionStatus: SubmissionStatus?` and `mySubmissionId: String?` to `ExerciseResponse`. Mapper takes `userId` as input. The list/get endpoints pass the authenticated user ID through.
- [ ] Add `myEnrollment: EnrollmentSummary?` to `CourseResponse`. Mapper looks up by `(courseId, userId)`.
- [ ] Define `EnrollmentSummary` DTO: `{ id, role, status, joinedAt }`.

**Risk:** Low. Additive only. Mappers need a `userId` parameter where they currently lack one; services already have it from `X-User-Id`.

### Phase 2 — Backend `?expand=` support on existing endpoints
**Scope:** Backend only. Backwards compatible (no expand = today's behavior).

- [ ] `GET /v1/lessons/{id}?expand=summary,exercises,module` — three expand tokens.
- [ ] `GET /v1/lessons/{id}/exercises?expand=questions,mySubmission` — two expand tokens.
- [ ] Introduce a shared `ExpandTokens` parser utility in `common/utils/`.
- [ ] Update OpenAPI/spec docs in `lib4gz-fe/documents/network/api-endpoints.yaml`.

**Risk:** Low–medium. Need to design DTO unions cleanly. Consider using nullable fields rather than polymorphic responses for simplicity.

### Phase 3 — Backend composite aggregate endpoints (Tier 1)
**Scope:** Backend only. New URLs; old endpoints still serve.

- [ ] `GET /v1/courses/{courseId}/syllabus` → new `SyllabusController` (or method on `CourseController`).
- [ ] `GET /v1/lessons/{lessonId}/submissions` (teacher-only) → new method on `SubmissionController` or new `LessonSubmissionsController`.
- [ ] `GET /v1/me/courses` (optional) → new method on `UserController` or new `MeController`.
- [ ] Update `security.json`:
  - `/v1/courses/*/syllabus` → AUTH (enrollment check in service)
  - `/v1/lessons/*/submissions` → AUTH with TEACHER role check in service
  - `/v1/me/courses` → AUTH

**Risk:** Medium. Authorization edge cases (private course + unenrolled caller; pending enrollment; etc.) need careful spec.

### Phase 4 — Frontend migration, one screen at a time
**Scope:** Frontend only. Each PR migrates one screen.

Order of migration (highest ROI first):
1. Exercise Attempt → `exerciseService.listByLesson` with `expand=questions,mySubmission` (Phase 2 only needed)
2. Lesson Submissions tab → `submissionService.listByLesson` (Phase 3 needed)
3. Course Layout + Course Home → `courseService.getSyllabus` (Phase 3 needed)
4. Course Material → `courseService.getSyllabus` (reuses #3)
5. Lesson Detail Management → `lessonService.get` with `expand=summary,exercises` (Phase 2 needed); use `moduleTitle` from Phase 1
6. Lesson Exercises tab → use `mySubmissionStatus` field from Phase 1 (no backend changes after Phase 1)
7. Dashboard (optional) → `meService.getCourses` (Phase 3 needed)

### Phase 5 (later) — Deprecate unused legacy endpoints
Once production traffic on the granular endpoints drops below a threshold (e.g., 1% of total), deprecate and eventually remove:
- `GET /v1/courses/{id}/my-enrollment` — replaced by `myEnrollment` field in `CourseResponse` / `syllabus`
- Possibly `GET /v1/lessons/{id}/summary` — if every caller uses `?expand=summary`

Mutations (`POST`, `PATCH`, `DELETE`, `approve`, `reject`, `submit`, etc.) stay forever. Do not deprecate.

---

## 9. Backend Implementation Notes

### Where to put the new code
The codebase uses feature-sliced packages: `com.example.lib4gz.{auth,courses,exercise}`. Two reasonable options:

**Option A — Per-feature aggregation controllers:**
- `courses/controller/SyllabusController.kt`
- `exercise/controller/LessonSubmissionsController.kt`
- `auth/controller/MeController.kt`

**Option B — Add aggregate methods to existing controllers:**
- Add `getSyllabus(...)` to `CourseController`
- Add `listSubmissionsByLesson(...)` to `SubmissionController`
- Add `listMyCourses(...)` to `UserController`

**Recommendation:** Option B for `/syllabus` and `/me/courses` (they fit naturally as methods on the existing resource controllers). Option A for the lesson-submissions aggregate (it's cross-cutting between lessons and submissions; a dedicated controller is cleaner).

### Service-layer joins
Aggregate endpoints should fan out to **multiple repositories in parallel** server-side, not perform N+1 in the service layer:

```kotlin
// SyllabusService.getSyllabus(courseId, userId)
return Mono.zip(
    courseRepo.findByIdWithCreator(courseId),
    moduleRepo.findByCourseIdWithLessons(courseId),  // single JOIN query
    enrollmentRepo.findByCourseIdAndUserId(courseId, userId)
).map { (course, modulesWithLessons, enrollment) -> /* assemble */ }
```

The `findByCourseIdWithLessons` repository method should use a single SQL query with a JOIN, ordered by `module.orderIndex, lesson.orderIndex`. Avoid `for module in modules: findLessonsByModuleId(...)` which just moves the N+1 server-side.

### ExpandTokens utility
Suggested signature in `common/utils/`:

```kotlin
class ExpandTokens(private val tokens: Set<String>) {
    fun has(token: String): Boolean = tokens.contains(token)

    companion object {
        fun parse(raw: String?): ExpandTokens =
            ExpandTokens(raw?.split(",")?.map { it.trim().lowercase() }?.toSet() ?: emptySet())
    }
}
```

Controller usage:
```kotlin
@GetMapping("/lessons/{id}")
fun getLesson(
    @PathVariable id: String,
    @RequestParam(required = false) expand: String?,
    @RequestHeader(PZRequestHeader.USER_ID) userId: String
): Mono<LessonResponse> =
    lessonService.getLesson(id, userId, ExpandTokens.parse(expand))
```

### Authorization
- `/syllabus` — caller must have ACTIVE enrollment OR be the course creator OR course is PUBLIC.
- `/lessons/{id}/submissions` — caller must have ACTIVE enrollment with role TEACHER on the course owning the lesson.
- `/me/courses` — any authenticated user.

Add these as service-level checks (the JSON whitelist in `security.json` should only mark them as AUTH-required).

### Caching
- All aggregates: return `ETag: W/"<hash>"` and honor `If-None-Match`.
- Hash inputs: max `updatedAt` of all included entities.
- `Cache-Control: private, must-revalidate` (because `myEnrollment` / `mySubmission` are caller-scoped).

### Response sizes
The syllabus for a large course (10 modules × 10 lessons) is roughly 100 rows × ~200 bytes JSON ≈ 20 KB. Acceptable. If we hit edge cases at >50 modules, add `?modulesPage=` later.

---

## 10. Frontend Implementation Notes

### Service additions
- `course.service.ts`: add `getSyllabus(courseId: string): Observable<SyllabusResponse>`.
- `lesson.service.ts`: extend `get(id, options?: { expand?: LessonExpand[] })`.
- `exercise.service.ts`: extend `listByLesson(lessonId, options?: { expand?: ExerciseExpand[] })`.
- `submission.service.ts`: add `listByLesson(lessonId: string, options?: { expand?: SubmissionExpand[] })`.
- (Optional) `user.service.ts` or new `me.service.ts`: add `getCourses()`.

### Model additions in `lib4gz-fe/src/app/shared/models/`
- `SyllabusResponse` interface.
- `LessonSubmissionsResponse` interface.
- Extend `LessonResponse` with `moduleTitle`, optional `module`, `summary`, `exercises`.
- Extend `ExerciseResponse` with `mySubmissionStatus`, `mySubmissionId`, optional `questions`, `mySubmission`.
- Extend `CourseResponse` with `myEnrollment`.

### State sharing between Course Layout and children
Today, `CourseLessonComponent` exposes `lesson` and `isTeacher` to children via Angular's component-DI (children `inject(CourseLessonComponent)`). Apply the same pattern at the `CourseLayoutComponent` level: expose `syllabus` signal; `CourseHomeComponent` reads from it instead of re-fetching.

### Spec doc updates
Per `lib4gz-fe/CLAUDE.md`, the project is spec-driven. Update:
- `documents/network/api-endpoints.yaml` — declare new endpoints and `?expand=` params.
- `documents/network/models.yaml` — declare new response models.
- Affected screen specs in `documents/screens/` — update "data dependencies" sections.

---

## 11. Anti-Patterns to Avoid

- ❌ **GraphQL-ish single `/data` endpoint.** Loses caching, auth scoping, observability. Use REST aggregates.
- ❌ **One endpoint per screen** (`/api/dashboard`, `/api/lesson-view`). Couples API to UI route names; rots when the UI redesigns.
- ❌ **Merging mutations into composite POSTs** (`POST /lesson-with-summary-and-exercises`). Different invariants, different failure modes. Keep writes granular.
- ❌ **Returning everything by default.** Make richness opt-in via `?expand=`. Default response stays lean.
- ❌ **Denormalizing volatile fields.** `mySubmissionStatus` is fine (cheap, OK to be a couple of seconds stale). Don't denormalize, e.g., "live online users count."
- ❌ **Server-side N+1.** When implementing aggregate endpoints, write JOIN queries — don't replace FE fan-out with BE fan-out.
- ❌ **Re-using the same DTO class with optional fields all the way down.** Where the shape is fundamentally different (e.g., `LessonSubmissionsResponse`), introduce a new DTO. Where it's the same entity with optional joins (e.g., `LessonResponse` with optional `summary`), reuse with nullable fields.

---

## 12. Open Questions / Decisions Needed

1. **Syllabus eagerness.** Should `/syllabus` include all lessons of all modules, or lazy-load lessons per module? Current proposal: eager. Tradeoff: ~20KB response for a 100-lesson course vs. eliminating all subsequent calls. Eager wins unless we expect courses >500 lessons.

2. **`/me/courses` priority.** Worth doing now, or defer? Current proposal: defer. The 2 parallel calls are cheap.

3. **Public courses in `/syllabus`.** Should unenrolled callers be able to GET `/syllabus` for PUBLIC courses (browse mode)? Affects authorization and the `myEnrollment: null` case.

4. **Should `LessonResponse.exercises` (when expanded) include `mySubmission`?** If yes, nested `?expand=exercises.mySubmission` syntax is needed. Recommendation: keep flat; the deep-expand case is rare and `GET /lessons/{id}/exercises?expand=...` covers it.

5. **Deprecation timeline for legacy endpoints.** Soft-deprecate (keep serving, mark in docs) for one release cycle, then remove? Or keep indefinitely? Recommendation: keep indefinitely. They're useful for invalidation after mutations and for admin tools.

6. **Should we add `If-Modified-Since` support on aggregates?** ETag covers most needs; `If-Modified-Since` is a minor addition. Recommendation: ETag only for v1.

7. **Pagination on `/me/courses`.** Likely fine without, even at 1000 courses per user. If we ever need it, add `?page=&limit=`.

---

## Appendix — Endpoint Inventory (Current)

For reference, the full set of backend endpoints today (45 total, all under `/api/v1`):

**Auth (4):** `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh-token`, `POST /users/{userId}/grant-teacher`

**Courses (6):** `GET /courses`, `POST /courses`, `GET /courses/{id}`, `GET /courses/code/{code}`, `PATCH /courses/{id}`, `DELETE /courses/{id}`

**Enrollments (7):** `POST /courses/{courseId}/enroll`, `GET /courses/{courseId}/enrollments`, `GET /courses/{courseId}/my-enrollment`, `PUT /enrollments/{id}`, `POST /enrollments/{id}/approve`, `POST /enrollments/{id}/reject`, `DELETE /enrollments/{id}`

**Modules (5):** `POST /courses/{courseId}/modules`, `GET /courses/{courseId}/modules`, `GET /modules/{id}`, `PATCH /modules/{id}`, `DELETE /modules/{id}`

**Lessons (5):** `POST /modules/{moduleId}/lessons`, `GET /modules/{moduleId}/lessons`, `GET /lessons/{id}`, `PATCH /lessons/{id}`, `DELETE /lessons/{id}`

**Summary (2):** `GET /lessons/{id}/summary`, `POST /lessons/{id}/summary`

**Exercises (5):** `POST /lessons/{lessonId}/exercises`, `GET /lessons/{lessonId}/exercises`, `GET /exercises/{id}`, `PATCH /exercises/{id}`, `DELETE /exercises/{id}`

**Questions (3):** `GET /exercises/{exerciseId}/questions`, `POST /exercises/{exerciseId}/questions`, `PATCH /exercises/{exerciseId}/questions`

**Submissions (8):** `GET /exercises/{exerciseId}/submissions`, `GET /exercises/{exerciseId}/my-submission`, `POST /exercises/{exerciseId}/submissions`, `GET /submissions/{id}`, `POST /submissions/{id}/submit`, `POST /submissions/{id}/approve`, `POST /submissions/{id}/revision`, `POST /answers/{answerId}/comment`

**Proposed additions: 3 new endpoints + extensions to 2 existing endpoints + 3 new response fields.**
