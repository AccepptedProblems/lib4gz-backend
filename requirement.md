# lib4gz - LMS Platform Backend Requirements

## PROJECT OVERVIEW

**Project Name**: lib4gz
**Type**: Learning Management System (LMS) Backend API
**Version**: 0.0.1-SNAPSHOT

A Spring Boot + Kotlin backend that supports course management, enrollment, lesson delivery, exercises, and submission workflows.

---

## 1. TECHNOLOGY STACK

### Core Technologies
| Technology | Version | Purpose |
|------------|---------|---------|
| Kotlin | 1.9.25 | Primary language |
| Spring Boot | 3.5.7 | Application framework |
| Gradle (Kotlin DSL) | 9.2.1 | Build tool |
| JDK | 21 | Java toolchain |

### Dependencies

**Spring Starters:**
- `spring-boot-starter-web` - REST API
- `spring-boot-starter-webflux` - Reactive support
- `spring-boot-starter-security` - Authentication
- `spring-boot-starter-data-jpa` - ORM

**Additional Libraries:**
- `jjwt-api:0.12.6` + `jjwt-impl` + `jjwt-jackson` - JWT handling
- `jackson-module-kotlin` - JSON serialization
- `reactor-kotlin-extensions` - Reactive Kotlin
- `kotlinx-coroutines-reactor` - Coroutines
- `postgresql` (runtime) - Database driver
- `lombok:1.18.42` - Boilerplate reduction

**Gradle Plugins:**
- `kotlin("jvm")` 1.9.25
- `kotlin("plugin.spring")` 1.9.25
- `kotlin("plugin.jpa")` 1.9.25
- `org.springframework.boot` 3.5.7
- `io.spring.dependency-management` 1.1.7

---

## 2. PROJECT STRUCTURE

```
src/main/kotlin/com/example/lib4gz/
├── Lib4gzApplication.kt              # Entry point
├── auth/                             # Authentication module
│   ├── controller/AuthController.kt
│   ├── service/AuthService.kt
│   ├── repo/UserRepo.kt
│   └── model/
│       ├── entity/User.kt
│       ├── payload/UserPayload.kt
│       └── mapper/UserMapper.kt
├── courses/                          # Course management module
│   ├── controller/
│   │   ├── CourseController.kt
│   │   ├── EnrollmentController.kt
│   │   ├── ModuleController.kt
│   │   ├── LessonController.kt
│   │   └── SummaryController.kt
│   ├── service/
│   │   ├── CourseService.kt
│   │   ├── EnrollmentService.kt
│   │   ├── ModuleService.kt
│   │   ├── LessonService.kt
│   │   └── SummaryService.kt
│   ├── repo/
│   │   ├── CourseRepo.kt
│   │   ├── EnrollmentRepo.kt
│   │   ├── ModuleRepo.kt
│   │   ├── LessonRepo.kt
│   │   └── SummaryRepo.kt
│   └── model/
│       ├── entity/Courses.kt         # All course-related entities
│       ├── payload/                  # Request/Response DTOs
│       └── mapper/CourseMapper.kt
├── exercise/                         # Exercise & submission module
│   ├── controller/
│   │   ├── ExerciseController.kt
│   │   ├── QuestionController.kt
│   │   └── SubmissionController.kt
│   ├── service/
│   │   ├── ExerciseService.kt
│   │   ├── QuestionService.kt
│   │   └── SubmissionService.kt
│   ├── repo/
│   │   ├── ExerciseRepo.kt
│   │   ├── QuestionRepo.kt
│   │   ├── StudentAnswerRepo.kt
│   │   └── SubmissionRepo.kt
│   └── model/
│       ├── entity/Exercise.kt
│       ├── payload/
│       └── mapper/
└── common/                           # Shared infrastructure
    ├── config/
    │   ├── security/                 # JWT, filters, security config
    │   ├── common/                   # Request utilities
    │   └── error/                    # Exception handling
    ├── exception/                    # Custom exceptions
    └── utils/                        # Utilities

src/main/resources/
├── application.yaml                  # Main config
├── application-dev.yaml              # Dev profile
└── security.json                     # Security endpoints & CORS
```

---

## 3. CONFIGURATION FILES

### 3.1 application.yaml
```yaml
server:
  port: 8080
  error:
    include-message: always

spring:
  application:
    name: Z_Lib4gz_Backend

application:
  security:
    jwt:
      secret-key: 404E635266556A586E3272357F4428472B4B6250645367566B5970
      expiration: 86400000       # 24 hours
      refresh-token:
        expiration: 604800000    # 7 days
```

### 3.2 application-dev.yaml
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydatabase
    username: myuser
    password: mypassword
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### 3.3 security.json
```json
{
  "permittedEndpoints": [
    "/v1/auth/login",
    "/v1/auth/register",
    "/v2/api-docs",
    "/v3/api-docs",
    "/v3/api-docs/**",
    "/swagger-resources",
    "/swagger-resources/**",
    "/configuration/ui",
    "/configuration/security",
    "/swagger-ui/**",
    "/webjars/**",
    "/swagger-ui.html"
  ],
  "authorizedEndpoints": [],
  "corsSettings": {
    "allowedOrigins": ["http://localhost:3000", "http://localhost:3001"],
    "allowedMethods": ["HEAD", "GET", "POST", "PUT", "DELETE", "PATCH"],
    "allowedHeaders": ["Origin", "Authorization", "Cache-Control", "Content-Type", "access_token"],
    "allowCredentials": true,
    "maxAge": 3600
  }
}
```

---

## 4. DATA MODELS (ENTITIES)

### 4.1 User Entity

**Table**: `users`

| Column | Type | Constraints |
|--------|------|-------------|
| id | VARCHAR(36) | PK, UUID |
| email | VARCHAR(255) | UNIQUE, nullable |
| username | VARCHAR(255) | UNIQUE, NOT NULL |
| password | VARCHAR(255) | NOT NULL (BCrypt) |
| name | VARCHAR(255) | NOT NULL |
| avatar_url | VARCHAR(255) | nullable |
| email_verified | BOOLEAN | default false |
| status | VARCHAR(50) | enum: ACTIVE, INACTIVE, BANNED |
| created_at | BIGINT | immutable timestamp |
| updated_at | BIGINT | auto-update timestamp |
| deleted_at | BIGINT | nullable (soft delete) |

**Junction Table**: `user_global_roles`
| Column | Type |
|--------|------|
| user_id | VARCHAR(36) FK |
| roles | VARCHAR(50) enum: ADMIN, USER |

**Methods**:
- `hasRole(role: UserRole): Boolean`
- `isAdmin(): Boolean`

**Soft Delete**: Use `@SQLDelete` and `@Where(clause = "deleted_at IS NULL")`

### 4.2 Course Entity

**Table**: `courses`

| Column | Type | Constraints |
|--------|------|-------------|
| id | VARCHAR(36) | PK, UUID |
| title | VARCHAR(500) | NOT NULL |
| description | TEXT | nullable |
| visibility | VARCHAR(50) | enum: PUBLIC, PRIVATE, UNLISTED |
| created_by_id | VARCHAR(36) | FK to users |
| settings | JSONB | nullable, Map<String, Any> |
| created_at | BIGINT | immutable |
| updated_at | BIGINT | auto-update |
| deleted_at | BIGINT | nullable (soft delete) |

**Relationships**:
- `@ManyToOne` createdBy -> User
- `@OneToMany` modules (cascade ALL, orphanRemoval)
- `@OneToMany` enrollments (cascade ALL, orphanRemoval)

### 4.3 Enrollment Entity

**Table**: `enrollments`

| Column | Type | Constraints |
|--------|------|-------------|
| id | VARCHAR(36) | PK, UUID |
| course_id | VARCHAR(36) | FK to courses |
| user_id | VARCHAR(36) | FK to users |
| role | VARCHAR(50) | enum: LEARNER, TEACHER |
| status | VARCHAR(50) | enum: PENDING, ACTIVE, REJECTED, INACTIVE |
| joined_at | BIGINT | nullable (when activated) |
| created_at | BIGINT | immutable |
| updated_at | BIGINT | auto-update |

**Unique Constraint**: `(course_id, user_id)`

**Methods**:
- `approve()`: status=ACTIVE, joinedAt=now
- `reject()`: status=REJECTED
- `isActive()`, `isTeacher()`, `isLearner()`

### 4.4 Module Entity

**Table**: `modules`

| Column | Type | Constraints |
|--------|------|-------------|
| id | VARCHAR(36) | PK, UUID |
| course_id | VARCHAR(36) | FK to courses |
| title | VARCHAR(500) | NOT NULL |
| order_index | INT | NOT NULL |
| created_at | BIGINT | immutable |
| updated_at | BIGINT | auto-update |
| deleted_at | BIGINT | nullable (soft delete) |

**Unique Constraint**: `(course_id, order_index)`

**Relationships**:
- `@ManyToOne` course -> Course
- `@OneToMany` lessons (cascade ALL, orphanRemoval, @OrderBy orderIndex ASC)

### 4.5 Lesson Entity

**Table**: `lessons`

| Column | Type | Constraints |
|--------|------|-------------|
| id | VARCHAR(36) | PK, UUID |
| module_id | VARCHAR(36) | FK to modules |
| title | VARCHAR(500) | NOT NULL |
| order_index | INT | NOT NULL |
| created_at | BIGINT | immutable |
| updated_at | BIGINT | auto-update |
| deleted_at | BIGINT | nullable (soft delete) |

**Unique Constraint**: `(module_id, order_index)`

**Relationships**:
- `@ManyToOne` module -> Module
- `@OneToOne` summary (cascade ALL, orphanRemoval)

### 4.6 Summary Entity

**Table**: `summaries`

| Column | Type | Constraints |
|--------|------|-------------|
| id | VARCHAR(36) | PK, UUID |
| lesson_id | VARCHAR(36) | FK to lessons |
| content | TEXT | NOT NULL |
| edited_by_id | VARCHAR(36) | FK to users |
| version | INT | default 1 |
| created_at | BIGINT | immutable |
| updated_at | BIGINT | auto-update |

**Relationships**:
- `@OneToOne` lesson (bidirectional)
- `@ManyToOne` editedBy -> User

### 4.7 Exercise Entity

**Table**: `exercises`

| Column | Type | Constraints |
|--------|------|-------------|
| id | VARCHAR(36) | PK, UUID |
| lesson_id | VARCHAR(36) | FK to lessons |
| title | VARCHAR(500) | NOT NULL |
| type | VARCHAR(50) | enum: TEXT_ANSWER, CODE, FILE_UPLOAD, MULTIPLE_CHOICE, PROJECT_LINK |
| settings | JSONB | nullable, Map<String, Any> |
| order_index | INT | NOT NULL |
| created_at | BIGINT | immutable |
| updated_at | BIGINT | auto-update |
| deleted_at | BIGINT | nullable (soft delete) |

**Unique Constraint**: `(lesson_id, order_index)`

### 4.8 Question Entity

**Table**: `questions`

| Column | Type | Constraints |
|--------|------|-------------|
| id | VARCHAR(36) | PK, UUID |
| exercise_id | VARCHAR(36) | NOT NULL (no JPA relationship) |
| content | TEXT | NOT NULL |
| order_index | INT | NOT NULL |
| meta | JSONB | nullable, Map<String, Any> |
| visibility | VARCHAR(50) | enum: VISIBLE, HIDDEN |
| created_at | BIGINT | immutable |
| updated_at | BIGINT | auto-update |
| deleted_at | BIGINT | nullable (soft delete) |

### 4.9 Submission Entity

**Table**: `submissions`

| Column | Type | Constraints |
|--------|------|-------------|
| id | VARCHAR(36) | PK, UUID |
| exercise_id | VARCHAR(36) | FK to exercises |
| user_id | VARCHAR(36) | FK to users |
| status | VARCHAR(50) | enum: DRAFT, SUBMITTED, APPROVED, NEEDS_REVISION |
| submitted_at | BIGINT | nullable |
| approved_at | BIGINT | nullable |
| created_at | BIGINT | immutable |
| updated_at | BIGINT | auto-update |

**Unique Constraint**: `(exercise_id, user_id)`

**Methods**:
- `submit()`: status=SUBMITTED, submittedAt=now
- `approve()`: status=APPROVED, approvedAt=now
- `requestRevision()`: status=NEEDS_REVISION
- `isDraft()`, `isSubmitted()`, `isApproved()`, `needsRevision()`

### 4.10 StudentAnswer Entity

**Table**: `student_answers`

| Column | Type | Constraints |
|--------|------|-------------|
| id | VARCHAR(36) | PK, UUID |
| question_id | VARCHAR(36) | FK to questions |
| submission_id | VARCHAR(36) | FK to submissions |
| answer | TEXT | nullable |
| teacher_comment | TEXT | nullable |
| created_at | BIGINT | immutable |
| updated_at | BIGINT | auto-update |

---

## 5. API ENDPOINTS

### 5.1 Authentication (`/v1/auth`)

| Method | Endpoint | Request | Response | Auth |
|--------|----------|---------|----------|------|
| POST | /register | UserCreationRequest | UserResponse | No |
| POST | /login | LoginReq | LoginResponse | No |

### 5.2 Courses (`/v1/courses`)

| Method | Endpoint | Request | Response | Auth |
|--------|----------|---------|----------|------|
| GET | / | ?type=created\|enrolled\|public | Flux<CourseResponse> | Yes |
| POST | / | CreateCourseRequest | CourseResponse | Yes |
| GET | /{id} | - | CourseResponse | Yes |
| PATCH | /{id} | UpdateCourseRequest | CourseResponse | Yes (creator) |
| DELETE | /{id} | - | Void | Yes (creator) |

### 5.3 Enrollments

| Method | Endpoint | Request | Response | Auth |
|--------|----------|---------|----------|------|
| POST | /courses/{courseId}/enroll | EnrollmentRequest | EnrollmentResponse | Yes |
| GET | /courses/{courseId}/enrollments | - | Flux<EnrollmentResponse> | Yes (teacher) |
| GET | /courses/{courseId}/my-enrollment | - | EnrollmentResponse | Yes |
| PUT | /enrollments/{id} | UpdateEnrollmentRequest | EnrollmentResponse | Yes (teacher) |
| POST | /enrollments/{id}/approve | - | EnrollmentResponse | Yes (teacher) |
| POST | /enrollments/{id}/reject | - | EnrollmentResponse | Yes (teacher) |
| DELETE | /enrollments/{id} | - | Void | Yes |

### 5.4 Modules

| Method | Endpoint | Request | Response | Auth |
|--------|----------|---------|----------|------|
| POST | /courses/{courseId}/modules | CreateModuleRequest | ModuleResponse | Yes (teacher) |
| GET | /courses/{courseId}/modules | - | Flux<ModuleResponse> | Yes |
| GET | /modules/{id} | - | ModuleResponse | Yes |
| PATCH | /modules/{id} | UpdateModuleRequest | ModuleResponse | Yes (teacher) |
| DELETE | /modules/{id} | - | Void | Yes (teacher) |

### 5.5 Lessons

| Method | Endpoint | Request | Response | Auth |
|--------|----------|---------|----------|------|
| POST | /modules/{moduleId}/lessons | CreateLessonRequest | LessonResponse | Yes (teacher) |
| GET | /modules/{moduleId}/lessons | - | Flux<LessonResponse> | Yes |
| GET | /lessons/{id} | - | LessonResponse | Yes |
| PATCH | /lessons/{id} | UpdateLessonRequest | LessonResponse | Yes (teacher) |
| DELETE | /lessons/{id} | - | Void | Yes (teacher) |

### 5.6 Summaries

| Method | Endpoint | Request | Response | Auth |
|--------|----------|---------|----------|------|
| GET | /lessons/{lessonId}/summary | - | SummaryResponse | Yes |
| POST | /lessons/{lessonId}/summary | CreateSummaryRequest | SummaryResponse | Yes (teacher) |

### 5.7 Exercises

| Method | Endpoint | Request | Response | Auth |
|--------|----------|---------|----------|------|
| POST | /lessons/{lessonId}/exercises | CreateExerciseRequest | ExerciseResponse | Yes (teacher) |
| GET | /lessons/{lessonId}/exercises | - | Flux<ExerciseResponse> | Yes |
| GET | /exercises/{id} | - | ExerciseResponse | Yes |
| PATCH | /exercises/{id} | UpdateExerciseRequest | ExerciseResponse | Yes (teacher) |
| DELETE | /exercises/{id} | - | Void | Yes (teacher) |

### 5.8 Questions

| Method | Endpoint | Request | Response | Auth |
|--------|----------|---------|----------|------|
| GET | /exercises/{exerciseId}/questions | - | Flux<QuestionResponse> | Yes |
| POST | /exercises/{exerciseId}/questions | CreateQuestionsRequest | Flux<QuestionResponse> | Yes (teacher) |
| PATCH | /exercises/{exerciseId}/questions | UpdateQuestionsRequest | QuestionsResponse | Yes (teacher) |

### 5.9 Submissions

| Method | Endpoint | Request | Response | Auth |
|--------|----------|---------|----------|------|
| GET | /exercises/{exerciseId}/submissions | - | Flux<SubmissionResponse> | Yes |
| GET | /exercises/{exerciseId}/my-submission | - | SubmissionResponse | Yes |
| POST | /exercises/{exerciseId}/submissions | CreateSubmissionRequest | SubmissionResponse | Yes |
| GET | /submissions/{id} | - | SubmissionResponse | Yes |
| POST | /submissions/{id}/submit | - | SubmissionResponse | Yes |
| POST | /submissions/{id}/approve | - | SubmissionResponse | Yes (teacher) |
| POST | /submissions/{id}/revision | RevisionRequest | SubmissionResponse | Yes (teacher) |
| POST | /answers/{answerId}/comment | CommentRequest | SubmissionResponse | Yes (teacher) |

---

## 6. REQUEST/RESPONSE PAYLOADS

### 6.1 Auth Payloads

```kotlin
// Requests
data class UserCreationRequest(
    val username: String,
    val email: String,
    val password: String
)

data class LoginReq(
    val username: String,
    val password: String
)

// Responses
data class UserResponse(
    val id: String,
    val username: String,
    val email: String?
)

data class Token(
    val token: String,
    val expiredAt: Long
)

data class LoginResponse(
    val user: UserResponse,
    val accessToken: Token
)
```

### 6.2 Course Payloads

```kotlin
// Requests
data class CreateCourseRequest(
    val title: String,
    val description: String? = null,
    val visibility: Visibility? = Visibility.PRIVATE,
    val settings: Map<String, Any>? = null
)

data class UpdateCourseRequest(
    val title: String? = null,
    val description: String? = null,
    val visibility: Visibility? = null,
    val settings: Map<String, Any>? = null
)

// Responses
data class CourseResponse(
    val id: String,
    val title: String,
    val description: String?,
    val visibility: Visibility,
    val createdBy: UserSummary,
    val settings: Map<String, Any>?,
    val createdAt: Long,
    val updatedAt: Long,
    val moduleCount: Long? = null,
    val enrollmentCount: Long? = null
)

data class UserSummary(
    val id: String,
    val name: String,
    val email: String?,
    val avatarUrl: String? = null
)
```

### 6.3 Enrollment Payloads

```kotlin
data class EnrollmentRequest(
    val role: EnrollmentRole? = EnrollmentRole.LEARNER
)

data class UpdateEnrollmentRequest(
    val role: EnrollmentRole? = null,
    val status: EnrollmentStatus? = null
)

data class EnrollmentResponse(
    val id: String,
    val courseId: String,
    val courseName: String,
    val user: UserSummary,
    val role: EnrollmentRole,
    val status: EnrollmentStatus,
    val joinedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
```

### 6.4 Module Payloads

```kotlin
data class CreateModuleRequest(
    val title: String,
    val orderIndex: Int? = null
)

data class UpdateModuleRequest(
    val title: String? = null,
    val orderIndex: Int? = null
)

data class ModuleResponse(
    val id: String,
    val courseId: String,
    val title: String,
    val orderIndex: Int,
    val lessonCount: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
```

### 6.5 Lesson Payloads

```kotlin
data class CreateLessonRequest(
    val title: String,
    val orderIndex: Int? = null
)

data class UpdateLessonRequest(
    val title: String? = null,
    val orderIndex: Int? = null
)

data class LessonResponse(
    val id: String,
    val moduleId: String,
    val title: String,
    val orderIndex: Int,
    val hasSummary: Boolean,
    val exerciseCount: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
```

### 6.6 Summary Payloads

```kotlin
data class CreateSummaryRequest(
    val content: String
)

data class SummaryResponse(
    val id: String,
    val lessonId: String,
    val content: String,
    val editedBy: UserSummary,
    val version: Int,
    val createdAt: Long,
    val updatedAt: Long
)
```

### 6.7 Exercise Payloads

```kotlin
data class CreateExerciseRequest(
    val title: String,
    val type: ExerciseType,
    val settings: Map<String, Any>? = null,
    val orderIndex: Int? = null
)

data class UpdateExerciseRequest(
    val title: String? = null,
    val type: ExerciseType? = null,
    val settings: Map<String, Any>? = null,
    val orderIndex: Int? = null
)

data class ExerciseResponse(
    val id: String,
    val lessonId: String,
    val title: String,
    val type: ExerciseType,
    val settings: Map<String, Any>?,
    val orderIndex: Int,
    val questionCount: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
```

### 6.8 Question Payloads

```kotlin
data class CreateQuestionsRequest(
    val questions: List<CreateQuestionItem>
)

data class CreateQuestionItem(
    val content: String,
    val orderIndex: Int? = null,
    val meta: Map<String, Any>? = null,
    val visibility: QuestionVisibility? = QuestionVisibility.VISIBLE
)

data class UpdateQuestionsRequest(
    val questions: List<UpdateQuestionItem>
)

data class UpdateQuestionItem(
    val id: String? = null,  // null for CREATE
    val action: QuestionAction,  // CREATE, UPDATE, DELETE
    val content: String? = null,
    val orderIndex: Int? = null,
    val meta: Map<String, Any>? = null,
    val visibility: QuestionVisibility? = null
)

enum class QuestionAction { CREATE, UPDATE, DELETE }

data class QuestionResponse(
    val id: String,
    val exerciseId: String,
    val content: String,
    val orderIndex: Int,
    val meta: Map<String, Any>?,
    val visibility: QuestionVisibility,
    val createdAt: Long,
    val updatedAt: Long
)

data class QuestionsResponse(
    val created: List<QuestionResponse>,
    val updated: List<QuestionResponse>,
    val deleted: List<String>  // UUIDs
)
```

### 6.9 Submission Payloads

```kotlin
data class CreateSubmissionRequest(
    val answers: List<AnswerRequest>
)

data class AnswerRequest(
    val questionId: String,
    val answer: String
)

data class RevisionRequest(
    val feedback: String? = null
)

data class CommentRequest(
    val comment: String
)

data class SubmissionResponse(
    val id: String,
    val exerciseId: String,
    val userId: String,
    val status: SubmissionStatus,
    val answers: List<StudentAnswerResponse>,
    val createdAt: Long,
    val updatedAt: Long,
    val submittedAt: Long?,
    val approvedAt: Long?
)

data class StudentAnswerResponse(
    val id: String,
    val questionId: String,
    val answer: String?,
    val teacherComment: String?,
    val createdAt: Long,
    val updatedAt: Long
)
```

---

## 7. SECURITY CONFIGURATION

### 7.1 JWT Authentication

**Token Structure:**
- Header: `access_token`
- Expiration: 24 hours (access), 7 days (refresh)
- Claims: Contains `user` object with UserResponse

**JwtProvider Methods:**
- `generateAccessToken(user: User): Token`
- `generateRefreshToken(user: User): String`
- `validateToken(token: String): Boolean`
- `getUsernameFromToken(token: String): String`
- `getUserIdFromToken(token: String): String`

### 7.2 Security Filter Chain

```kotlin
@Bean
fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    return http
        .csrf { it.disable() }
        .cors { it.configurationSource(corsConfigurationSource()) }
        .sessionManagement { it.sessionCreationPolicy(STATELESS) }
        .authorizeHttpRequests { auth ->
            permittedEndpoints.forEach { auth.requestMatchers(it).permitAll() }
            auth.anyRequest().authenticated()
        }
        .exceptionHandling { it.authenticationEntryPoint(authEntryPoint) }
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        .build()
}
```

### 7.3 JWT Auth Filter

1. Extract token from `access_token` header
2. Validate token
3. Extract username, load UserDetails
4. Set SecurityContext
5. Add `X-User-Id` header to request

### 7.4 MutableHttpServletRequest

Utility to add/remove headers:
- `addHeader(name: String, value: String)`
- `removeHeader(name: String)`

---

## 8. SERVICE LAYER PATTERNS

### 8.1 Reactive Pattern

All services use:
```kotlin
fun operation(...): Mono<T> {
    return Mono.fromCallable {
        // Blocking JPA operations
        result
    }.subscribeOn(Schedulers.boundedElastic())
}
```

### 8.2 Authorization Checks

**Course Access:**
- Creator always has access
- ACTIVE enrollment required for enrolled access
- PUBLIC courses visible to all authenticated users

**Teacher Operations:**
- Check `isTeacherInCourse(courseId, userId)` or `isCreator()`
- Throw `UnauthorizedException` if not teacher

**Learner Operations:**
- Check `isEnrolledInCourse(courseId, userId)`

### 8.3 Service Interfaces

Each service has interface + impl pattern:
```kotlin
interface CourseService {
    fun createCourse(userId: UUID, request: CreateCourseRequest): Mono<CourseResponse>
    // ...
}

@Service
class CourseServiceImpl(
    private val courseRepo: CourseRepo,
    private val enrollmentRepo: EnrollmentRepo,
    // ...
) : CourseService {
    // implementations
}
```

---

## 9. REPOSITORY LAYER

### 9.1 Custom Query Methods

**CourseRepo:**
- `findByCreatedBy_Id(userId: UUID): List<Course>`
- `findByEnrolledUser(userId: UUID): List<Course>` (custom @Query)
- `findPublicCourses(): List<Course>` (custom @Query)
- `existsByIdAndCreatedBy_Id(courseId: UUID, userId: UUID): Boolean`

**EnrollmentRepo:**
- `findByCourse_IdAndUser_Id(courseId: UUID, userId: UUID): Enrollment?`
- `findActiveEnrollment(courseId: UUID, userId: UUID): Enrollment?`
- `countByCourse_IdAndStatus(courseId: UUID, status: EnrollmentStatus): Long`

**ModuleRepo:**
- `findByCourse_IdOrderByOrderIndexAsc(courseId: UUID): List<Module>`
- `findMaxOrderIndexByCourseId(courseId: UUID): Int?`
- `countByCourse_Id(courseId: UUID): Long`

**QuestionRepo:**
- `findByExerciseIdOrderByOrderIndexAsc(exerciseId: UUID): List<Question>`
- `findByExerciseIdAndVisibility(exerciseId: UUID, visibility: QuestionVisibility): List<Question>`

**SubmissionRepo:**
- `findByExercise_IdAndUser_Id(exerciseId: UUID, userId: UUID): Submission?`

---

## 10. ERROR HANDLING

### 10.1 Custom Exceptions

```kotlin
open class BusinessException(message: String) : RuntimeException(message)
class BadRequestException(message: String) : BusinessException(message)
class ResourceNotFoundException(message: String) : BusinessException(message)
class UnauthorizedException(message: String) : BusinessException(message)
class ValidationException(message: String) : BusinessException(message)
```

### 10.2 GlobalExceptionHandler

Maps exceptions to HTTP responses:
- `ResponseStatusException` -> extract status/reason
- `InvalidFormatException` -> 400 Bad Request
- `InsufficientAuthenticationException` -> 401
- `ResourceNotFoundException` -> 404
- `UnauthorizedException` -> 403
- `BusinessException` -> 400
- Generic -> 500

### 10.3 Error Response Structure

```kotlin
data class ErrorResp(
    val timestamp: Long,
    val path: String,
    val status: HttpStatus,
    val requestId: String,
    val traceId: String,
    val errors: MutableList<ApiError>
)

sealed class ApiError
data class InputError(val field: String, val message: String) : ApiError()
data class GenericError(val code: String, val message: String) : ApiError()
```

---

## 11. MAPPERS

### 11.1 Mapper Pattern

Each mapper is an object with static methods:
```kotlin
object CourseMapper {
    fun toResponse(
        course: Course,
        moduleCount: Long? = null,
        enrollmentCount: Long? = null
    ): CourseResponse {
        return CourseResponse(
            id = course.id.toString(),
            title = course.title,
            // ...
        )
    }
}
```

### 11.2 UserSummary Extraction

```kotlin
fun toUserSummary(user: User): UserSummary {
    return UserSummary(
        id = user.id.toString(),
        name = user.name,
        email = user.email,
        avatarUrl = user.avatarUrl
    )
}
```

---

## 12. UTILITIES

### 12.1 IdGenerator

```kotlin
object IdGenerator {
    fun generate(prefix: String = "lb"): String {
        val uuid = UUID.randomUUID()
        val base64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(uuid.toByteArray())
        return "${prefix}_$base64"
    }
}
```

### 12.2 Request Header Constants

```kotlin
object PZRequestHeader {
    const val USER_ID = "X-User-Id"
}
```

---

## 13. ENUMERATIONS

```kotlin
// User
enum class UserRole { ADMIN, USER }
enum class UserStatus { ACTIVE, INACTIVE, BANNED }

// Course
enum class Visibility { PUBLIC, PRIVATE, UNLISTED }
enum class EnrollmentRole { LEARNER, TEACHER }
enum class EnrollmentStatus { PENDING, ACTIVE, REJECTED, INACTIVE }

// Exercise
enum class ExerciseType { TEXT_ANSWER, CODE, FILE_UPLOAD, MULTIPLE_CHOICE, PROJECT_LINK }
enum class QuestionVisibility { VISIBLE, HIDDEN }
enum class SubmissionStatus { DRAFT, SUBMITTED, APPROVED, NEEDS_REVISION }

// Questions batch
enum class QuestionAction { CREATE, UPDATE, DELETE }
```

---

## 14. KEY IMPLEMENTATION DETAILS

### 14.1 Soft Delete Pattern

```kotlin
@Entity
@SQLDelete(sql = "UPDATE courses SET deleted_at = EXTRACT(EPOCH FROM NOW()) * 1000 WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
class Course { ... }
```

### 14.2 JSONB Columns

```kotlin
@Column(columnDefinition = "jsonb")
@JdbcTypeCode(SqlTypes.JSON)
var settings: Map<String, Any>? = null
```

### 14.3 Timestamp Pattern

All entities use `Long` timestamps:
```kotlin
@Column(name = "created_at", updatable = false)
val createdAt: Long = Instant.now().toEpochMilli()

@Column(name = "updated_at")
var updatedAt: Long = Instant.now().toEpochMilli()
```

### 14.4 Auto OrderIndex

When orderIndex is null, auto-assign next index:
```kotlin
val nextIndex = repo.findMaxOrderIndexByParentId(parentId)?.plus(1) ?: 0
entity.orderIndex = request.orderIndex ?: nextIndex
```

### 14.5 Course Creation with Auto-Enrollment

When creating a course, automatically create TEACHER enrollment:
```kotlin
fun createCourse(userId: UUID, request: CreateCourseRequest): Mono<CourseResponse> {
    return Mono.fromCallable {
        val user = userRepo.findById(userId).orElseThrow()
        val course = courseRepo.save(Course(..., createdBy = user))

        enrollmentRepo.save(Enrollment(
            course = course,
            user = user,
            role = EnrollmentRole.TEACHER,
            status = EnrollmentStatus.ACTIVE,
            joinedAt = Instant.now().toEpochMilli()
        ))

        CourseMapper.toResponse(course, 0, 1)
    }.subscribeOn(Schedulers.boundedElastic())
}
```

---

## 15. CONTROLLER PATTERNS

### 15.1 Standard Controller Structure

```kotlin
@RestController
@RequestMapping("/v1/courses")
class CourseController(
    private val courseService: CourseService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCourse(
        @RequestHeader(PZRequestHeader.USER_ID) userId: UUID,
        @RequestBody request: CreateCourseRequest
    ): Mono<CourseResponse> {
        return courseService.createCourse(userId, request)
    }

    @GetMapping
    fun listCourses(
        @RequestHeader(PZRequestHeader.USER_ID) userId: UUID,
        @RequestParam(defaultValue = "enrolled") type: String
    ): Flux<CourseResponse> {
        return when (type) {
            "created" -> courseService.listUserCreatedCourses(userId)
            "enrolled" -> courseService.listEnrolledCourses(userId)
            "public" -> courseService.listPublicCourses()
            else -> Flux.error(BadRequestException("Invalid type"))
        }
    }
}
```

### 15.2 Nested Resource Controllers

```kotlin
@RestController
@RequestMapping("/v1")
class ModuleController(private val moduleService: ModuleService) {

    @PostMapping("/courses/{courseId}/modules")
    @ResponseStatus(HttpStatus.CREATED)
    fun createModule(
        @PathVariable courseId: UUID,
        @RequestHeader(PZRequestHeader.USER_ID) userId: UUID,
        @RequestBody request: CreateModuleRequest
    ): Mono<ModuleResponse> = moduleService.createModule(courseId, userId, request)

    @GetMapping("/modules/{moduleId}")
    fun getModule(
        @PathVariable moduleId: UUID,
        @RequestHeader(PZRequestHeader.USER_ID) userId: UUID
    ): Mono<ModuleResponse> = moduleService.getModuleById(moduleId, userId)
}

@RestController
@RequestMapping("/v1")
class LessonController(private val lessonService: LessonService) {

    @PostMapping("/modules/{moduleId}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    fun createLesson(
        @PathVariable moduleId: UUID,
        @RequestHeader(PZRequestHeader.USER_ID) userId: UUID,
        @RequestBody request: CreateLessonRequest
    ): Mono<LessonResponse> = lessonService.createLesson(moduleId, userId, request)

    @GetMapping("/modules/{moduleId}/lessons")
    fun listLessons(
        @PathVariable moduleId: UUID,
        @RequestHeader(PZRequestHeader.USER_ID) userId: UUID
    ): Flux<LessonResponse> = lessonService.listLessonsByModule(moduleId, userId)

    @GetMapping("/lessons/{lessonId}")
    fun getLesson(
        @PathVariable lessonId: UUID,
        @RequestHeader(PZRequestHeader.USER_ID) userId: UUID
    ): Mono<LessonResponse> = lessonService.getLessonById(lessonId, userId)
}
```

---

## 16. BUILD CONFIGURATION

### build.gradle.kts

```kotlin
plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

---

## 17. APPLICATION ENTRY POINT

```kotlin
package com.example.lib4gz

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class Lib4gzApplication

fun main(args: Array<String>) {
    runApplication<Lib4gzApplication>(*args)
}
```

---

## IMPLEMENTATION CHECKLIST

### Phase 1: Foundation
- [x] Project setup with Gradle Kotlin DSL
- [x] Spring Boot configuration
- [x] Database configuration (PostgreSQL)
- [x] Security configuration (JWT)

### Phase 2: Authentication
- [x] User entity and repository
- [x] Auth controller (register, login)
- [x] JWT provider and filter
- [x] Security filter chain

### Phase 3: Course Management
- [x] Course entity and CRUD
- [x] Enrollment management
- [x] Module CRUD
- [x] Lesson CRUD
- [x] Summary feature

### Phase 4: Exercises
- [x] Exercise entity and CRUD
- [x] Question batch operations
- [x] Submission workflow
- [x] Teacher feedback (comments)

### Phase 5: Error Handling
- [x] Custom exceptions
- [x] Global exception handler
- [x] Error response structure

---

**END OF REQUIREMENTS DOCUMENT**
