# Enrollment Feature

## 1. Overview

Enrollment management feature handling the lifecycle of user enrollments in courses: requesting enrollment, approving, rejecting, updating, and removing enrollments. Enrollments are the primary mechanism controlling access to courses. Each user can have at most one enrollment per course (enforced by a unique constraint). Course creators automatically receive a TEACHER enrollment. Only teachers can approve, reject, or manage other users' enrollments.

---

## 2. Models

> Full model definitions: [models/enrollment.yaml](../models/enrollment.yaml)

### Entity
- **Enrollment** — `enrollments` table, hard delete (NOT soft-deleted), unique constraint on (courseId, userId)

### Enums
- **EnrollmentRole**: LEARNER, TEACHER
- **EnrollmentStatus**: PENDING, ACTIVE, REJECTED, INACTIVE

### DTOs
- **EnrollmentRequest** — optional role (default LEARNER)
- **UpdateEnrollmentRequest** — optional role, status
- **EnrollmentResponse** — includes courseId, courseName, user (UserSummary), role, status, joinedAt?

### Mapper
- **EnrollmentMapper** — `@Component` using `CourseMapper.toUserSummary()`

---

## 3. Repository

**Package:** `com.example.lib4gz.courses.repo`
**File:** `EnrollmentRepo.kt`

```kotlin
package com.example.lib4gz.courses.repo

import com.example.lib4gz.courses.model.entity.Enrollment
import com.example.lib4gz.courses.model.entity.EnrollmentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface EnrollmentRepo : JpaRepository<Enrollment, String> {

    fun findByCourse_Id(courseId: String): List<Enrollment>

    fun findByUser_Id(userId: String): List<Enrollment>

    fun findByCourse_IdAndUser_Id(courseId: String, userId: String): Enrollment?

    fun existsByCourse_IdAndUser_Id(courseId: String, userId: String): Boolean

    fun findByCourse_IdAndStatus(courseId: String, status: EnrollmentStatus): List<Enrollment>

    @Query("SELECT e FROM Enrollment e WHERE e.course.id = :courseId AND e.user.id = :userId AND e.status = 'ACTIVE'")
    fun findActiveEnrollment(courseId: String, userId: String): Enrollment?

    fun countByCourse_IdAndStatus(courseId: String, status: EnrollmentStatus): Long
}
```

---

## 4. Service

**Package:** `com.example.lib4gz.courses.service`
**File:** `EnrollmentService.kt` (interface and implementation in same file)

### Interface

```kotlin
package com.example.lib4gz.courses.service

import com.example.lib4gz.courses.model.payload.EnrollmentRequest
import com.example.lib4gz.courses.model.payload.EnrollmentResponse
import com.example.lib4gz.courses.model.payload.UpdateEnrollmentRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface EnrollmentService {

    fun requestEnrollment(courseId: String, userId: String, request: EnrollmentRequest): Mono<EnrollmentResponse>

    fun approveEnrollment(enrollmentId: String, teacherId: String): Mono<EnrollmentResponse>

    fun rejectEnrollment(enrollmentId: String, teacherId: String): Mono<EnrollmentResponse>

    fun updateEnrollment(enrollmentId: String, userId: String, request: UpdateEnrollmentRequest): Mono<EnrollmentResponse>

    fun removeEnrollment(enrollmentId: String, userId: String): Mono<Void>

    fun listCourseEnrollments(courseId: String, userId: String): Flux<EnrollmentResponse>

    fun getUserEnrollment(courseId: String, userId: String): Mono<EnrollmentResponse>

    // These remain synchronous as they're used internally by other services
    fun isTeacherInCourse(courseId: String, userId: String): Boolean

    fun isEnrolledInCourse(courseId: String, userId: String): Boolean
}
```

### Implementation

```kotlin
package com.example.lib4gz.courses.service

import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.exception.BadRequestException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.model.entity.Enrollment
import com.example.lib4gz.courses.model.entity.EnrollmentRole
import com.example.lib4gz.courses.model.entity.EnrollmentStatus
import com.example.lib4gz.courses.model.mapper.EnrollmentMapper
import com.example.lib4gz.courses.model.payload.EnrollmentRequest
import com.example.lib4gz.courses.model.payload.EnrollmentResponse
import com.example.lib4gz.courses.model.payload.UpdateEnrollmentRequest
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.EnrollmentRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant

@Service
@Transactional
class EnrollmentServiceImpl(
    private val enrollmentRepo: EnrollmentRepo,
    private val courseRepo: CourseRepo,
    private val userRepo: UserRepo,
    private val enrollmentMapper: EnrollmentMapper
) : EnrollmentService {

    override fun requestEnrollment(courseId: String, userId: String, request: EnrollmentRequest): Mono<EnrollmentResponse> {
        return Mono.fromCallable {
            val course = courseRepo.findById(courseId).orElseThrow {
                ResourceNotFoundException("Course not found with id: $courseId")
            }

            val user = userRepo.findById(userId).orElseThrow {
                ResourceNotFoundException("User not found with id: $userId")
            }

            // Idempotent: if already enrolled, return existing enrollment
            val existingEnrollment = enrollmentRepo.findByCourse_IdAndUser_Id(courseId, userId)
            if (existingEnrollment != null) {
                return@fromCallable enrollmentMapper.toResponse(existingEnrollment, course.title)
            }

            // Course creator automatically becomes TEACHER with ACTIVE status
            val isCreator = course.createdBy.id == userId
            val enrollment = Enrollment(
                course = course,
                user = user,
                role = if (isCreator) EnrollmentRole.TEACHER else request.role,
                status = if (isCreator) EnrollmentStatus.ACTIVE else EnrollmentStatus.PENDING,
                joinedAt = if (isCreator) Instant.now().toEpochMilli() else null
            )

            val savedEnrollment = enrollmentRepo.save(enrollment)
            enrollmentMapper.toResponse(savedEnrollment, course.title)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun approveEnrollment(enrollmentId: String, teacherId: String): Mono<EnrollmentResponse> {
        return Mono.fromCallable {
            val enrollment = enrollmentRepo.findById(enrollmentId).orElseThrow {
                ResourceNotFoundException("Enrollment not found with id: $enrollmentId")
            }

            // Check if the approver is a teacher in this course
            if (!isTeacherInCourse(enrollment.course.id, teacherId)) {
                throw UnauthorizedException("Only teachers can approve enrollments")
            }

            enrollment.status = EnrollmentStatus.ACTIVE
            enrollment.joinedAt = Instant.now().toEpochMilli()

            val savedEnrollment = enrollmentRepo.save(enrollment)
            enrollmentMapper.toResponse(savedEnrollment, enrollment.course.title)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun rejectEnrollment(enrollmentId: String, teacherId: String): Mono<EnrollmentResponse> {
        return Mono.fromCallable {
            val enrollment = enrollmentRepo.findById(enrollmentId).orElseThrow {
                ResourceNotFoundException("Enrollment not found with id: $enrollmentId")
            }

            // Check if the rejecter is a teacher in this course
            if (!isTeacherInCourse(enrollment.course.id, teacherId)) {
                throw UnauthorizedException("Only teachers can reject enrollments")
            }

            enrollment.status = EnrollmentStatus.REJECTED

            val savedEnrollment = enrollmentRepo.save(enrollment)
            enrollmentMapper.toResponse(savedEnrollment, enrollment.course.title)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun updateEnrollment(enrollmentId: String, userId: String, request: UpdateEnrollmentRequest): Mono<EnrollmentResponse> {
        return Mono.fromCallable {
            val enrollment = enrollmentRepo.findById(enrollmentId).orElseThrow {
                ResourceNotFoundException("Enrollment not found with id: $enrollmentId")
            }

            // Only teachers can update enrollments
            if (!isTeacherInCourse(enrollment.course.id, userId)) {
                throw UnauthorizedException("Only teachers can update enrollments")
            }

            request.role?.let { enrollment.role = it }
            request.status?.let {
                enrollment.status = it
                if (it == EnrollmentStatus.ACTIVE && enrollment.joinedAt == null) {
                    enrollment.joinedAt = Instant.now().toEpochMilli()
                }
            }

            val savedEnrollment = enrollmentRepo.save(enrollment)
            enrollmentMapper.toResponse(savedEnrollment, enrollment.course.title)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun removeEnrollment(enrollmentId: String, userId: String): Mono<Void> {
        return Mono.fromCallable {
            val enrollment = enrollmentRepo.findById(enrollmentId).orElseThrow {
                ResourceNotFoundException("Enrollment not found with id: $enrollmentId")
            }

            // Users can remove their own enrollment, or teachers can remove others
            val isSelf = enrollment.user.id == userId
            val isTeacher = isTeacherInCourse(enrollment.course.id, userId)

            if (!isSelf && !isTeacher) {
                throw UnauthorizedException("You can only remove your own enrollment or be a teacher")
            }

            enrollmentRepo.delete(enrollment)
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    override fun listCourseEnrollments(courseId: String, userId: String): Flux<EnrollmentResponse> {
        return Mono.fromCallable {
            val course = courseRepo.findById(courseId).orElseThrow {
                ResourceNotFoundException("Course not found with id: $courseId")
            }

            // Only teachers can list all enrollments
            if (!isTeacherInCourse(courseId, userId)) {
                throw UnauthorizedException("Only teachers can view all enrollments")
            }

            val enrollments = enrollmentRepo.findByCourse_Id(courseId)
            enrollments.map { enrollmentMapper.toResponse(it, course.title) }
        }.flatMapMany { Flux.fromIterable(it) }
         .subscribeOn(Schedulers.boundedElastic())
    }

    override fun getUserEnrollment(courseId: String, userId: String): Mono<EnrollmentResponse> {
        return Mono.fromCallable {
            val enrollment = enrollmentRepo.findByCourse_IdAndUser_Id(courseId, userId)
            enrollment?.let { enrollmentMapper.toResponse(it, it.course.title) }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun isTeacherInCourse(courseId: String, userId: String): Boolean {
        // Course creator is always a teacher
        val course = courseRepo.findById(courseId).orElse(null) ?: return false
        if (course.createdBy.id == userId) {
            return true
        }

        val enrollment = enrollmentRepo.findActiveEnrollment(courseId, userId) ?: return false
        return enrollment.role == EnrollmentRole.TEACHER
    }

    override fun isEnrolledInCourse(courseId: String, userId: String): Boolean {
        // Course creator is always enrolled
        val course = courseRepo.findById(courseId).orElse(null) ?: return false
        if (course.createdBy.id == userId) {
            return true
        }

        return enrollmentRepo.findActiveEnrollment(courseId, userId) != null
    }
}
```

**Business logic details:**
- `requestEnrollment()`: Finds the course and user. **Idempotent** — if enrollment already exists, returns the existing enrollment response without creating a new record. If the user is the course creator, creates enrollment with TEACHER role, ACTIVE status, and joinedAt set to now. Otherwise, creates enrollment with the requested role and PENDING status (joinedAt is null).
- `approveEnrollment()`: Finds enrollment, checks that the calling user is a teacher in the course (throws UnauthorizedException). Sets status=ACTIVE and joinedAt=now directly on the entity.
- `rejectEnrollment()`: Finds enrollment, checks teacher authorization. Sets status=REJECTED directly on the entity.
- `updateEnrollment()`: Teacher-only. Applies non-null fields from request (role and/or status). When status is set to ACTIVE and joinedAt is null, automatically sets joinedAt to now.
- `removeEnrollment()`: Either the enrolled user themselves or a teacher can remove the enrollment. This is a hard delete.
- `listCourseEnrollments()`: Teacher-only. Returns all enrollments for the course.
- `getUserEnrollment()`: Returns the calling user's own enrollment in the specified course. Returns null (empty Mono) if not found — does NOT throw an exception.
- `isTeacherInCourse()`: Loads the course via `findById`. Returns true if the user is the course creator OR has an ACTIVE enrollment with TEACHER role. Returns false if the course does not exist.
- `isEnrolledInCourse()`: Loads the course via `findById`. Returns true if the user is the course creator OR has any ACTIVE enrollment. Returns false if the course does not exist.

---

## 5. Controller

**Package:** `com.example.lib4gz.courses.controller`
**File:** `EnrollmentController.kt`

### Endpoint Table

| Method | Path                                       | Function              | Request Body                        | Response                    | HTTP Status |
|--------|--------------------------------------------|-----------------------|-------------------------------------|-----------------------------|-------------|
| POST   | /v1/courses/{courseId}/enroll               | requestEnrollment     | EnrollmentRequest? (optional body)  | Mono\<EnrollmentResponse\>  | 201         |
| GET    | /v1/courses/{courseId}/enrollments          | listCourseEnrollments | -                                   | Flux\<EnrollmentResponse\>  | 200         |
| GET    | /v1/courses/{courseId}/my-enrollment        | getMyEnrollment       | -                                   | Mono\<EnrollmentResponse\>  | 200         |
| PUT    | /v1/enrollments/{enrollmentId}             | updateEnrollment      | UpdateEnrollmentRequest             | Mono\<EnrollmentResponse\>  | 200         |
| POST   | /v1/enrollments/{enrollmentId}/approve     | approveEnrollment     | -                                   | Mono\<EnrollmentResponse\>  | 200         |
| POST   | /v1/enrollments/{enrollmentId}/reject      | rejectEnrollment      | -                                   | Mono\<EnrollmentResponse\>  | 200         |
| DELETE | /v1/enrollments/{enrollmentId}             | removeEnrollment      | -                                   | Mono\<Void\>                | 204         |

### Code

```kotlin
package com.example.lib4gz.courses.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.courses.model.payload.EnrollmentRequest
import com.example.lib4gz.courses.model.payload.EnrollmentResponse
import com.example.lib4gz.courses.model.payload.UpdateEnrollmentRequest
import com.example.lib4gz.courses.service.EnrollmentService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1")
class EnrollmentController(
    private val enrollmentService: EnrollmentService
) {

    @PostMapping("/courses/{courseId}/enroll")
    @ResponseStatus(HttpStatus.CREATED)
    fun requestEnrollment(
        @PathVariable courseId: String,
        @RequestBody(required = false) enrollRequest: EnrollmentRequest?,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<EnrollmentResponse> {
        return enrollmentService.requestEnrollment(courseId, userId, enrollRequest ?: EnrollmentRequest())
    }

    @GetMapping("/courses/{courseId}/enrollments")
    fun listCourseEnrollments(
        @PathVariable courseId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<EnrollmentResponse> {
        return enrollmentService.listCourseEnrollments(courseId, userId)
    }

    @GetMapping("/courses/{courseId}/my-enrollment")
    fun getMyEnrollment(
        @PathVariable courseId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<EnrollmentResponse> {
        return enrollmentService.getUserEnrollment(courseId, userId)
    }

    @PutMapping("/enrollments/{enrollmentId}")
    fun updateEnrollment(
        @PathVariable enrollmentId: String,
        @RequestBody updateRequest: UpdateEnrollmentRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<EnrollmentResponse> {
        return enrollmentService.updateEnrollment(enrollmentId, userId, updateRequest)
    }

    @PostMapping("/enrollments/{enrollmentId}/approve")
    fun approveEnrollment(
        @PathVariable enrollmentId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<EnrollmentResponse> {
        return enrollmentService.approveEnrollment(enrollmentId, userId)
    }

    @PostMapping("/enrollments/{enrollmentId}/reject")
    fun rejectEnrollment(
        @PathVariable enrollmentId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<EnrollmentResponse> {
        return enrollmentService.rejectEnrollment(enrollmentId, userId)
    }

    @DeleteMapping("/enrollments/{enrollmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeEnrollment(
        @PathVariable enrollmentId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<Void> {
        return enrollmentService.removeEnrollment(enrollmentId, userId)
    }
}
```

---

## 6. Business Rules

1. **One enrollment per user per course:** Enforced by the database unique constraint on `(courseId, userId)`. The request enrollment endpoint is **idempotent** — if the user already has an enrollment, the existing enrollment is returned as a successful response without creating a new record.
2. **Course creator auto-enrollment:** When the course creator enrolls, they automatically receive TEACHER role, ACTIVE status, and `joinedAt` set to the current timestamp (regardless of what role is requested).
3. **Teacher-only operations:** Only teachers can:
   - Approve enrollments (`POST /enrollments/{id}/approve`)
   - Reject enrollments (`POST /enrollments/{id}/reject`)
   - Update enrollments (`PUT /enrollments/{id}`)
   - List all course enrollments (`GET /courses/{courseId}/enrollments`)
4. **Teacher determination:** A user is considered a teacher if they are the course creator (checked by loading course via `findById` and comparing `createdBy.id`) OR they have an ACTIVE enrollment with TEACHER role.
5. **Enrollment removal:** Either the enrolled user themselves (self-removal) or a teacher in the course can remove an enrollment. Returns UnauthorizedException otherwise.
6. **Hard delete:** Enrollments are hard-deleted (no `@SQLDelete` or `@Where` annotations). When removed, the row is physically deleted from the database.
7. **Optional request body:** The `POST /courses/{courseId}/enroll` endpoint accepts an optional `EnrollmentRequest` body. If no body is provided, the enrollment defaults to LEARNER role.
8. **Enrollment statuses:**
   - `PENDING` -- initial state for non-creator enrollments, awaiting teacher approval
   - `ACTIVE` -- approved and active, grants access to the course
   - `REJECTED` -- teacher rejected the enrollment request
   - `INACTIVE` -- enrollment has been deactivated
9. **Enrolled determination:** A user is considered enrolled if they are the course creator OR have any ACTIVE enrollment (regardless of role).
10. **URL structure:** Course-scoped endpoints use `/v1/courses/{courseId}/...` while enrollment-specific operations use `/v1/enrollments/{enrollmentId}/...`. The controller uses `@RequestMapping("/v1")` as the base path.
11. **Reactive pattern:** All service methods wrap blocking JPA calls in `Mono.fromCallable { }.subscribeOn(Schedulers.boundedElastic())`. Flux-returning methods use `flatMapMany { Flux.fromIterable(it) }`.
12. **User ID header:** All endpoints receive the authenticated user ID via the `X-User-Id` header (constant `PZRequestHeader.USER_ID`), injected by JwtAuthFilter.
13. **getUserEnrollment returns null:** The `GET /courses/{courseId}/my-enrollment` endpoint returns an empty response (null) if the user has no enrollment in the course — it does NOT throw an exception.
14. **Auto-set joinedAt on ACTIVE:** When `updateEnrollment()` sets status to ACTIVE and `joinedAt` is null, `joinedAt` is automatically set to the current timestamp.
