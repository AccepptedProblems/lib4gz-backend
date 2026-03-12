# lib4gz Project Structure

## Build

- **Build file**: `build.gradle.kts`
- **Root package**: `com.example.lib4gz`
- **Language**: Kotlin
- **Framework**: Spring Boot with WebFlux (reactive) + JPA (blocking, wrapped in reactive schedulers)
- **Database**: PostgreSQL

## Source Tree

```
src/main/kotlin/com/example/lib4gz/
├── Lib4gzApplication.kt
│
├── auth/
│   ├── controller/
│   │   └── AuthController.kt
│   ├── service/
│   │   └── AuthService.kt
│   ├── repo/
│   │   └── UserRepo.kt
│   └── model/
│       ├── entity/
│       │   └── User.kt                    # User, UserRole, UserStatus
│       ├── payload/
│       │   └── UserPayload.kt             # UserCreationRequest, UserResponse, LoginReq, Token, LoginResponse
│       └── mapper/
│           └── UserMapper.kt              # Extension fun User.toResponse()
│
├── courses/
│   ├── controller/
│   │   ├── CourseController.kt
│   │   ├── EnrollmentController.kt
│   │   ├── ModuleController.kt
│   │   ├── LessonController.kt
│   │   └── SummaryController.kt
│   ├── service/
│   │   ├── CourseService.kt               # Interface + CourseServiceImpl
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
│       ├── entity/
│       │   └── Courses.kt                 # Course, Enrollment, Module, Lesson, Summary + enums (all in one file)
│       ├── payload/
│       │   ├── CoursePayload.kt
│       │   ├── EnrollmentPayload.kt
│       │   ├── ModulePayload.kt
│       │   ├── LessonPayload.kt
│       │   └── SummaryPayload.kt
│       └── mapper/
│           └── CourseMapper.kt            # CourseMapper, EnrollmentMapper, ModuleMapper, LessonMapper, SummaryMapper (all in one file)
│
├── exercise/
│   ├── controller/
│   │   ├── ExerciseController.kt
│   │   ├── QuestionController.kt
│   │   └── SubmissionController.kt        # Also contains RevisionRequest, CommentRequest data classes
│   ├── service/
│   │   ├── ExerciseService.kt
│   │   ├── QuestionService.kt
│   │   └── SubmissionService.kt
│   ├── repo/
│   │   ├── ExerciseRepo.kt
│   │   ├── QuestionRepo.kt
│   │   ├── SubmissionRepo.kt
│   │   └── StudentAnswerRepo.kt
│   └── model/
│       ├── entity/
│       │   └── Exercise.kt               # Exercise, Question, Submission, StudentAnswer + enums (all in one file)
│       ├── payload/
│       │   ├── ExercisePayload.kt
│       │   ├── QuestionPayload.kt
│       │   └── SubmissionPayload.kt
│       └── mapper/
│           └── ExerciseMapper.kt          # ExerciseMapper, QuestionMapper, SubmissionMapper (all in one file)
│
└── common/
    ├── config/
    │   ├── security/
    │   │   ├── SecurityConfig.kt
    │   │   ├── AuthConfig.kt
    │   │   ├── JwtProvider.kt
    │   │   ├── JwtAuthFilter.kt
    │   │   ├── AuthEntryPoint.kt
    │   │   ├── CustomUserDetailService.kt
    │   │   ├── CustomUserDetails.kt
    │   │   └── loader/
    │   │       ├── SecurityConfigLoader.kt
    │   │       └── Entity.kt
    │   ├── common/
    │   │   ├── RequestHeader.kt           # PZRequestHeader object
    │   │   └── MutableHttpServletRequest.kt
    │   └── error/
    │       ├── ErrorResp.kt
    │       └── GlobalExceptionHandler.kt
    ├── exception/
    │   ├── BusinessException.kt
    │   ├── BadRequestException.kt
    │   ├── ResourceNotFoundException.kt
    │   ├── UnauthorizedException.kt
    │   └── ValidationException.kt
    └── utils/
        └── IdGenerator.kt
```

## Resources

```
src/main/resources/
├── application.yaml                       # Main Spring Boot configuration
├── application-dev.yaml                   # Dev profile overrides
└── security.json                          # Security route configuration (loaded by SecurityConfigLoader)
```

## Package Descriptions

### `auth`

Handles user registration, login, and JWT token generation. The `User` entity holds global roles and account status. The `AuthService` coordinates password hashing and token creation via `JwtProvider`.

### `courses`

The largest feature. Manages the full course hierarchy:

- **Course** -- top-level container, owned by a creator, has visibility settings
- **Enrollment** -- links users to courses with a role (TEACHER/LEARNER) and status (PENDING/ACTIVE/INACTIVE)
- **Module** -- ordered sections within a course
- **Lesson** -- ordered items within a module
- **Summary** -- versioned text content for a lesson (one-to-one with lesson)

All entities except `Enrollment` and `Summary` support soft delete.

### `exercise`

Manages assessments within lessons:

- **Exercise** -- an assessment container within a lesson, has a type and settings
- **Question** -- ordered items within an exercise, with JSONB metadata
- **Submission** -- a user's attempt at an exercise (one per user per exercise)
- **StudentAnswer** -- individual answers to questions within a submission

All entities except `Submission` and `StudentAnswer` support soft delete. Note that `Question.exerciseId` is a plain UUID column, not a JPA `@ManyToOne` relationship.

### `common`

Shared infrastructure:

- **config/security/**: JWT filter chain, token provider, Spring Security configuration, custom UserDetails
- **config/security/loader/**: Loads route-level security rules from `security.json`
- **config/common/**: Request header constants (`PZRequestHeader`), mutable request wrapper
- **config/error/**: Global exception handler, error response DTO
- **exception/**: Custom exception classes used throughout the application
- **utils/**: Utility classes (ID generation)
