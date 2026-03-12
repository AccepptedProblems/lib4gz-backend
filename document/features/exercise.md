# Exercise Feature

## Overview

Exercises within lessons - various types of assignments that students complete. Each exercise belongs to a lesson and has a type (text answer, code, file upload, multiple choice, or project link). Exercises are ordered within their lesson and support soft deletion.

## Models

> Full model definitions: [models/exercise.yaml](../models/exercise.yaml)

### Entity
- **Exercise** — `exercises` table, soft delete, unique constraint on (lessonId, orderIndex), JSONB settings

### Enums
- **ExerciseType**: TEXT_ANSWER, CODE, FILE_UPLOAD, MULTIPLE_CHOICE, PROJECT_LINK

### DTOs
- **CreateExerciseRequest** — title, type (required), settings, orderIndex?, questions?
- **UpdateExerciseRequest** — all fields optional
- **ExerciseResponse** — includes optional questionCount

### Mapper
- **ExerciseMapper** — `@Component` with `toResponse(exercise, questionCount?)`

## Repository

- **Interface:** `@Repository interface ExerciseRepo : JpaRepository<Exercise, String>`

### Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| findByLesson_IdOrderByOrderIndexAsc(lessonId: String) | List\<Exercise\> | All exercises for a lesson, ordered |
| findByLesson_Id(lessonId: String) | List\<Exercise\> | All exercises for a lesson (unordered) |
| @Query("SELECT MAX(e.orderIndex) FROM Exercise e WHERE e.lesson.id = :lessonId") findMaxOrderIndexByLessonId(lessonId: String) | Int? | Highest orderIndex in lesson |
| existsByLesson_IdAndOrderIndex(lessonId: String, orderIndex: Int) | Boolean | Check orderIndex uniqueness |
| countByLesson_Id(lessonId: String) | Long | Count exercises in lesson |
| @Query("SELECT e FROM Exercise e WHERE e.lesson.module.course.id = :courseId") findByCourseId(courseId: String) | List\<Exercise\> | All exercises in a course |

## Service

### Interface: ExerciseService

```kotlin
interface ExerciseService {
    fun createExercise(lessonId: String, userId: String, request: CreateExerciseRequest): Mono<ExerciseResponse>
    fun getExerciseById(exerciseId: String, userId: String): Mono<ExerciseResponse>
    fun updateExercise(exerciseId: String, userId: String, request: UpdateExerciseRequest): Mono<ExerciseResponse>
    fun deleteExercise(exerciseId: String, userId: String): Mono<Void>
    fun listLessonExercises(lessonId: String, userId: String): Flux<ExerciseResponse>
    fun getCourseIdByExercise(exerciseId: String): String
}
```

### Implementation Logic

- **Authorization:** Navigates `exercise.lesson.module.course.id` to derive the course ID for role-based authorization checks.
- **createExercise:** Teacher-only. Loads lesson. Auto-assigns `orderIndex` when null (uses `findMaxOrderIndexByLessonId + 1`, or 0 if none exist). Creates and saves Exercise entity. If `questions` list is provided, creates Question entities inline with auto-assigned orderIndex, and returns response with `questionCount` reflecting the created questions.
- **getExerciseById:** Enrolled users can view. Loads exercise and returns response.
- **updateExercise:** Teacher-only. Loads exercise, applies non-null fields from request, saves.
- **deleteExercise:** Teacher-only. Loads exercise and deletes (soft delete via @SQLDelete).
- **listLessonExercises:** Enrolled users can view. Returns all exercises for lesson ordered by orderIndex.
- **getCourseIdByExercise:** Synchronous helper. Loads exercise, returns `exercise.lesson.module.course.id`.

## Controller

- **Annotations:** `@RestController @RequestMapping("/v1")`

### Endpoints

| Method | Path | Function | Status |
|--------|------|----------|--------|
| POST | /v1/lessons/{lessonId}/exercises | createExercise | 201 |
| GET | /v1/lessons/{lessonId}/exercises | listLessonExercises | 200 |
| GET | /v1/exercises/{exerciseId} | getExercise | 200 |
| PATCH | /v1/exercises/{exerciseId} | updateExercise | 200 |
| DELETE | /v1/exercises/{exerciseId} | deleteExercise | 204 |

## Business Rules

1. **Teacher-only for CUD:** Only teachers can create, update, and delete exercises.
2. **Enrolled users can view:** Any enrolled user (teacher or student) can view exercises.
3. **orderIndex auto-assignment:** When `orderIndex` is null in CreateExerciseRequest, automatically assign the next available index (max + 1, or 0 if no exercises exist).
4. **Unique constraint:** `(lessonId, orderIndex)` must be unique - enforced at the database level.
5. **Soft delete:** Uses `@SQLDelete` with inline SQL timestamp function `(EXTRACT(EPOCH FROM NOW()) * 1000)::bigint` — NOT a bind parameter `?` for the timestamp, because Hibernate 6.x only passes the entity ID. `@Where(clause = "deleted_at IS NULL")` filters out deleted records automatically.
6. **Course ID derivation:** Course ID is derived by navigating the relationship chain: `exercise.lesson.module.course.id`.
7. **JSON settings:** The `settings` field is stored as JSONB in the database, allowing flexible exercise-type-specific configuration.
8. **Inline question creation:** When `questions` is provided in `CreateExerciseRequest`, questions are created atomically with the exercise in a single transaction. Each question's `orderIndex` is auto-assigned if not specified. This reuses the same `CreateQuestionItem` DTO from the question feature.
