# Summary Feature Documentation

## 1. Overview

Lesson summaries provide rich text content for each lesson. There is exactly one summary per lesson (1:1 relationship). Summaries are versioned -- the `version` field increments with each update, and `editedBy` tracks the user who last modified the content. Unlike most other entities in the system, summaries are **not** soft-deleted.

---

## 2. Models

> Full model definitions: [models/summary.yaml](../models/summary.yaml)

### Entity
- **Summary** — `summaries` table, NOT soft-deleted, 1:1 with Lesson, versioned (version increments on update)

### DTOs
- **CreateSummaryRequest** — content (required)
- **UpdateSummaryRequest** — content (required)
- **SummaryResponse** — includes editedBy (UserSummary), version

### Mapper
- **SummaryMapper** — `@Component` with `toResponse(summary)`, uses `CourseMapper.toUserSummary()`

---

## 3. Repository

- **Annotation:** `@Repository`
- **Interface:** `SummaryRepo : JpaRepository<Summary, String>`

```kotlin
@Repository
interface SummaryRepo : JpaRepository<Summary, String> {

    fun findByLesson_Id(lessonId: String): Summary?

    fun existsByLesson_Id(lessonId: String): Boolean

    fun deleteByLesson_Id(lessonId: String)
}
```

## 4. Service

### Interface

```kotlin
interface SummaryService {
    fun getSummary(lessonId: String, userId: String): Mono<SummaryResponse>
    fun createSummary(lessonId: String, userId: String, request: CreateSummaryRequest): Mono<SummaryResponse>
    fun updateSummary(lessonId: String, userId: String, request: UpdateSummaryRequest): Mono<SummaryResponse>
    fun createOrUpdateSummary(lessonId: String, userId: String, content: String): Mono<SummaryResponse>
}
```

### Implementation

- **Dependencies:** `summaryRepo`, `lessonRepo`, `userRepo`, `enrollmentService`, `summaryMapper`

#### getSummary(lessonId, userId)
1. Fetch the `Lesson` entity from `lessonRepo`. Throw not found if missing.
2. Derive `courseId` from `lesson.module.course.id`.
3. Verify user is **enrolled** in the course via `enrollmentService.isEnrolledInCourse(userId, courseId)`. Throw forbidden if not.
4. Fetch the `Summary` entity via `summaryRepo.findByLesson_Id(lessonId)`. Throw not found if missing.
5. Map to `SummaryResponse` via `summaryMapper.toResponse(summary)` and return.

#### createSummary(lessonId, userId, request)
1. Fetch the `Lesson` entity from `lessonRepo`. Throw not found if missing.
2. Derive `courseId` from `lesson.module.course.id`.
3. Verify user is a **teacher** in the course via `enrollmentService.isTeacherInCourse(userId, courseId)`. Throw forbidden if not.
4. Check if a summary already exists for this lesson via `summaryRepo.existsByLesson_Id(lessonId)`. If it exists, throw a conflict/bad request error (use `createOrUpdateSummary` instead).
5. Fetch the `User` entity from `userRepo`. Throw not found if missing.
6. Create a new `Summary` entity:
   - `lesson` = the fetched lesson
   - `content` = `request.content`
   - `editedBy` = the fetched user
   - `version` = `1` (default)
7. Save via `summaryRepo.save(summary)`.
8. Map to `SummaryResponse` and return.

#### updateSummary(lessonId, userId, request)
1. Fetch the `Lesson` entity from `lessonRepo`. Throw not found if missing.
2. Derive `courseId` from `lesson.module.course.id`.
3. Verify user is a **teacher** in the course. Throw forbidden if not.
4. Fetch the existing `Summary` via `summaryRepo.findByLesson_Id(lessonId)`. Throw not found if missing.
5. Fetch the `User` entity from `userRepo`. Throw not found if missing.
6. Update the summary:
   - `summary.content` = `request.content`
   - `summary.editedBy` = the fetched user
   - `summary.version` = `summary.version + 1` (increment version)
7. Save via `summaryRepo.save(summary)`.
8. Map to `SummaryResponse` and return.

#### createOrUpdateSummary(lessonId, userId, content)
1. Fetch the `Lesson` entity from `lessonRepo`. Throw not found if missing.
2. Derive `courseId` from `lesson.module.course.id`.
3. Verify user is a **teacher** in the course. Throw forbidden if not.
4. Check if a summary exists via `summaryRepo.findByLesson_Id(lessonId)`.
5. **If summary exists (update path):**
   - Fetch the `User` entity from `userRepo`.
   - Update `summary.content` = `content`.
   - Update `summary.editedBy` = the fetched user.
   - Increment `summary.version` = `summary.version + 1`.
   - Save via `summaryRepo.save(summary)`.
6. **If summary does not exist (create path):**
   - Fetch the `User` entity from `userRepo`.
   - Create a new `Summary` entity with `lesson`, `content`, `editedBy`, and `version = 1`.
   - Save via `summaryRepo.save(summary)`.
7. Map the saved summary to `SummaryResponse` and return.

## 5. Controller

- **Annotations:** `@RestController`, `@RequestMapping("/v1")`

| Method | Path | Function Signature | Response Status |
|--------|------|--------------------|-----------------|
| `GET` | `/v1/lessons/{lessonId}/summary` | `getSummary(@PathVariable lessonId: String, @RequestHeader("userId") userId: String)` | `200 OK` |
| `POST` | `/v1/lessons/{lessonId}/summary` | `createOrUpdateSummary(@PathVariable lessonId: String, @RequestHeader("userId") userId: String, @RequestBody request: CreateSummaryRequest)` | `201 Created` |

### Controller annotations per endpoint

- `GET /v1/lessons/{lessonId}/summary` -> `@GetMapping("/lessons/{lessonId}/summary")`
- `POST /v1/lessons/{lessonId}/summary` -> `@PostMapping("/lessons/{lessonId}/summary")` with `@ResponseStatus(HttpStatus.CREATED)`

### Controller implementation detail

The `POST` endpoint receives a `CreateSummaryRequest` body (which contains `content`) and calls `summaryService.createOrUpdateSummary(lessonId, userId, request.content)`. This provides **upsert behavior** -- it creates a new summary if none exists, or updates the existing one.

## 6. Business Rules

1. **One summary per lesson:** The `lessonId` column has a `unique = true` constraint, enforcing the 1:1 relationship at the database level. Each lesson can have at most one summary.
2. **Versioning:** The `version` field starts at `1` on creation and increments by `1` on every update. This provides an audit trail of how many times the summary has been modified.
3. **editedBy tracking:** The `editedBy` field records the user who last created or modified the summary. It is updated on every write operation.
4. **Teacher-only for create/update:** Only users with the teacher role in the course can create or update summaries.
5. **Enrolled users can view:** Any user enrolled in the course (teacher or student) can retrieve a lesson's summary.
6. **POST endpoint is an upsert:** The `POST /v1/lessons/{lessonId}/summary` endpoint uses `createOrUpdateSummary` internally. If a summary already exists for the lesson, it updates the content, increments the version, and updates `editedBy`. If no summary exists, it creates a new one with `version = 1`.
7. **No soft delete:** Unlike modules and lessons, summaries do not use `@SQLDelete` or `@Where`. They are either present or absent. Deletion of a summary (if needed) would be a hard delete.
8. **Authorization via lesson traversal:** The course ID is derived by traversing `lesson.module.course.id`. This chain is used in every service method to check enrollment/teacher status.
9. **@PreUpdate lifecycle:** The `updatedAt` field is automatically refreshed to the current epoch millisecond timestamp before every update.
10. **UserSummary in response:** The `editedBy` field in `SummaryResponse` is a `UserSummary` DTO (not the full User entity), containing minimal user identification fields.
