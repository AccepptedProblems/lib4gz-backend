# LMS Platform Backend Documentation - Phase 1 (Feature-Based Architecture)

## Table of Contents
1. [Project Overview](#project-overview)
2. [Phase 1 Scope](#phase-1-scope)
3. [Technology Stack](#technology-stack)
4. [Architecture Overview](#architecture-overview)
5. [Feature-Based Structure](#feature-based-structure)
6. [Database Design](#database-design)
7. [Domain Models with Imports](#domain-models-with-imports)
8. [API Endpoints](#api-endpoints)
9. [Security & Authentication](#security--authentication)
10. [Development Setup](#development-setup)

---

## Project Overview

The LMS Platform is a learning management system that supports both decentralized study groups and centralized classrooms. Phase 1 focuses on delivering the core functionality needed for basic course management, content delivery, and student submissions.

---

## Phase 1 Scope

### ✅ Included in Phase 1

**Feature Modules:**
- **Authentication** - User signup, login, JWT management
- **Course Management** - Course, enrollment, module, lesson, summary CRUD
- **Exercise Management** - Exercise, question creation and management
- **Submission Management** - Student submissions and basic approval workflow

### ❌ Deferred to Phase 2+

- Score and points system
- Review workflow with checklist
- File uploads and management
- Notification system
- Leaderboard and rankings
- Tags, badges, comments
- Submission version history
- Public gallery
- OAuth authentication

---

## Technology Stack

### Core Technologies
- **Language**: Kotlin 1.9+
- **Framework**: Spring Boot 3.2+
- **Build Tool**: Gradle (Kotlin DSL)
- **JDK**: 17+

### Database & Persistence
- **Primary Database**: PostgreSQL 15+
- **ORM**: Spring Data JPA (Hibernate as JPA provider)
- **Migration Tool**: JPA DDL Auto (for development)
- **Data Access**: JPA Repository pattern (no raw JDBC)

---

## Architecture Overview

### Feature-Based Architecture

This project follows a **feature-based architecture** where each feature is organized into its own folder containing:

```
feature/
├── controller/      # HTTP endpoints and request handling
├── service/         # Business logic and workflows
├── repository/      # Data access layer
└── model/          # Data models (entities, DTOs, requests, responses)
```

**Benefits:**
- ✅ **Modularity**: Each feature is self-contained
- ✅ **Scalability**: Easy to add new features without affecting existing ones
- ✅ **Team Collaboration**: Different teams can work on different features
- ✅ **Easy Onboarding**: New developers can understand one feature at a time
- ✅ **Maintainability**: Changes to one feature don't impact others

### Layer Responsibilities

#### Controller Layer
- **Purpose**: Entry point for external interactions
- **Responsibilities**: Receive HTTP requests, validate data, pass to Service, return HTTP responses
- **Does NOT**: Contain business logic or data access

#### Service Layer
- **Purpose**: Core business logic
- **Responsibilities**: Orchestrate workflows, perform calculations/validations, implement business rules
- **Does NOT**: Handle HTTP concerns or direct database access

#### Repository Layer
- **Purpose**: Data access abstraction using Spring Data JPA
- **Responsibilities**:
  - Extend `JpaRepository<Entity, ID>` interface
  - Define custom query methods using method naming conventions
  - Use `@Query` annotations for complex queries
  - No manual JDBC code required
- **Does NOT**: Contain business logic or SQL connections

#### Model Package
- **Contains**: Entities (JPA), DTOs, Request/Response classes, Enums

### Spring Data JPA Approach

This project uses **Spring Data JPA** for all database operations:

- **No JDBC code**: All database interactions through JPA entities and repositories
- **Automatic query generation**: Spring Data JPA generates queries from method names
- **Repository pattern**: Interfaces extend `JpaRepository<Entity, ID>`
- **Entity annotations**: Using JPA annotations (`@Entity`, `@Table`, `@Column`, etc.)
- **Hibernate as provider**: Hibernate implements the JPA specification under the hood

**Example Repository**:
```kotlin
@Repository
interface UserRepository : JpaRepository<User, String> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}
// Spring Data JPA automatically implements these methods!
```

---

## Feature-Based Structure

```
src/main/kotlin/com/example/lib4gz/
├── common/                          # Shared code across features
│   ├── config/
│   │   ├── SecurityConfig.kt
│   │   ├── JpaConfig.kt
│   │   └── WebConfig.kt
│   ├── security/
│   │   ├── JwtTokenProvider.kt
│   │   ├── JwtAuthenticationFilter.kt
│   │   ├── CustomUserDetailsService.kt
│   │   ├── CurrentUser.kt
│   │   └── SecurityPolicy.kt
│   ├── exception/
│   │   ├── GlobalExceptionHandler.kt
│   │   ├── ResourceNotFoundException.kt
│   │   ├── UnauthorizedException.kt
│   │   ├── ValidationException.kt
│   │   └── BusinessException.kt
│   ├── util/
│   │   ├── ValidationUtils.kt
│   │   └── DateUtils.kt
│   └── model/
│       ├── ErrorResponse.kt
│       └── PageResponse.kt
│
├── auth/                        # Authentication Feature
│   ├── controller/
│   │   └── AuthController.kt
│   ├── service/
│   │   └── AuthService.kt
│   ├── repository/
│   │   └── UserRepository.kt
│   └── model/
│       ├── entity/
│       │   ├── User.kt              # Entity
│       │   └── UserRole.kt          # Enum
│       ├── payload/
│       │   ├── SignupRequest.kt
│       │   ├── LoginRequest.kt
│       │   └── AuthResponse.kt
│       └── mapper/
│           └── UserMapper.kt
│
├── course/                      # Course Management Feature
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
│   ├── repository/
│   │   ├── CourseRepository.kt
│   │   ├── EnrollmentRepository.kt
│   │   ├── ModuleRepository.kt
│   │   ├── LessonRepository.kt
│   │   └── SummaryRepository.kt
│   └── model/
│       ├── entity/
│       │   ├── Course.kt
│       │   ├── Enrollment.kt
│       │   ├── EnrollmentRole.kt
│       │   ├── EnrollmentStatus.kt
│       │   ├── Module.kt
│       │   ├── Lesson.kt
│       │   └── Summary.kt
│       ├── request/
│       │   ├── CreateCourseRequest.kt
│       │   ├── UpdateCourseRequest.kt
│       │   ├── EnrollmentRequest.kt
│       │   ├── ModuleRequest.kt
│       │   ├── LessonRequest.kt
│       │   └── SummaryRequest.kt
│       └── response/
│           ├── CourseResponse.kt
│           ├── EnrollmentResponse.kt
│           ├── ModuleResponse.kt
│           ├── LessonResponse.kt
│           └── SummaryResponse.kt
│
└── exercise/                    # Exercise & Submission Management Feature
    ├── controller/
    │   ├── ExerciseController.kt
    │   ├── QuestionController.kt
    │   └── SubmissionController.kt
    ├── service/
    │   ├── ExerciseService.kt
    │   ├── QuestionService.kt
    │   └── SubmissionService.kt
    ├── repository/
    │   ├── ExerciseRepository.kt
    │   ├── QuestionRepository.kt
    │   ├── SubmissionRepository.kt
    │   └── StudentAnswerRepository.kt
    └── model/
        ├── entity/
        │   ├── Exercise.kt
        │   ├── ExerciseType.kt
        │   ├── Question.kt
        │   ├── Submission.kt
        │   ├── SubmissionStatus.kt
        │   └── StudentAnswer.kt
        ├── request/
        │   ├── CreateExerciseRequest.kt
        │   ├── UpdateExerciseRequest.kt
        │   ├── CreateQuestionRequest.kt
        │   ├── UpdateQuestionRequest.kt
        │   ├── CreateSubmissionRequest.kt
        │   ├── UpdateSubmissionRequest.kt
        │   └── SubmitForReviewRequest.kt
        └── response/
            ├── ExerciseResponse.kt
            ├── QuestionResponse.kt
            ├── SubmissionResponse.kt
            └── ApprovalResponse.kt

src/main/resources/
├── application.yml
├── application-dev.yml
└── security.json
```

---

## Database Design

### Database Schema

```sql
-- Users
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    username VARCHAR(255) UNIQUE NOT NULL,
    passwordHash VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    avatarUrl TEXT,
    emailVerified BOOLEAN DEFAULT FALSE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'BANNED')),
    createdAt BIGINT NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT
);

-- User Roles Junction Table
CREATE TABLE user_global_roles (
    userId VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    PRIMARY KEY (userId, role)
);

-- Courses
CREATE TABLE courses (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    visibility VARCHAR(50) NOT NULL DEFAULT 'PRIVATE',
    createdBy VARCHAR(36) NOT NULL REFERENCES users(id),
    settings JSONB DEFAULT '{}',
    createdAt BIGINT NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT
);

-- Enrollments
CREATE TABLE enrollments (
    id VARCHAR(36) PRIMARY KEY,
    courseId VARCHAR(36) NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    userId VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL CHECK (role IN ('LEARNER', 'TEACHER')),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'INACTIVE')),
    joinedAt BIGINT,
    createdAt BIGINT NOT NULL,
    updatedAt BIGINT NOT NULL,
    UNIQUE(courseId, userId)
);

-- Modules
CREATE TABLE modules (
    id VARCHAR(36) PRIMARY KEY,
    courseId VARCHAR(36) NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    orderIndex INTEGER NOT NULL,
    createdAt BIGINT NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    UNIQUE(courseId, orderIndex)
);

-- Lessons
CREATE TABLE lessons (
    id VARCHAR(36) PRIMARY KEY,
    moduleId VARCHAR(36) NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    orderIndex INTEGER NOT NULL,
    createdAt BIGINT NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    UNIQUE(moduleId, orderIndex)
);

-- Summaries
CREATE TABLE summaries (
    id VARCHAR(36) PRIMARY KEY,
    lessonId VARCHAR(36) NOT NULL REFERENCES lessons(id) ON DELETE CASCADE UNIQUE,
    content TEXT NOT NULL,
    editedBy VARCHAR(36) NOT NULL REFERENCES users(id),
    version INTEGER NOT NULL DEFAULT 1,
    createdAt BIGINT NOT NULL,
    updatedAt BIGINT NOT NULL
);

-- Exercises
CREATE TABLE exercises (
    id VARCHAR(36) PRIMARY KEY,
    lessonId VARCHAR(36) NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('TEXT_ANSWER', 'CODE', 'FILE_UPLOAD', 'MULTIPLE_CHOICE', 'PROJECT_LINK')),
    settings JSONB DEFAULT '{}',
    orderIndex INTEGER NOT NULL,
    createdAt BIGINT NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    UNIQUE(lessonId, orderIndex)
);

-- Questions
CREATE TABLE questions (
    id VARCHAR(36) PRIMARY KEY,
    exerciseId VARCHAR(36) NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    orderIndex INTEGER NOT NULL,
    meta JSONB DEFAULT '{}',
    visibility VARCHAR(50) DEFAULT 'VISIBLE',
    createdAt BIGINT NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT
);

-- Submissions
CREATE TABLE submissions (
    id VARCHAR(36) PRIMARY KEY,
    exerciseId VARCHAR(36) NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    userId VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'NEEDS_REVISION')),
    createdAt BIGINT NOT NULL,
    updatedAt BIGINT NOT NULL,
    submittedAt BIGINT,
    approvedAt BIGINT,
    UNIQUE(exerciseId, userId)
);

-- Student Answers
CREATE TABLE student_answers (
    id VARCHAR(36) PRIMARY KEY,
    questionId VARCHAR(36) NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    submissionId VARCHAR(36) NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    answer TEXT,
    teacherComment TEXT,
    createdAt BIGINT NOT NULL,
    updatedAt BIGINT NOT NULL
);
```

### Indexes

```sql
-- Performance indexes
CREATE INDEX idx_enrollments_user ON enrollments(userId);
CREATE INDEX idx_enrollments_course ON enrollments(courseId);
CREATE INDEX idx_enrollments_status ON enrollments(status);
CREATE INDEX idx_submissions_user ON submissions(userId);
CREATE INDEX idx_submissions_exercise ON submissions(exerciseId);
CREATE INDEX idx_submissions_status ON submissions(status);
CREATE INDEX idx_modules_course ON modules(courseId, orderIndex);
CREATE INDEX idx_lessons_module ON lessons(moduleId, orderIndex);
CREATE INDEX idx_exercises_lesson ON exercises(lessonId, orderIndex);
CREATE INDEX idx_questions_exercise ON questions(exerciseId, orderIndex);
CREATE INDEX idx_student_answers_question ON student_answers(questionId);
CREATE INDEX idx_student_answers_submission ON student_answers(submissionId);
```

---

## Domain Models with Imports

### Model Design Philosophy

This project uses a **hybrid approach** for JPA relationships, balancing performance, type safety, and flexibility:

#### When to Use JPA Relationships (`@ManyToOne`, `@OneToMany`, `@OneToOne`):
✅ **Use relationships when you need automatic joining:**
- **Parent → Children collections**: Course has many Modules, Module has many Lessons
- **Child → Parent navigation**: Lesson belongs to Module, Summary belongs to Lesson
- **Cross-module references**: Course created by User, Summary edited by User

**Benefits:**
- Automatic JOIN queries with lazy/eager loading control
- Type-safe navigation (e.g., `lesson.module.course.title`)
- Cascade operations (delete parent → children deleted automatically)
- Hibernate manages foreign key constraints

#### When to Keep UUID References:
✅ **Keep UUID fields when you want manual control:**
- **Enrollment**: Uses `courseId` and `userId` instead of relationships to Course/User
  - Reason: Avoid loading full Course/User objects when checking permissions
  - More efficient for permission queries
- **Module**: Uses `courseId` as UUID (but could be changed to relationship if auto-join preferred)

#### Key Annotations Explained:

**Relationship Management:**
- `@ManyToOne(fetch = FetchType.LAZY)` - Load related entity only when accessed
- `@OneToMany(mappedBy = "...", cascade = [...])` - Defines inverse side of relationship
- `@OneToOne` - One-to-one relationship (e.g., Lesson ↔ Summary)
- `@JoinColumn(name = "...")` - Specifies foreign key column name

**Soft Delete Support:**
- `@SQLDelete(sql = "UPDATE table SET deletedAt = ? WHERE id = ?")` - Custom delete SQL
- `@Where(clause = "deletedAt IS NULL")` - Automatically filters out deleted records in queries

**Data Integrity:**
- `orphanRemoval = true` - Delete child when removed from parent collection
- `cascade = [CascadeType.ALL]` - Propagate operations to children
- `@OrderBy("orderIndex ASC")` - Maintain order in collections

**UUID Storage:**
- `@Column(length = 36)` - Stores UUID as VARCHAR(36) for PostgreSQL compatibility
- Alternative: `@Column(columnDefinition = "UUID")` for native UUID type

### Feature: Auth

**User Entity:**
```kotlin
package com.example.lib4gz.auth.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deletedAt = ? WHERE id = ?")
data class User(
    @Id
    @Column(length = 36)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = true, unique = true, length = 255)
    val email: String,

    @Column(nullable = false, unique = true, length = 255)
    val username: String,

    @Column(name = "passwordHash", length = 255)
    val password: String? = null,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column
    var avatarUrl: String? = null,

    @ElementCollection(targetClass = UserRole::class, fetch = FetchType.EAGER)
    @CollectionTable(name = "user_global_roles", joinColumns = [JoinColumn(name = "userId")])
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    val roles: MutableSet<UserRole> = mutableSetOf(UserRole.USER),

    @Column(nullable = false)
    var emailVerified: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(nullable = false)
    var updatedAt: Long = Instant.now().toEpochMilli(),

    @Column
    var deletedAt: Long? = null
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now().toEpochMilli()
    }

    fun hasRole(role: UserRole): Boolean = roles.contains(role)
    fun isAdmin(): Boolean = hasRole(UserRole.ADMIN)
}

enum class UserRole {
    ADMIN,
    USER
}

enum class UserStatus {
    ACTIVE,
    INACTIVE,
    BANNED
}
```

### Feature: Course

**Course Entity:**
```kotlin
package com.example.lib4gz.courses.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.Where
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "courses")
@SQLDelete(sql = "UPDATE courses SET deletedAt = ? WHERE id = ?")
@Where(clause = "deletedAt IS NULL")
data class Course(
    @Id
    @Column(length = 36)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 500)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(nullable = false, length = 50)
    var visibility: String = "PRIVATE",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "createdBy", nullable = false)
    val createdBy: User,

    @OneToMany(mappedBy = "course", cascade = [CascadeType.ALL], orphanRemoval = true)
    @Where(clause = "deletedAt IS NULL")
    val modules: MutableList<Module> = mutableListOf(),

    @OneToMany(mappedBy = "course", cascade = [CascadeType.ALL], orphanRemoval = true)
    val enrollments: MutableList<Enrollment> = mutableListOf(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var settings: Map<String, Any> = emptyMap(),

    @Column(nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(nullable = false)
    var updatedAt: Long = Instant.now().toEpochMilli(),

    @Column
    var deletedAt: Long? = null
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now().toEpochMilli()
    }
}
```

**Enrollment Entity:**
```kotlin
@Entity
@Table(
    name = "enrollments",
    uniqueConstraints = [UniqueConstraint(columnNames = ["courseId", "userId"])]
)
data class Enrollment(
    @Id
    @Column(length = 36)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 36)
    val courseId: UUID,

    @Column(nullable = false, length = 36)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var role: EnrollmentRole,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: EnrollmentStatus = EnrollmentStatus.PENDING,

    @Column
    var joinedAt: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(nullable = false)
    var updatedAt: Long = Instant.now().toEpochMilli()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now().toEpochMilli()
    }

    fun approve() {
        status = EnrollmentStatus.ACTIVE
        joinedAt = Instant.now().toEpochMilli()
    }

    fun reject() {
        status = EnrollmentStatus.REJECTED
    }

    fun isActive(): Boolean = status == EnrollmentStatus.ACTIVE
    fun isTeacher(): Boolean = role == EnrollmentRole.TEACHER
    fun isLearner(): Boolean = role == EnrollmentRole.LEARNER
}

enum class EnrollmentRole {
    LEARNER,
    TEACHER
}

enum class EnrollmentStatus {
    PENDING,
    ACTIVE,
    REJECTED,
    INACTIVE
}
```

**Module Entity:**
```kotlin
@Entity
@Table(
    name = "modules",
    uniqueConstraints = [UniqueConstraint(columnNames = ["courseId", "orderIndex"])]
)
@SQLDelete(sql = "UPDATE modules SET deletedAt = ? WHERE id = ?")
@Where(clause = "deletedAt IS NULL")
data class Module(
    @Id
    @Column(length = 36)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courseId", nullable = false)
    val course: Course,

    @Column(nullable = false, length = 500)
    var title: String,

    @Column(nullable = false)
    var orderIndex: Int,

    @OneToMany(mappedBy = "module", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Where(clause = "deletedAt IS NULL")
    val lessons: MutableList<Lesson> = mutableListOf(),

    @Column(nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(nullable = false)
    var updatedAt: Long = Instant.now().toEpochMilli(),

    @Column
    var deletedAt: Long? = null
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now().toEpochMilli()
    }
}
```

**Lesson Entity:**
```kotlin
@Entity
@Table(
    name = "lessons",
    uniqueConstraints = [UniqueConstraint(columnNames = ["moduleId", "orderIndex"])]
)
@SQLDelete(sql = "UPDATE lessons SET deletedAt = ? WHERE id = ?")
@Where(clause = "deletedAt IS NULL")
data class Lesson(
    @Id
    @Column(length = 36)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moduleId", nullable = false)
    val module: Module,

    @Column(nullable = false, length = 500)
    var title: String,

    @Column(nullable = false)
    var orderIndex: Int,

    @OneToOne(mappedBy = "lesson", cascade = [CascadeType.ALL], orphanRemoval = true)
    val summary: Summary? = null,

    @OneToMany(mappedBy = "lesson", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Where(clause = "deletedAt IS NULL")
    val exercises: MutableList<Exercise> = mutableListOf(),

    @Column(nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(nullable = false)
    var updatedAt: Long = Instant.now().toEpochMilli(),

    @Column
    var deletedAt: Long? = null
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now().toEpochMilli()
    }
}
```

**Summary Entity:**
```kotlin
@Entity
@Table(name = "summaries")
data class Summary(
    @Id
    @Column(length = 36)
    val id: UUID = UUID.randomUUID(),

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lessonId", nullable = false, unique = true)
    val lesson: Lesson,

    @Column(columnDefinition = "TEXT", nullable = false)
    var content: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editedBy", nullable = false)
    var editedBy: User,

    @Column(nullable = false)
    var version: Int = 1,

    @Column(nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(nullable = false)
    var updatedAt: Long = Instant.now().toEpochMilli()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now().toEpochMilli()
    }
}
```

---

## API Endpoints

### Authentication
- `POST /api/auth/signup` - Register new user
- `POST /api/auth/login` - Login with email/password
- `POST /api/auth/logout` - Logout current session

### Courses
- `GET /api/courses` - List enrolled courses
- `POST /api/courses` - Create new course
- `GET /api/courses/:id` - Get course details
- `PATCH /api/courses/:id` - Update course
- `DELETE /api/courses/:id` - Delete course

### Enrollment
- `POST /api/courses/:id/enroll` - Request enrollment
- `GET /api/courses/:id/enrollments` - List enrollments
- `PUT /api/enrollments/:id` - Update enrollment status/role
- `DELETE /api/enrollments/:id` - Remove enrollment

### Modules
- `POST /api/courses/:id/modules` - Create module
- `GET /api/modules/:id` - Get module details
- `PATCH /api/modules/:id` - Update module
- `DELETE /api/modules/:id` - Delete module

### Lessons
- `POST /api/modules/:moduleId/lessons` - Create lesson
- `GET /api/modules/:moduleId/lessons` - List lessons by module
- `GET /api/lessons/:id` - Get lesson details
- `PATCH /api/lessons/:id` - Update lesson
- `DELETE /api/lessons/:id` - Delete lesson

### Summaries
- `GET /api/lessons/:id/summary` - Get lesson summary
- `POST /api/lessons/:id/summary` - Create/update summary

### Exercises
- `POST /api/lessons/:id/exercises` - Create exercise
- `GET /api/exercises/:id` - Get exercise details
- `PATCH /api/exercises/:id` - Update exercise
- `DELETE /api/exercises/:id` - Delete exercise

### Questions
- `POST /api/exercises/:id/questions` - Add question
- `PATCH /api/questions/:id` - Update question
- `DELETE /api/questions/:id` - Delete question

### Submissions
- `GET /api/exercises/:id/submissions` - List submissions
- `POST /api/exercises/:id/submissions` - Create/update submission
- `POST /api/submissions/:id/submit` - Submit for review
- `POST /api/submissions/:id/approve` - Approve submission
- `POST /api/submissions/:id/request-revision` - Request revision

---

## Security & Authentication

### JWT-Based Authentication
- Access tokens valid for 24 hours
- Tokens contain user ID, email, and roles
- All endpoints (except auth) require valid token

### Authorization Levels
1. **Global Roles**: ADMIN, USER
2. **Course Roles**: TEACHER, LEARNER (per enrollment)

### Permission Model
- **Course Access**: Must be actively enrolled
- **Content Creation**: TEACHER role required
- **Submission Creation**: LEARNER role required
- **Review/Approval**: TEACHER role required

---

## Development Setup

### Prerequisites
- JDK 17+
- PostgreSQL 15+
- Gradle 8+

### Quick Start
```bash
# Start PostgreSQL
docker run -d --name lms-postgres \
  -e POSTGRES_DB=lms_platform \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15-alpine

# Run application
./gradlew bootRun
```

### Access Points
- API: http://localhost:8080
- Health: http://localhost:8080/actuator/health

---

**Document Version**: Phase 1 - Feature-Based Architecture with JPA DDL Auto
**Last Updated**: December 2, 2024