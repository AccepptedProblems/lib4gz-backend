# Course Feature

## 1. Overview

Core course management feature providing full CRUD operations for courses. Courses are the primary container in the system, holding modules and enrollments. Courses support visibility settings (PUBLIC, PRIVATE, UNLISTED) and JSON-based settings storage. Each course has a unique 10-digit numeric `code` auto-generated on creation for easy sharing and lookup. Only users with the global `UserRole.TEACHER` role can create courses. Course creation automatically enrolls the creator as a TEACHER with ACTIVE status.

---

## 2. Models

> Full model definitions: [models/course.yaml](../models/course.yaml)

### Entity
- **Course** — `courses` table, soft delete via `@SQLDelete` + `@Where`, JSONB settings, `Visibility` enum, unique 10-digit numeric `code`

### Enums
- **Visibility**: PUBLIC, PRIVATE, UNLISTED

### DTOs
- **CreateCourseRequest** — title (required), description?, visibility, settings
- **UpdateCourseRequest** — all fields optional
- **CourseResponse** — full course with code, createdBy (UserSummary), optional moduleCount/enrollmentCount
- **UserSummary** — shared DTO (id, name, email, avatarUrl?)

### Mapper
- **CourseMapper** — `@Component` with `toResponse()` + companion `toUserSummary()`

---

## 3. Repository

**Package:** `com.example.lib4gz.courses.repo`
**File:** `CourseRepo.kt`

```kotlin
package com.example.lib4gz.courses.repo

import com.example.lib4gz.courses.model.entity.Course
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface CourseRepo : JpaRepository<Course, String> {

    fun findByCode(code: String): Course?

    fun findByCreatedBy_Id(userId: String): List<Course>

    @Query("SELECT c FROM Course c JOIN c.enrollments e WHERE e.user.id = :userId AND e.status = 'ACTIVE'")
    fun findByEnrolledUser(userId: String): List<Course>

    fun existsByIdAndCreatedBy_Id(courseId: String, userId: String): Boolean

    @Query("SELECT c FROM Course c WHERE c.visibility = com.example.lib4gz.courses.model.entity.Visibility.PUBLIC")
    fun findPublicCourses(): List<Course>
}
```

---

## 4. Service

**Package:** `com.example.lib4gz.courses.service`
**File:** `CourseService.kt` (interface) and `CourseServiceImpl.kt` (implementation)

### Interface

```kotlin
package com.example.lib4gz.courses.service

import com.example.lib4gz.courses.model.payload.CourseResponse
import com.example.lib4gz.courses.model.payload.CreateCourseRequest
import com.example.lib4gz.courses.model.payload.UpdateCourseRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface CourseService {
    fun createCourse(userId: String, request: CreateCourseRequest): Mono<CourseResponse>
    fun getCourseById(courseId: String, userId: String): Mono<CourseResponse>
    fun getCourseByCode(code: String, userId: String): Mono<CourseResponse>
    fun updateCourse(courseId: String, userId: String, request: UpdateCourseRequest): Mono<CourseResponse>
    fun deleteCourse(courseId: String, userId: String): Mono<Void>
    fun listUserCreatedCourses(userId: String): Flux<CourseResponse>
    fun listEnrolledCourses(userId: String): Flux<CourseResponse>
    fun listPublicCourses(): Flux<CourseResponse>
}
```

### Implementation

```kotlin
package com.example.lib4gz.courses.service

import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.courses.model.entity.Course
import com.example.lib4gz.courses.model.entity.Enrollment
import com.example.lib4gz.courses.model.entity.EnrollmentRole
import com.example.lib4gz.courses.model.entity.EnrollmentStatus
import com.example.lib4gz.courses.model.mapper.CourseMapper
import com.example.lib4gz.courses.model.payload.CourseResponse
import com.example.lib4gz.courses.model.payload.CreateCourseRequest
import com.example.lib4gz.courses.model.payload.UpdateCourseRequest
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.EnrollmentRepo
import com.example.lib4gz.courses.repo.ModuleRepo
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import com.example.lib4gz.common.utils.IdGenerator
import java.time.Instant

@Service
@Transactional
class CourseServiceImpl(
    private val courseRepo: CourseRepo,
    private val userRepo: UserRepo,
    private val enrollmentRepo: EnrollmentRepo,
    private val moduleRepo: ModuleRepo,
    private val courseMapper: CourseMapper
) : CourseService {

    override fun createCourse(userId: String, request: CreateCourseRequest): Mono<CourseResponse> {
        val user = userRepo.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        if (!user.hasRole(UserRole.TEACHER)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only users with TEACHER role can create courses")
        }

        val course = Course(
            title = request.title,
            description = request.description,
            visibility = request.visibility,
            createdBy = user,
            settings = request.settings
        )

        val savedCourse = courseRepo.save(course)

        // Auto-create TEACHER enrollment for course creator
        val enrollment = Enrollment(
            id = IdGenerator.generate("enr"),
            course = savedCourse,
            user = user,
            role = EnrollmentRole.TEACHER,
            status = EnrollmentStatus.ACTIVE,
            joinedAt = Instant.now().toEpochMilli(),
            createdAt = Instant.now().toEpochMilli(),
            updatedAt = Instant.now().toEpochMilli()
        )
        enrollmentRepo.save(enrollment)

        return Mono.just(courseMapper.toResponse(savedCourse))
    }

    override fun getCourseById(courseId: String, userId: String): Mono<CourseResponse> {
        val course = courseRepo.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        if (!hasAccessToCourse(course, userId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }

        val moduleCount = moduleRepo.countByCourse_Id(courseId).toInt()
        val enrollmentCount = enrollmentRepo.countByCourse_IdAndStatus(courseId, EnrollmentStatus.ACTIVE).toInt()

        return Mono.just(courseMapper.toResponse(course, moduleCount, enrollmentCount))
    }

    override fun updateCourse(courseId: String, userId: String, request: UpdateCourseRequest): Mono<CourseResponse> {
        if (!courseRepo.existsByIdAndCreatedBy_Id(courseId, userId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the course creator can update this course")
        }

        val course = courseRepo.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found") }

        request.title?.let { course.title = it }
        request.description?.let { course.description = it }
        request.visibility?.let { course.visibility = it }
        request.settings?.let { course.settings = it }

        val updatedCourse = courseRepo.save(course)
        return Mono.just(courseMapper.toResponse(updatedCourse))
    }

    override fun deleteCourse(courseId: String, userId: String): Mono<Void> {
        if (!courseRepo.existsByIdAndCreatedBy_Id(courseId, userId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the course creator can delete this course")
        }

        courseRepo.deleteById(courseId)
        return Mono.empty()
    }

    override fun listUserCreatedCourses(userId: String): Flux<CourseResponse> {
        val courses = courseRepo.findByCreatedBy_Id(userId)
        return Flux.fromIterable(courses.map { courseMapper.toResponse(it) })
    }

    override fun listEnrolledCourses(userId: String): Flux<CourseResponse> {
        val courses = courseRepo.findByEnrolledUser(userId)
        return Flux.fromIterable(courses.map { courseMapper.toResponse(it) })
    }

    override fun listPublicCourses(): Flux<CourseResponse> {
        val courses = courseRepo.findPublicCourses()
        return Flux.fromIterable(courses.map { courseMapper.toResponse(it) })
    }

    private fun hasAccessToCourse(course: Course, userId: String): Boolean {
        // Creator always has access
        if (course.createdBy.id == userId) return true
        // Public courses are accessible to everyone
        if (course.visibility == com.example.lib4gz.courses.model.entity.Visibility.PUBLIC) return true
        // Check for active enrollment
        val enrollment = enrollmentRepo.findActiveEnrollment(course.id, userId)
        return enrollment != null
    }
}
```

**Business logic details:**
- `createCourse()`: Finds user by ID, verifies user has `UserRole.TEACHER` global role (throws 403 FORBIDDEN if not), creates Course entity from request, saves it, then auto-creates an Enrollment with role=TEACHER, status=ACTIVE, and joinedAt=now for the creator. Returns the course response.
- `getCourseById()`: Finds course by ID, checks access via `hasAccessToCourse()`, then returns response including `moduleCount` (from `moduleRepo.countByCourse_Id`) and `enrollmentCount` (from `enrollmentRepo.countByCourse_IdAndStatus` with ACTIVE status).
- `getCourseByCode()`: Finds course by its unique 10-digit code via `courseRepo.findByCode()`, checks access via `hasAccessToCourse()`, then returns response including `moduleCount` and `enrollmentCount`. Throws 404 if no course matches the code.
- `updateCourse()`: Checks `courseRepo.existsByIdAndCreatedBy_Id` -- throws FORBIDDEN if user is not the creator. Applies non-null fields from the request. Saves and returns.
- `deleteCourse()`: Checks `courseRepo.existsByIdAndCreatedBy_Id` -- throws FORBIDDEN if user is not the creator. Deletes by ID (soft delete via @SQLDelete).
- `hasAccessToCourse(course, userId)`: Returns true if userId matches course creator, OR course visibility is PUBLIC, OR an ACTIVE enrollment exists for the user.

---

## 5. Controller

**Package:** `com.example.lib4gz.courses.controller`
**File:** `CourseController.kt`

### Endpoint Table

| Method | Path                    | Function         | Request Body         | Request Params/Headers                          | Response                | HTTP Status |
|--------|-------------------------|------------------|----------------------|-------------------------------------------------|-------------------------|-------------|
| GET    | /v1/courses             | listCourses      | -                    | @RequestParam type?: String, @RequestHeader userId: String | Flux\<CourseResponse\>  | 200         |
| POST   | /v1/courses             | createCourse     | CreateCourseRequest  | @RequestHeader userId: String                     | Mono\<CourseResponse\>  | 201         |
| GET    | /v1/courses/{id}        | getCourse        | -                    | @PathVariable id: String, @RequestHeader userId: String | Mono\<CourseResponse\>  | 200         |
| GET    | /v1/courses/code/{code} | getCourseByCode  | -                    | @PathVariable code: String, @RequestHeader userId: String | Mono\<CourseResponse\>  | 200         |
| PATCH  | /v1/courses/{id}        | updateCourse     | UpdateCourseRequest  | @PathVariable id: String, @RequestHeader userId: String | Mono\<CourseResponse\>  | 200         |
| DELETE | /v1/courses/{id}        | deleteCourse     | -                    | @PathVariable id: String, @RequestHeader userId: String | Mono\<Void\>            | 204         |

### Code

```kotlin
package com.example.lib4gz.courses.controller

import com.example.lib4gz.courses.model.payload.CourseResponse
import com.example.lib4gz.courses.model.payload.CreateCourseRequest
import com.example.lib4gz.courses.model.payload.UpdateCourseRequest
import com.example.lib4gz.courses.service.CourseService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1/courses")
class CourseController(
    private val courseService: CourseService
) {

    @GetMapping
    fun listCourses(
        @RequestParam(required = false) type: String?,
        @RequestHeader("userId") userId: String
    ): Flux<CourseResponse> {
        return when (type) {
            "created" -> courseService.listUserCreatedCourses(userId)
            "public" -> courseService.listPublicCourses()
            else -> courseService.listEnrolledCourses(userId) // default is "enrolled"
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCourse(
        @RequestBody request: CreateCourseRequest,
        @RequestHeader("userId") userId: String
    ): Mono<CourseResponse> {
        return courseService.createCourse(userId, request)
    }

    @GetMapping("/{id}")
    fun getCourse(
        @PathVariable id: String,
        @RequestHeader("userId") userId: String
    ): Mono<CourseResponse> {
        return courseService.getCourseById(id, userId)
    }

    @GetMapping("/code/{code}")
    fun getCourseByCode(
        @PathVariable code: String,
        @RequestHeader("userId") userId: String
    ): Mono<CourseResponse> {
        return courseService.getCourseByCode(code, userId)
    }

    @PatchMapping("/{id}")
    fun updateCourse(
        @PathVariable id: String,
        @RequestBody request: UpdateCourseRequest,
        @RequestHeader("userId") userId: String
    ): Mono<CourseResponse> {
        return courseService.updateCourse(id, userId, request)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCourse(
        @PathVariable id: String,
        @RequestHeader("userId") userId: String
    ): Mono<Void> {
        return courseService.deleteCourse(id, userId)
    }
}
```

---

## 6. Business Rules

1. **TEACHER role required to create:** Only users with the global `UserRole.TEACHER` role can create courses. Returns HTTP 403 FORBIDDEN otherwise. This is distinct from the per-course `EnrollmentRole.TEACHER`.
2. **Auto-enrollment on create:** When a course is created, the creator is automatically enrolled as a TEACHER with status ACTIVE and `joinedAt` set to the current timestamp.
2. **Only creator can update:** Only the user who created the course (checked via `existsByIdAndCreatedBy_Id`) can update course fields. Returns HTTP 403 FORBIDDEN otherwise.
3. **Only creator can delete:** Only the user who created the course can delete it. Returns HTTP 403 FORBIDDEN otherwise.
4. **Access control for viewing:** A user can view a course if they are the creator, OR the course has PUBLIC visibility, OR the user has an ACTIVE enrollment in the course.
5. **List types:** The `GET /v1/courses` endpoint supports a `type` query parameter:
   - `"created"` -- returns courses created by the user (`findByCreatedBy_Id`)
   - `"enrolled"` -- returns courses where the user has an ACTIVE enrollment (`findByEnrolledUser`) -- this is the **default** when `type` is not provided
   - `"public"` -- returns all courses with PUBLIC visibility (`findPublicCourses`)
6. **Soft delete:** Courses use `@SQLDelete` with inline SQL timestamp function `(EXTRACT(EPOCH FROM NOW()) * 1000)::bigint` — NOT a bind parameter `?` for the timestamp, because Hibernate 6.x only passes the entity ID. `@Where(clause = "deleted_at IS NULL")` filters out soft-deleted courses from queries.
7. **Module/enrollment counts:** When fetching a single course (`getCourseById`), the response includes `moduleCount` and `enrollmentCount` (count of ACTIVE enrollments only).
8. **Default visibility:** New courses default to PRIVATE visibility.
9. **Settings:** Course settings are stored as a JSON/JSONB map (`Map<String, Any>`) and default to an empty map.
10. **Course code:** Each course is auto-assigned a unique 10-digit numeric code on creation via `IdGenerator.generateNumericCode(10)`. The code is immutable (`val`), stored with a unique constraint, and can be used to look up a course via `GET /v1/courses/code/{code}`. Access control is enforced the same way as `getCourseById`.
