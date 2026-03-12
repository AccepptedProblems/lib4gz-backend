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
**File:** `EnrollmentService.kt` (interface) and `EnrollmentServiceImpl.kt` (implementation)

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
    fun isTeacherInCourse(courseId: String, userId: String): Boolean
    fun isEnrolledInCourse(courseId: String, userId: String): Boolean
}
```

### Implementation

```kotlin
package com.example.lib4gz.courses.service

import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.courses.model.entity.Enrollment
import com.example.lib4gz.courses.model.entity.EnrollmentRole
import com.example.lib4gz.courses.model.entity.EnrollmentStatus
import com.example.lib4gz.courses.model.mapper.EnrollmentMapper
import com.example.lib4gz.courses.model.payload.EnrollmentRequest
import com.example.lib4gz.courses.model.payload.EnrollmentResponse
import com.example.lib4gz.courses.model.payload.UpdateEnrollmentRequest
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.EnrollmentRepo
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import com.example.lib4gz.common.utils.IdGenerator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
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
        val course = courseRepo.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val user = userRepo.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        if (enrollmentRepo.existsByCourse_IdAndUser_Id(courseId, userId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User is already enrolled in this course")
        }

        val now = Instant.now().toEpochMilli()

        val enrollment = if (course.createdBy.id == userId) {
            // Course creator automatically gets TEACHER + ACTIVE + joinedAt
            Enrollment(
                id = IdGenerator.generate("enr"),
                course = course,
                user = user,
                role = EnrollmentRole.TEACHER,
                status = EnrollmentStatus.ACTIVE,
                joinedAt = now,
                createdAt = now,
                updatedAt = now
            )
        } else {
            // Other users get the requested role with PENDING status
            Enrollment(
                id = IdGenerator.generate("enr"),
                course = course,
                user = user,
                role = request.role,
                status = EnrollmentStatus.PENDING,
                joinedAt = null,
                createdAt = now,
                updatedAt = now
            )
        }

        val savedEnrollment = enrollmentRepo.save(enrollment)
        return Mono.just(enrollmentMapper.toResponse(savedEnrollment, course.title))
    }

    override fun approveEnrollment(enrollmentId: String, teacherId: String): Mono<EnrollmentResponse> {
        val enrollment = enrollmentRepo.findById(enrollmentId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found") }

        if (!isTeacherInCourse(enrollment.course.id, teacherId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only teachers can approve enrollments")
        }

        enrollment.approve()
        val savedEnrollment = enrollmentRepo.save(enrollment)
        return Mono.just(enrollmentMapper.toResponse(savedEnrollment, enrollment.course.title))
    }

    override fun rejectEnrollment(enrollmentId: String, teacherId: String): Mono<EnrollmentResponse> {
        val enrollment = enrollmentRepo.findById(enrollmentId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found") }

        if (!isTeacherInCourse(enrollment.course.id, teacherId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only teachers can reject enrollments")
        }

        enrollment.reject()
        val savedEnrollment = enrollmentRepo.save(enrollment)
        return Mono.just(enrollmentMapper.toResponse(savedEnrollment, enrollment.course.title))
    }

    override fun updateEnrollment(enrollmentId: String, userId: String, request: UpdateEnrollmentRequest): Mono<EnrollmentResponse> {
        val enrollment = enrollmentRepo.findById(enrollmentId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found") }

        if (!isTeacherInCourse(enrollment.course.id, userId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only teachers can update enrollments")
        }

        request.role?.let { enrollment.role = it }
        request.status?.let { enrollment.status = it }

        val savedEnrollment = enrollmentRepo.save(enrollment)
        return Mono.just(enrollmentMapper.toResponse(savedEnrollment, enrollment.course.title))
    }

    override fun removeEnrollment(enrollmentId: String, userId: String): Mono<Void> {
        val enrollment = enrollmentRepo.findById(enrollmentId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found") }

        // Self or teacher can remove
        val isSelf = enrollment.user.id == userId
        val isTeacher = isTeacherInCourse(enrollment.course.id, userId)

        if (!isSelf && !isTeacher) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the enrolled user or a teacher can remove this enrollment")
        }

        enrollmentRepo.delete(enrollment)
        return Mono.empty()
    }

    override fun listCourseEnrollments(courseId: String, userId: String): Flux<EnrollmentResponse> {
        if (!isTeacherInCourse(courseId, userId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only teachers can list course enrollments")
        }

        val course = courseRepo.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        val enrollments = enrollmentRepo.findByCourse_Id(courseId)
        return Flux.fromIterable(enrollments.map { enrollmentMapper.toResponse(it, course.title) })
    }

    override fun getUserEnrollment(courseId: String, userId: String): Mono<EnrollmentResponse> {
        val enrollment = enrollmentRepo.findByCourse_IdAndUser_Id(courseId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found")

        val course = courseRepo.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        return Mono.just(enrollmentMapper.toResponse(enrollment, course.title))
    }

    override fun isTeacherInCourse(courseId: String, userId: String): Boolean {
        // Course creator is always a teacher
        if (courseRepo.existsByIdAndCreatedBy_Id(courseId, userId)) {
            return true
        }
        // Check for ACTIVE enrollment with TEACHER role
        val enrollment = enrollmentRepo.findActiveEnrollment(courseId, userId)
        return enrollment != null && enrollment.isTeacher()
    }

    override fun isEnrolledInCourse(courseId: String, userId: String): Boolean {
        // Course creator is always considered enrolled
        if (courseRepo.existsByIdAndCreatedBy_Id(courseId, userId)) {
            return true
        }
        // Check for any ACTIVE enrollment
        val enrollment = enrollmentRepo.findActiveEnrollment(courseId, userId)
        return enrollment != null
    }
}
```

**Business logic details:**
- `requestEnrollment()`: Finds the course and user. Checks if enrollment already exists (throws 409 CONFLICT). If the user is the course creator, creates enrollment with TEACHER role, ACTIVE status, and joinedAt set to now. Otherwise, creates enrollment with the requested role and PENDING status (joinedAt is null).
- `approveEnrollment()`: Finds enrollment, checks that the calling user is a teacher in the course (throws FORBIDDEN). Calls `enrollment.approve()` which sets status=ACTIVE and joinedAt=now.
- `rejectEnrollment()`: Finds enrollment, checks teacher authorization. Calls `enrollment.reject()` which sets status=REJECTED.
- `updateEnrollment()`: Teacher-only. Applies non-null fields from request (role and/or status).
- `removeEnrollment()`: Either the enrolled user themselves or a teacher can remove the enrollment. This is a hard delete.
- `listCourseEnrollments()`: Teacher-only. Returns all enrollments for the course.
- `getUserEnrollment()`: Returns the calling user's own enrollment in the specified course.
- `isTeacherInCourse()`: Returns true if the user is the course creator OR has an ACTIVE enrollment with TEACHER role.
- `isEnrolledInCourse()`: Returns true if the user is the course creator OR has any ACTIVE enrollment.

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

import com.example.lib4gz.courses.model.entity.EnrollmentRole
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
        @RequestHeader("userId") userId: String,
        @RequestBody(required = false) request: EnrollmentRequest?
    ): Mono<EnrollmentResponse> {
        val enrollmentRequest = request ?: EnrollmentRequest(role = EnrollmentRole.LEARNER)
        return enrollmentService.requestEnrollment(courseId, userId, enrollmentRequest)
    }

    @GetMapping("/courses/{courseId}/enrollments")
    fun listCourseEnrollments(
        @PathVariable courseId: String,
        @RequestHeader("userId") userId: String
    ): Flux<EnrollmentResponse> {
        return enrollmentService.listCourseEnrollments(courseId, userId)
    }

    @GetMapping("/courses/{courseId}/my-enrollment")
    fun getMyEnrollment(
        @PathVariable courseId: String,
        @RequestHeader("userId") userId: String
    ): Mono<EnrollmentResponse> {
        return enrollmentService.getUserEnrollment(courseId, userId)
    }

    @PutMapping("/enrollments/{enrollmentId}")
    fun updateEnrollment(
        @PathVariable enrollmentId: String,
        @RequestHeader("userId") userId: String,
        @RequestBody request: UpdateEnrollmentRequest
    ): Mono<EnrollmentResponse> {
        return enrollmentService.updateEnrollment(enrollmentId, userId, request)
    }

    @PostMapping("/enrollments/{enrollmentId}/approve")
    fun approveEnrollment(
        @PathVariable enrollmentId: String,
        @RequestHeader("userId") userId: String
    ): Mono<EnrollmentResponse> {
        return enrollmentService.approveEnrollment(enrollmentId, userId)
    }

    @PostMapping("/enrollments/{enrollmentId}/reject")
    fun rejectEnrollment(
        @PathVariable enrollmentId: String,
        @RequestHeader("userId") userId: String
    ): Mono<EnrollmentResponse> {
        return enrollmentService.rejectEnrollment(enrollmentId, userId)
    }

    @DeleteMapping("/enrollments/{enrollmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeEnrollment(
        @PathVariable enrollmentId: String,
        @RequestHeader("userId") userId: String
    ): Mono<Void> {
        return enrollmentService.removeEnrollment(enrollmentId, userId)
    }
}
```

---

## 6. Business Rules

1. **One enrollment per user per course:** Enforced by the database unique constraint on `(courseId, userId)`. Attempting to enroll twice returns HTTP 409 CONFLICT.
2. **Course creator auto-enrollment:** When the course creator enrolls, they automatically receive TEACHER role, ACTIVE status, and `joinedAt` set to the current timestamp (regardless of what role is requested).
3. **Teacher-only operations:** Only teachers can:
   - Approve enrollments (`POST /enrollments/{id}/approve`)
   - Reject enrollments (`POST /enrollments/{id}/reject`)
   - Update enrollments (`PUT /enrollments/{id}`)
   - List all course enrollments (`GET /courses/{courseId}/enrollments`)
4. **Teacher determination:** A user is considered a teacher if they are the course creator (checked via `courseRepo.existsByIdAndCreatedBy_Id`) OR they have an ACTIVE enrollment with TEACHER role.
5. **Enrollment removal:** Either the enrolled user themselves (self-removal) or a teacher in the course can remove an enrollment. Returns HTTP 403 FORBIDDEN otherwise.
6. **Hard delete:** Enrollments are hard-deleted (no `@SQLDelete` or `@Where` annotations). When removed, the row is physically deleted from the database.
7. **Optional request body:** The `POST /courses/{courseId}/enroll` endpoint accepts an optional `EnrollmentRequest` body. If no body is provided, the enrollment defaults to LEARNER role.
8. **Enrollment statuses:**
   - `PENDING` -- initial state for non-creator enrollments, awaiting teacher approval
   - `ACTIVE` -- approved and active, grants access to the course
   - `REJECTED` -- teacher rejected the enrollment request
   - `INACTIVE` -- enrollment has been deactivated
9. **Enrolled determination:** A user is considered enrolled if they are the course creator OR have any ACTIVE enrollment (regardless of role).
10. **URL structure:** Course-scoped endpoints use `/v1/courses/{courseId}/...` while enrollment-specific operations use `/v1/enrollments/{enrollmentId}/...`. The controller uses `@RequestMapping("/v1")` as the base path.
