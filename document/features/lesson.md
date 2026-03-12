# Lesson Feature Documentation

## 1. Overview

Lessons are the content delivery units within modules. Each lesson belongs to exactly one module, is ordered by index within that module, and can optionally have a one-to-one summary attached. Lessons serve as the leaf-level organizational structure within the course hierarchy: Course -> Module -> Lesson.

---

## 2. Models

> Full model definitions: [models/lesson.yaml](../models/lesson.yaml)

### Entity
- **Lesson** — `lessons` table, soft delete, unique constraint on (moduleId, orderIndex), optional 1:1 Summary

### DTOs
- **CreateLessonRequest** — title (required), orderIndex?
- **UpdateLessonRequest** — all fields optional
- **LessonResponse** — includes computed `hasSummary` (boolean) and `exerciseCount` (optional)

### Mapper
- **LessonMapper** — `@Component` with `toResponse(lesson, hasSummary, exerciseCount?)`

---

## 3. Repository

- **Annotation:** `@Repository`
- **Interface:** `LessonRepo : JpaRepository<Lesson, String>`

```kotlin
@Repository
interface LessonRepo : JpaRepository<Lesson, String> {

    fun findByModule_IdOrderByOrderIndexAsc(moduleId: String): List<Lesson>

    fun findByModule_Id(moduleId: String): List<Lesson>

    @Query("SELECT MAX(l.orderIndex) FROM Lesson l WHERE l.module.id = :moduleId")
    fun findMaxOrderIndexByModuleId(moduleId: String): Int?

    fun existsByModule_IdAndOrderIndex(moduleId: String, orderIndex: Int): Boolean

    fun countByModule_Id(moduleId: String): Long

    @Query("SELECT l FROM Lesson l WHERE l.module.course.id = :courseId")
    fun findByCourseId(courseId: String): List<Lesson>
}
```

## 4. Service

### Interface

```kotlin
interface LessonService {
    fun createLesson(moduleId: String, userId: String, request: CreateLessonRequest): Mono<LessonResponse>
    fun getLessonById(lessonId: String, userId: String): Mono<LessonResponse>
    fun updateLesson(lessonId: String, userId: String, request: UpdateLessonRequest): Mono<LessonResponse>
    fun deleteLesson(lessonId: String, userId: String): Mono<Void>
    fun listModuleLessons(moduleId: String, userId: String): Flux<LessonResponse>
}
```

### Implementation

- **Dependencies:** `lessonRepo`, `moduleRepo`, `enrollmentService`, `exerciseRepo` (for exerciseCount), `lessonMapper`

#### createLesson(moduleId, userId, request)
1. Fetch the `Module` entity from `moduleRepo`. Throw not found if missing.
2. Derive `courseId` from `module.course.id`.
3. Verify user is a **teacher** in the course via `enrollmentService.isTeacherInCourse(userId, courseId)`. Throw forbidden if not.
4. Determine `orderIndex`:
   - If `request.orderIndex` is **not null**, use it directly.
   - If `request.orderIndex` is **null**, auto-assign: `lessonRepo.findMaxOrderIndexByModuleId(moduleId)?.plus(1) ?: 0`.
5. Create a new `Lesson` entity with the module reference, title, and computed orderIndex.
6. Save via `lessonRepo.save(lesson)`.
7. Map to `LessonResponse` via `lessonMapper.toResponse(lesson, hasSummary = false)` and return (new lesson has no summary).

#### getLessonById(lessonId, userId)
1. Fetch the `Lesson` entity from `lessonRepo`. Throw not found if missing.
2. Derive `courseId` from `lesson.module.course.id`.
3. Verify user is **enrolled** in the course via `enrollmentService.isEnrolledInCourse(userId, courseId)`. Throw forbidden if not.
4. Compute `hasSummary` as `lesson.summary != null`.
5. Compute `exerciseCount` via the exercise repository (e.g., `exerciseRepo.countByLesson_Id(lessonId)`).
6. Map to `LessonResponse` with `hasSummary` and `exerciseCount` and return.

#### updateLesson(lessonId, userId, request)
1. Fetch the `Lesson` entity from `lessonRepo`. Throw not found if missing.
2. Derive `courseId` from `lesson.module.course.id`.
3. Verify user is a **teacher** in the course. Throw forbidden if not.
4. If `request.title` is not null, update `lesson.title`.
5. If `request.orderIndex` is not null, update `lesson.orderIndex`.
6. Save via `lessonRepo.save(lesson)`.
7. Compute `hasSummary` as `lesson.summary != null`.
8. Map to `LessonResponse` and return.

#### deleteLesson(lessonId, userId)
1. Fetch the `Lesson` entity from `lessonRepo`. Throw not found if missing.
2. Derive `courseId` from `lesson.module.course.id`.
3. Verify user is a **teacher** in the course. Throw forbidden if not.
4. Delete via `lessonRepo.delete(lesson)` (triggers `@SQLDelete` soft delete).
5. Return `Mono.empty()`.

#### listModuleLessons(moduleId, userId)
1. Fetch the `Module` entity from `moduleRepo`. Throw not found if missing.
2. Derive `courseId` from `module.course.id`.
3. Verify user is **enrolled** in the course. Throw forbidden if not.
4. Fetch all lessons via `lessonRepo.findByModule_IdOrderByOrderIndexAsc(moduleId)`.
5. For each lesson, compute `hasSummary` as `lesson.summary != null`.
6. Map each to `LessonResponse` via `lessonMapper.toResponse(lesson, hasSummary)`.
7. Return as `Flux`.

## 5. Controller

- **Annotations:** `@RestController`, `@RequestMapping("/v1")`

| Method | Path | Function Signature | Response Status |
|--------|------|--------------------|-----------------|
| `POST` | `/v1/modules/{moduleId}/lessons` | `createLesson(@PathVariable moduleId: String, @RequestHeader("userId") userId: String, @RequestBody request: CreateLessonRequest)` | `201 Created` |
| `GET` | `/v1/modules/{moduleId}/lessons` | `listModuleLessons(@PathVariable moduleId: String, @RequestHeader("userId") userId: String)` | `200 OK` |
| `GET` | `/v1/lessons/{lessonId}` | `getLesson(@PathVariable lessonId: String, @RequestHeader("userId") userId: String)` | `200 OK` |
| `PATCH` | `/v1/lessons/{lessonId}` | `updateLesson(@PathVariable lessonId: String, @RequestHeader("userId") userId: String, @RequestBody request: UpdateLessonRequest)` | `200 OK` |
| `DELETE` | `/v1/lessons/{lessonId}` | `deleteLesson(@PathVariable lessonId: String, @RequestHeader("userId") userId: String)` | `204 No Content` |

### Controller annotations per endpoint

- `POST /v1/modules/{moduleId}/lessons` -> `@PostMapping("/modules/{moduleId}/lessons")` with `@ResponseStatus(HttpStatus.CREATED)`
- `GET /v1/modules/{moduleId}/lessons` -> `@GetMapping("/modules/{moduleId}/lessons")`
- `GET /v1/lessons/{lessonId}` -> `@GetMapping("/lessons/{lessonId}")`
- `PATCH /v1/lessons/{lessonId}` -> `@PatchMapping("/lessons/{lessonId}")`
- `DELETE /v1/lessons/{lessonId}` -> `@DeleteMapping("/lessons/{lessonId}")` with `@ResponseStatus(HttpStatus.NO_CONTENT)`

## 6. Business Rules

1. **Teacher-only for mutations:** Only users with the teacher role in the course can create, update, or delete lessons.
2. **Enrolled users can view:** Any user enrolled in the course (teacher or student) can retrieve individual lessons or list all lessons for a module.
3. **orderIndex auto-assignment:** When `orderIndex` is `null` in the create request, it is automatically assigned as `MAX(orderIndex) + 1` for the module, or `0` if no lessons exist yet in the module.
4. **Unique constraint on (moduleId, orderIndex):** The database enforces that no two lessons in the same module can share the same `orderIndex`. Violations result in a constraint error.
5. **Soft delete:** Lessons are soft-deleted via `@SQLDelete` with inline SQL timestamp function `(EXTRACT(EPOCH FROM NOW()) * 1000)::bigint` — NOT a bind parameter `?` for the timestamp, because Hibernate 6.x only passes the entity ID. `@Where(clause = "deleted_at IS NULL")` ensures soft-deleted lessons are excluded from all standard queries.
6. **Course ID derivation for authorization:** The course ID is derived by traversing `lesson.module.course.id`. This chain is used in every service method to check enrollment/teacher status.
7. **hasSummary field:** Computed as `lesson.summary != null`. This is a derived boolean, not stored in the database.
8. **exerciseCount field:** Computed from the exercise repository count query. Included in single-lesson retrieval but may be omitted (null) in list responses for performance.
9. **@PreUpdate lifecycle:** The `updatedAt` field is automatically refreshed to the current epoch millisecond timestamp before every update.
