# Question Feature

## Overview

Questions within exercises - supports batch create/update/delete operations. Teachers see all questions (including HIDDEN), learners see only VISIBLE ones. The Question entity uses a plain String field (TypeID format) for `exerciseId` rather than a JPA relationship, giving manual control over the association.

## Models

> Full model definitions: [models/question.yaml](../models/question.yaml)

### Entity
- **Question** — `questions` table, soft delete. **IMPORTANT:** `exerciseId` is a plain String column (TypeID format), NOT a JPA relationship

### Enums
- **QuestionVisibility**: VISIBLE, HIDDEN
- **QuestionAction**: CREATE, UPDATE, DELETE (used in batch operations)

### DTOs
- **CreateQuestionsRequest** / **CreateQuestionItem** — batch create
- **UpdateQuestionsRequest** / **UpdateQuestionItem** — batch update with action field (CREATE/UPDATE/DELETE)
- **CreateQuestionRequest** / **UpdateQuestionRequest** — single operations
- **QuestionResponse** — single question
- **QuestionsResponse** — batch result with created/updated/deleted lists

### Mapper
- **QuestionMapper** — `@Component` with `toResponse(question)`

## Repository

- **Interface:** `@Repository interface QuestionRepo : JpaRepository<Question, String>`
- **NOTE:** Uses `exerciseId` directly (not `findByExercise_Id`) because it is a plain String field (TypeID format), not a JPA relationship.

### Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| findByExerciseIdOrderByOrderIndexAsc(exerciseId: String) | List\<Question\> | All questions for exercise, ordered |
| findByExerciseId(exerciseId: String) | List\<Question\> | All questions for exercise (unordered) |
| @Query("SELECT MAX(q.orderIndex) FROM Question q WHERE q.exerciseId = :exerciseId") findMaxOrderIndexByExerciseId(exerciseId: String) | Int? | Highest orderIndex in exercise |
| existsByExerciseIdAndOrderIndex(exerciseId: String, orderIndex: Int) | Boolean | Check orderIndex existence |
| countByExerciseId(exerciseId: String) | Long | Count questions in exercise |
| findByExerciseIdAndVisibility(exerciseId: String, visibility: QuestionVisibility) | List\<Question\> | Questions filtered by visibility |

## Service

### Interface: QuestionService

```kotlin
interface QuestionService {
    fun createQuestions(exerciseId: String, userId: String, request: CreateQuestionsRequest): Flux<QuestionResponse>
    fun updateQuestions(exerciseId: String, userId: String, request: UpdateQuestionsRequest): Mono<QuestionsResponse>
    fun getQuestionsByExercise(exerciseId: String, userId: String): Flux<QuestionResponse>
    fun getVisibleQuestionsByExercise(exerciseId: String, userId: String): Flux<QuestionResponse>
}
```

### Implementation Logic

- **createQuestions:** Teacher-only. Batch creates all questions from the request list. For each item, auto-assigns `orderIndex` when null (incrementing from current max). Sets `exerciseId` on each Question entity. Saves all and returns responses.

- **updateQuestions:** Teacher-only. Processes each `UpdateQuestionItem` by its `action` field:
  - `CREATE`: Creates a new question (same logic as createQuestions for individual items). Auto-assigns orderIndex when null.
  - `UPDATE`: Loads existing question by `questionId`. Validates the question belongs to the specified exercise. Applies non-null fields from the item. Saves.
  - `DELETE`: Loads existing question by `questionId`. Validates it belongs to the exercise. Deletes (soft delete via @SQLDelete).
  - Returns `QuestionsResponse` with separate `created`, `updated`, and `deleted` lists.

- **getQuestionsByExercise:** Role-aware. Teachers see ALL questions (including HIDDEN). Learners see only VISIBLE questions. Determines role by checking course enrollment/ownership through the exercise's lesson chain.

- **getVisibleQuestionsByExercise:** Always returns only VISIBLE questions regardless of role. Uses `findByExerciseIdAndVisibility(exerciseId, QuestionVisibility.VISIBLE)`.

## Controller

- **Annotations:** `@RestController @RequestMapping("/v1")`

### Endpoints

| Method | Path | Function | Request Body | Status |
|--------|------|----------|-------------|--------|
| GET | /v1/exercises/{exerciseId}/questions | getQuestions | - | 200 |
| POST | /v1/exercises/{exerciseId}/questions | createQuestions | CreateQuestionsRequest (batch) | 201 |
| PATCH | /v1/exercises/{exerciseId}/questions | updateQuestions | UpdateQuestionsRequest (batch with actions) | 200 |

## Business Rules

1. **Teacher-only for mutations:** Only teachers can create and update/delete questions.
2. **Visibility-based access:** Teachers see all questions (including HIDDEN). Learners see only VISIBLE questions.
3. **Batch operations:** The update endpoint supports CREATE, UPDATE, and DELETE actions in a single request via the `action` field on each `UpdateQuestionItem`.
4. **Question-exercise validation:** When updating or deleting, each question is validated to belong to the specified exercise.
5. **orderIndex auto-assignment:** On create, when `orderIndex` is null, automatically assign the next available index.
6. **No JPA relationship for exerciseId:** The `exerciseId` field is a plain String column (TypeID format), not a `@ManyToOne` relationship. This means repository methods use `findByExerciseId` (not `findByExercise_Id`).
7. **Soft delete:** Uses `@SQLDelete` with inline SQL timestamp function `(EXTRACT(EPOCH FROM NOW()) * 1000)::bigint` — NOT a bind parameter `?` for the timestamp, because Hibernate 6.x only passes the entity ID. `@Where(clause = "deleted_at IS NULL")` filters out deleted records.
8. **JSON meta field:** The `meta` field is stored as JSONB, allowing flexible question-type-specific metadata (e.g., multiple choice options, code templates).
