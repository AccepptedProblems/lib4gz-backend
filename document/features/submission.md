# Submission Feature

## Overview

Student submissions and answers - includes a draft/submit/approve/revision workflow. Covers the Submission entity (one per exercise per user), the StudentAnswer entity (individual answers to questions), and teacher comments on answers. Submissions are NOT soft-deleted.

## Models

> Full model definitions: [models/submission.yaml](../models/submission.yaml)

### Entities
- **Submission** — `submissions` table, NOT soft-deleted, unique constraint on (exerciseId, userId), state machine methods (submit/approve/requestRevision)
- **StudentAnswer** — `student_answers` table, linked to Submission and Question

### Enums
- **SubmissionStatus**: DRAFT, SUBMITTED, APPROVED, NEEDS_REVISION

### DTOs
- **CreateSubmissionRequest** / **UpdateSubmissionRequest** — contain list of AnswerRequest
- **AnswerRequest** — questionId, answer?
- **SubmissionResponse** — includes user (UserSummary), status, optional answers list
- **StudentAnswerResponse** — includes questionContent (populated from Question entity)
- **RevisionRequest** / **CommentRequest** — defined in SubmissionController.kt (not payload file)

### Mapper
- **SubmissionMapper** — `@Component` with `toResponse(submission, answers?, questions?)`, uses question map for questionContent

## Repositories

### SubmissionRepo

```kotlin
@Repository
interface SubmissionRepo : JpaRepository<Submission, String>
```

| Method | Return Type | Description |
|--------|-------------|-------------|
| findByExercise_Id(exerciseId: String) | List\<Submission\> | All submissions for exercise |
| findByUser_Id(userId: String) | List\<Submission\> | All submissions by user |
| findByExercise_IdAndUser_Id(exerciseId: String, userId: String) | Submission? | User's submission for exercise |
| existsByExercise_IdAndUser_Id(exerciseId: String, userId: String) | Boolean | Check if submission exists |
| findByExercise_IdAndStatus(exerciseId: String, status: SubmissionStatus) | List\<Submission\> | Submissions filtered by status |
| findByUser_IdAndStatus(userId: String, status: SubmissionStatus) | List\<Submission\> | User's submissions filtered by status |
| @Query("...WHERE exercise.lesson.module.course.id = :courseId") findByCourseId(courseId: String) | List\<Submission\> | All submissions in a course |
| @Query("...WHERE exercise.lesson.module.course.id = :courseId AND user.id = :userId") findByCourseIdAndUserId(courseId: String, userId: String) | List\<Submission\> | User's submissions in a course |
| countByExercise_IdAndStatus(exerciseId: String, status: SubmissionStatus) | Long | Count submissions by status |

### StudentAnswerRepo

```kotlin
@Repository
interface StudentAnswerRepo : JpaRepository<StudentAnswer, String>
```

| Method | Return Type | Description |
|--------|-------------|-------------|
| findBySubmission_Id(submissionId: String) | List\<StudentAnswer\> | All answers for submission |
| findByQuestion_Id(questionId: String) | List\<StudentAnswer\> | All answers for question |
| findBySubmission_IdAndQuestion_Id(submissionId: String, questionId: String) | StudentAnswer? | Specific answer |
| existsBySubmission_IdAndQuestion_Id(submissionId: String, questionId: String) | Boolean | Check if answer exists |
| deleteBySubmission_Id(submissionId: String) | - | Delete all answers for submission |
| @Query("...ORDER BY question.orderIndex ASC") findBySubmissionIdOrderedByQuestion(submissionId: String) | List\<StudentAnswer\> | Answers ordered by question order |
| countBySubmission_Id(submissionId: String) | Long | Count answers in submission |

## Service

### Interface: SubmissionService

```kotlin
interface SubmissionService {
    fun createOrUpdateSubmission(exerciseId: String, userId: String, request: CreateSubmissionRequest): Mono<SubmissionResponse>
    fun getSubmission(submissionId: String, userId: String): Mono<SubmissionResponse>
    fun getUserSubmissionForExercise(exerciseId: String, userId: String): Mono<SubmissionResponse>
    fun listExerciseSubmissions(exerciseId: String, userId: String): Flux<SubmissionResponse>
    fun submitForReview(submissionId: String, userId: String): Mono<SubmissionResponse>
    fun approveSubmission(submissionId: String, teacherId: String): Mono<SubmissionResponse>
    fun requestRevision(submissionId: String, teacherId: String, feedback: String?): Mono<SubmissionResponse>
    fun addTeacherComment(answerId: String, teacherId: String, comment: String): Mono<SubmissionResponse>
}
```

### Implementation Logic

- **createOrUpdateSubmission:** Upsert pattern. Finds existing submission for exercise+user pair using `findByExercise_IdAndUser_Id`, or creates a new one. Answers can be updated regardless of submission status (no status restriction). For each `AnswerRequest`, finds existing `StudentAnswer` by `submissionId + questionId` or creates a new one. Sets the `answer` field. Saves all and returns full response.

- **getSubmission:** Loads submission by ID. Returns full response with answers included.

- **getUserSubmissionForExercise:** Finds submission by exercise ID + user ID. Returns full response.

- **listExerciseSubmissions:** Role-aware. Teachers see ALL submissions for the exercise. Learners see only their own submission.

- **submitForReview:** Student-only. Loads submission, verifies the user owns it. Calls `submission.submit()` which transitions to SUBMITTED (no status restriction — users can resubmit from any status). Saves and returns response.

- **approveSubmission:** Teacher-only. Loads submission. Calls `submission.approve()` which validates SUBMITTED status and transitions to APPROVED. Sets `approvedAt`. Saves and returns response.

- **requestRevision:** Teacher-only. Loads submission. Calls `submission.requestRevision()` which validates SUBMITTED status and transitions to NEEDS_REVISION. The `feedback` parameter currently exists but is NOT stored on the Submission entity. Saves and returns response.

- **addTeacherComment:** Teacher-only. Finds `StudentAnswer` by ID. Sets `teacherComment` field to the provided comment. Saves the answer. Returns the full submission response (loads the parent submission and all its answers).

- **Private helper - buildSubmissionResponse:** Loads answers ordered by question orderIndex using `findBySubmissionIdOrderedByQuestion`. Maps question IDs to Question objects to populate `questionContent` in `StudentAnswerResponse`. Returns complete `SubmissionResponse`.

## Controller

- **Annotations:** `@RestController @RequestMapping("/v1")`
- **NOTE:** `RevisionRequest` and `CommentRequest` data classes are defined IN this controller file (not in the payload file).

### Endpoints

| Method | Path | Function | Request Body | Status |
|--------|------|----------|-------------|--------|
| GET | /v1/exercises/{exerciseId}/submissions | listExerciseSubmissions | - | 200 |
| GET | /v1/exercises/{exerciseId}/my-submission | getMySubmission (calls getUserSubmissionForExercise) | - | 200 |
| POST | /v1/exercises/{exerciseId}/submissions | createOrUpdateSubmission | CreateSubmissionRequest | 201 |
| GET | /v1/submissions/{submissionId} | getSubmission | - | 200 |
| POST | /v1/submissions/{submissionId}/submit | submitForReview | - | 200 |
| POST | /v1/submissions/{submissionId}/approve | approveSubmission | - | 200 |
| POST | /v1/submissions/{submissionId}/revision | requestRevision | @RequestBody(required = false) RevisionRequest? | 200 |
| POST | /v1/answers/{answerId}/comment | addTeacherComment | CommentRequest | 200 |

## Submission Workflow State Machine

```
DRAFT --> SUBMITTED --> APPROVED
                   \--> NEEDS_REVISION --> SUBMITTED (resubmit)
(Any status) --> SUBMITTED (user can resubmit anytime)
```

- **DRAFT:** Initial state. Student can edit answers freely.
- **SUBMITTED:** Student has submitted for review. Awaiting teacher action.
- **APPROVED:** Teacher approved the submission. Student can still resubmit.
- **NEEDS_REVISION:** Teacher requested changes. Student can edit answers and resubmit.

### Valid Transitions

| From | To | Actor | Method |
|------|----|-------|--------|
| Any status | SUBMITTED | Student | submit() |
| SUBMITTED | APPROVED | Teacher | approve() |
| SUBMITTED | NEEDS_REVISION | Teacher | requestRevision() |

## Business Rules

1. **One submission per exercise per user:** Enforced by unique constraint on `(exerciseId, userId)`.
2. **Upsert pattern:** POST to create or update. If a submission already exists for the exercise+user pair, it updates instead of creating a new one.
3. **Answers always editable:** Answers can be modified regardless of submission status. Users can update and resubmit at any time.
4. **submittedAt preserved on resubmission:** The `submittedAt` timestamp is only set on the first submission. When a student resubmits after revision, the original `submittedAt` is preserved.
5. **approvedAt set on approval:** The `approvedAt` timestamp is set when the teacher approves the submission.
6. **Teacher comment on StudentAnswer:** Comments are stored on individual `StudentAnswer` records, not on the Submission entity itself.
7. **RevisionRequest feedback not stored:** The `feedback` parameter in `requestRevision` exists in the API but is NOT persisted on the Submission entity.
8. **RevisionRequest body is optional:** The `@RequestBody(required = false)` annotation means the request body can be omitted entirely.
9. **No soft delete:** Submissions do not use `@SQLDelete` or `@Where`. They are not soft-deleted.
10. **Visibility:** Teachers see all submissions for an exercise. Learners see only their own submission.
11. **Status validation:** Teacher actions (approve, requestRevision) validate the current status before transitioning. Student submit has no status restriction — users can resubmit from any status.
