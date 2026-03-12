# lib4gz Architecture Overview

## Architecture Patterns & Conventions

### 1. Feature-Based Architecture

The project is organized by feature, not by technical layer. Each feature is a self-contained package with its own `controller/`, `service/`, `repo/`, and `model/` subpackages.

Current features:

| Feature      | Package                      | Description                              |
|--------------|------------------------------|------------------------------------------|
| **auth**     | `com.example.lib4gz.auth`    | User registration, login, JWT tokens     |
| **courses**  | `com.example.lib4gz.courses` | Courses, enrollments, modules, lessons, summaries |
| **exercise** | `com.example.lib4gz.exercise`| Exercises, questions, submissions, student answers |

Shared infrastructure lives in `com.example.lib4gz.common` (security, exceptions, utilities).

---

### 2. Layer Responsibilities

#### Controller

- HTTP entry point. Handles request/response mapping and basic validation.
- Delegates all work to the service layer.
- Does **NOT** contain business logic.
- Obtains the authenticated user via a custom request header injected by the JWT filter:

```kotlin
@RequestHeader(PZRequestHeader.USER_ID) userId: String
```

- Returns `Mono<ResponseEntity<T>>` or `Flux<T>`.

#### Service

- Follows the **Interface + Impl** pattern. Every service defines an interface (e.g., `CourseService`) and a corresponding implementation class annotated with `@Service` (e.g., `CourseServiceImpl`).
- All public methods return `Mono<T>` or `Flux<T>`.
- Wraps blocking JPA/repository calls in a reactive context:

```kotlin
Mono.fromCallable {
    // blocking JPA call
}.subscribeOn(Schedulers.boundedElastic())
```

- Contains all **authorization checks** (e.g., verifying the caller is a teacher in the course before allowing mutations).

#### Repository

- Extends `JpaRepository<Entity, String>`.
- Uses Spring Data method naming conventions (e.g., `findByEmail`, `findByCourseIdAndUserId`).
- Uses `@Query` annotations for anything beyond simple method-name derivation.
- No manual JDBC or native SQL outside of `@Query`.

#### Model

Organized into three subpackages:

| Subpackage   | Contents                                                         |
|--------------|------------------------------------------------------------------|
| `entity/`    | JPA `@Entity` classes, enums                                     |
| `payload/`   | Request DTOs (e.g., `CourseCreationRequest`) and response DTOs   |
| `mapper/`    | `@Component` mapper classes with `toResponse()` methods          |

---

### 3. Key Conventions

#### Soft Delete

Entities that support soft deletion use Hibernate annotations and a `deleted_at` field:

```kotlin
@SQLDelete(sql = "UPDATE table_name SET deleted_at = (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
class SomeEntity(
    // ...
    var deletedAt: Long? = null
)
```

**IMPORTANT (Hibernate 6.x):** The `@SQLDelete` SQL must use an inline SQL expression for the timestamp — NOT a bind parameter `?`. In Hibernate 6.x, `delete()` only passes the entity ID as a parameter. Using `SET deleted_at = ?` with two `?` placeholders causes "No value specified for parameter 2" because Hibernate binds the ID to the first `?` and leaves the second unbound.

When `deleteById` is called, Hibernate executes the `@SQLDelete` SQL instead of a real `DELETE`, setting `deleted_at` to the current epoch millis timestamp. The `@Where` clause ensures all queries automatically exclude soft-deleted rows.

#### Timestamps

- All timestamps are stored as `Long` (epoch milliseconds).
- Generated via `Instant.now().toEpochMilli()`.
- `createdAt` is set on construction.
- `updatedAt` is auto-refreshed using `@PreUpdate`:

```kotlin
@PreUpdate
fun onUpdate() {
    updatedAt = Instant.now().toEpochMilli()
}
```

#### TypeID Primary Keys

All entities use TypeID primary keys — a type-prefixed, time-sortable identifier based on UUIDv7 with Crockford Base32 encoding. Each entity type has a unique 3-character prefix (e.g., `usr`, `crs`, `mod`), making IDs self-describing and debuggable.

```kotlin
@Id
@Column(length = 30)
val id: String = IdGenerator.generate("usr")
```

The column type is `VARCHAR(30)` in the database. TypeIDs are 30 characters total: 3-char prefix + underscore + 26-char Crockford Base32-encoded UUIDv7.

Example: `usr_01h455vb4pex5vsknk084sn02q`

Entity prefixes:

| Entity        | Prefix |
|---------------|--------|
| User          | `usr`  |
| Course        | `crs`  |
| Enrollment    | `enr`  |
| Module        | `mod`  |
| Lesson        | `les`  |
| Summary       | `sum`  |
| Exercise      | `exc`  |
| Question      | `qst`  |
| Submission    | `sub`  |
| StudentAnswer | `ans`  |

#### JSONB Columns

For flexible/schema-less data (e.g., course settings, question metadata):

```kotlin
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
var settings: Map<String, Any> = emptyMap()
```

Stored as PostgreSQL `jsonb` columns.

#### Auto OrderIndex

When a new item is created and `orderIndex` is null in the request, the service queries for the current maximum and increments:

```kotlin
val nextIndex = repo.findMaxOrderIndex(parentId)?.plus(1) ?: 0
```

This ensures items are appended at the end by default.

#### Reactive Wrappers

Every service method wraps blocking JPA calls to avoid blocking the reactive event loop:

```kotlin
fun getCourse(id: String): Mono<CourseResponse> =
    Mono.fromCallable {
        val course = courseRepo.findById(id)
            .orElseThrow { ResourceNotFoundException("Course not found") }
        courseMapper.toResponse(course)
    }.subscribeOn(Schedulers.boundedElastic())
```

#### JWT via Custom Header

Authentication flow:

1. Client sends the JWT in the `access_token` HTTP header.
2. `JwtAuthFilter` validates the token using `JwtProvider`.
3. On success, the filter injects an `X-User-Id` header into the request (via `MutableHttpServletRequest`).
4. Controllers read the user ID from this header using `@RequestHeader(PZRequestHeader.USER_ID)`.

The `PZRequestHeader` object defines the header constant:

```kotlin
object PZRequestHeader {
    const val USER_ID = "X-User-Id"
}
```

#### Interface + Impl Pattern

All services follow this structure:

```kotlin
// CourseService.kt
interface CourseService {
    fun createCourse(request: CourseCreationRequest, userId: String): Mono<CourseResponse>
    // ...
}

@Service
class CourseServiceImpl(
    private val courseRepo: CourseRepo,
    private val courseMapper: CourseMapper,
    // ...
) : CourseService {
    override fun createCourse(request: CourseCreationRequest, userId: String): Mono<CourseResponse> {
        // implementation
    }
}
```

---

### 4. Authorization Model

#### Course-Based Access Control

All authorization is scoped to courses. There is no global admin role used for content access (global roles exist on `User` but are not used for course-level checks).

#### Roles

| Role        | Permissions                                          |
|-------------|------------------------------------------------------|
| **TEACHER** | Create, update, and delete content within a course   |
| **LEARNER** | View content, submit answers to exercises            |

#### Who Is a Teacher?

The **course creator** (`courses.createdBy`) is always treated as a TEACHER, regardless of enrollment records. Additionally, any user with an ACTIVE enrollment where `role = TEACHER` is a teacher.

```kotlin
fun isTeacherInCourse(courseId: String, userId: String): Boolean {
    // 1. Check if user is the course creator (always a teacher)
    // 2. Check for ACTIVE enrollment with TEACHER role
}
```

#### Who Has Access?

A user can access a course if any of the following are true:

1. They are the course **creator**.
2. The course has **PUBLIC** visibility.
3. They have an **ACTIVE** enrollment in the course.

```kotlin
fun isEnrolledInCourse(courseId: String, userId: String): Boolean {
    // 1. Check if user is the course creator (always considered enrolled)
    // 2. Check for ACTIVE enrollment (any role)
}
```

#### Typical Authorization Flow

1. Controller receives request with `userId` from the `X-User-Id` header.
2. Controller calls service method, passing `userId`.
3. Service checks authorization (e.g., `isTeacherInCourse`) before performing the operation.
4. If unauthorized, throws `UnauthorizedException`.

---

### 5. Mapper Pattern

Mappers are Spring-managed `@Component` classes (not Kotlin `object` singletons). This allows them to be injected with dependencies if needed.

```kotlin
@Component
class CourseMapper {

    fun toResponse(course: Course): CourseResponse {
        // map entity to response DTO
    }

    companion object {
        fun toUserSummary(user: User): UserSummary {
            // static-style mapping available without injection
        }
    }
}
```

- Each feature's mappers are grouped in a single file (e.g., `CourseMapper.kt` contains `CourseMapper`, `EnrollmentMapper`, `ModuleMapper`, `LessonMapper`, `SummaryMapper`).
- The `CourseMapper` companion object contains `toUserSummary()` for cases where the mapper is used in a static context.
- Auth uses a different pattern: `UserMapper.kt` defines an extension function `User.toResponse()`.
