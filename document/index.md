# lib4gz - LMS Platform Backend

A Spring Boot backend for a Learning Management System (LMS) supporting course management, enrollment, lesson delivery, exercises, and submission workflows.

---

## Tech Stack

| Component         | Version / Tool      |
| ----------------- | ------------------- |
| Language          | Kotlin 1.9.25       |
| Framework         | Spring Boot 3.5.7   |
| Build System      | Gradle (Kotlin DSL) |
| JDK               | 21                  |
| Database          | PostgreSQL           |
| Persistence       | Spring Data JPA      |

---

## Documentation Map

```
document/
├── index.md                          <- You are here
├── spec/
│   └── openapi.yaml                  # OpenAPI 3.1.0 - Full API specification
├── architecture/
│   ├── overview.md                   # Architecture patterns & conventions
│   ├── project-structure.md          # Package & file organization
│   └── database.md                   # Database schema (DDL)
├── features/
│   ├── auth.md                       # Authentication (User, Login, Register)
│   ├── course.md                     # Course management
│   ├── enrollment.md                 # Enrollment & roles
│   ├── module.md                     # Course modules
│   ├── lesson.md                     # Module lessons
│   ├── summary.md                    # Lesson summaries
│   ├── exercise.md                   # Exercises
│   ├── question.md                   # Questions (batch ops)
│   └── submission.md                 # Submissions & answers
├── models/
│   ├── auth.yaml                    # User entity, auth DTOs, UserMapper
│   ├── course.yaml                  # Course entity, Visibility enum, course DTOs
│   ├── enrollment.yaml              # Enrollment entity, enroll enums & DTOs
│   ├── module.yaml                  # Module entity, module DTOs, ModuleMapper
│   ├── lesson.yaml                  # Lesson entity, lesson DTOs, LessonMapper
│   ├── summary.yaml                 # Summary entity, summary DTOs, SummaryMapper
│   ├── exercise.yaml                # Exercise entity, ExerciseType enum, DTOs
│   ├── question.yaml                # Question entity, question enums & DTOs
│   ├── submission.yaml              # Submission entities, status enum, DTOs
│   └── common.yaml                  # Error models, exceptions, security models
├── infrastructure/
│   ├── security.md                   # JWT, filters, CORS
│   ├── error-handling.md             # Exceptions & error responses
│   └── configuration.md             # App config files
└── lib4gz-postman-collection.json    # Postman collection
```

### Specification

- [OpenAPI 3.1.0 - Full API Specification](spec/openapi.yaml)

### Architecture

- [Architecture Patterns & Conventions](architecture/overview.md)
- [Package & File Organization](architecture/project-structure.md)
- [Database Schema (DDL)](architecture/database.md)

### Features

- [Authentication (User, Login, Register)](features/auth.md)
- [Course Management](features/course.md)
- [Enrollment & Roles](features/enrollment.md)
- [Course Modules](features/module.md)
- [Module Lessons](features/lesson.md)
- [Lesson Summaries](features/summary.md)
- [Exercises](features/exercise.md)
- [Questions (Batch Operations)](features/question.md)
- [Submissions & Answers](features/submission.md)

### Models (`models/`)
YAML model definitions for all entities, enums, DTOs, and mappers. Referenced by feature docs and OpenAPI spec.

| File | Description |
|------|-------------|
| [auth.yaml](models/auth.yaml) | User entity, auth DTOs, UserMapper |
| [course.yaml](models/course.yaml) | Course entity, Visibility enum, course DTOs, UserSummary, CourseMapper |
| [enrollment.yaml](models/enrollment.yaml) | Enrollment entity, EnrollmentRole/Status enums, enrollment DTOs |
| [module.yaml](models/module.yaml) | Module entity, module DTOs, ModuleMapper |
| [lesson.yaml](models/lesson.yaml) | Lesson entity, lesson DTOs, LessonMapper |
| [summary.yaml](models/summary.yaml) | Summary entity, summary DTOs, SummaryMapper |
| [exercise.yaml](models/exercise.yaml) | Exercise entity, ExerciseType enum, exercise DTOs |
| [question.yaml](models/question.yaml) | Question entity, QuestionVisibility/Action enums, question DTOs |
| [submission.yaml](models/submission.yaml) | Submission + StudentAnswer entities, SubmissionStatus enum, submission DTOs |
| [common.yaml](models/common.yaml) | Error models (ErrorResp, ApiError), exceptions, security config models |

### Infrastructure

- [JWT, Filters, CORS](infrastructure/security.md)
- [Exceptions & Error Responses](infrastructure/error-handling.md)
- [App Configuration Files](infrastructure/configuration.md)

### Other

- [Postman Collection](lib4gz-postman-collection.json)

---

## Phase 1 Scope

### Included

| Domain                    | Capabilities                                                        |
| ------------------------- | ------------------------------------------------------------------- |
| **Auth**                  | Register, login, JWT issuance and validation, role-based access     |
| **Course Management**     | CRUD courses, modules, lessons, summaries                           |
| **Exercise Management**   | CRUD exercises, batch question creation and updates                 |
| **Submission Management** | Create submissions, submit answers, retrieve submission details     |

### Deferred

The following are **out of scope** for Phase 1 and planned for future iterations:

- Score system (auto-grading, grade book)
- Review workflow with checklist
- File uploads (attachments, media)
- Notifications (email, in-app)
- Leaderboard
- Tags, badges, and comments
- Submission version history
- Public gallery
- OAuth / third-party authentication providers

---

## Quick Start

**1. Start PostgreSQL**

```bash
docker run -d \
  --name lib4gz-postgres \
  -e POSTGRES_DB=mydatabase \
  -e POSTGRES_USER=myuser \
  -e POSTGRES_PASSWORD=mypassword \
  -p 5432:5432 \
  postgres:latest
```

**2. Run the application**

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

**3. Access the API**

```
http://localhost:8080/api
```
