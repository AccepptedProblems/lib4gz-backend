# lib4gz Database Schema

## Overview

- **Database**: PostgreSQL
- **ID Strategy**: Application-generated TypeIDs stored as `VARCHAR(50)` — type-prefixed, UUIDv7-based, Crockford Base32 encoded (e.g., `usr_01h455vb4pex5vsknk084sn02q`)
- **Timestamps**: All stored as `BIGINT` epoch milliseconds (camelCase naming: `createdAt`, `updatedAt`)
- **Soft Delete**: `deletedAt BIGINT` column (NULL means active, non-NULL means deleted)
- **JSONB**: Used for flexible metadata (course settings, question meta, exercise settings)

---

## DDL

### users

```sql
CREATE TABLE users (
    id              VARCHAR(50)     PRIMARY KEY,
    email           VARCHAR(255)    UNIQUE,
    username        VARCHAR(255)    UNIQUE NOT NULL,
    passwordHash    VARCHAR(255),
    name            VARCHAR(255)    NOT NULL,
    avatarUrl       TEXT,
    emailVerified   BOOLEAN         NOT NULL DEFAULT FALSE,
    pending_email   VARCHAR(255),                       -- new email waiting for its one-time code
    google_id       VARCHAR(255)    UNIQUE,             -- Google `sub`; set by Google sign-up or linking
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    createdAt       BIGINT          NOT NULL,
    updatedAt       BIGINT          NOT NULL,
    deletedAt       BIGINT,

    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BANNED'))
);
```

`pending_email` and `google_id` are added to existing databases by `ddl-auto=update`; check that the `google_id` unique constraint exists after the first boot. No data migration: existing rows keep `emailVerified = false`, `pending_email` and `google_id` NULL.

### email_otps

One-time email codes. **Hard-deleted** (transient credentials). New in the email/Google registration work; column names are the snake_case names mapped in `EmailOtp.kt`.

```sql
CREATE TABLE email_otps (
    id              VARCHAR(50)     PRIMARY KEY,        -- otp_…
    email           VARCHAR(255)    NOT NULL,
    purpose         VARCHAR(30)     NOT NULL,
    code_hash       VARCHAR(64)     NOT NULL,           -- SHA-256 hex, never the code itself
    attempts        INTEGER         NOT NULL,
    expires_at      BIGINT          NOT NULL,
    created_at      BIGINT          NOT NULL,           -- reset on reissue; resend cooldown measured from it

    CONSTRAINT uq_email_otps_email_purpose UNIQUE (email, purpose),
    CONSTRAINT chk_email_otps_purpose CHECK (purpose IN ('REGISTER', 'EMAIL_CHANGE', 'LOGIN', 'PASSWORD_RESET'))
);
```

At most one row per `(email, purpose)`. Reissuing a code updates that row in place; a correct code, an expired code, or too many attempts deletes it. The unique and check constraint names above are illustrative; Hibernate generates its own.

### pending_registrations

Sign-ups that proved their email (by code or by Google) but have not chosen a username yet. **Hard-deleted** on completion, when the same email starts again, or after expiry.

```sql
CREATE TABLE pending_registrations (
    id              VARCHAR(50)     PRIMARY KEY,        -- reg_…, the browser's only credential
    email           VARCHAR(255)    NOT NULL,
    google_id       VARCHAR(255),                       -- NULL on the email path
    name            VARCHAR(255),
    avatar_url      TEXT,
    email_verified  BOOLEAN         NOT NULL,
    created_at      BIGINT          NOT NULL,
    expires_at      BIGINT          NOT NULL            -- created_at + 15 min
);
```

`RegistrationCleanupJob` deletes rows of `email_otps` and `pending_registrations` whose `expires_at` has passed, every 10 minutes. Neither table has a foreign key to `users`.

### user_global_roles

```sql
CREATE TABLE user_global_roles (
    userId      VARCHAR(50)     NOT NULL,
    role        VARCHAR(50)     NOT NULL,

    PRIMARY KEY (userId, role),

    CONSTRAINT fk_user_global_roles_user
        FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE,

    CONSTRAINT chk_user_global_roles_role CHECK (role IN ('ADMIN', 'USER', 'TEACHER'))
);
```

### courses

```sql
CREATE TABLE courses (
    id              VARCHAR(50)     PRIMARY KEY,
    title           VARCHAR(500)    NOT NULL,
    description     TEXT,
    visibility      VARCHAR(50)     NOT NULL DEFAULT 'PRIVATE',
    createdBy       VARCHAR(50)     NOT NULL,
    settings        JSONB           DEFAULT '{}',
    createdAt       BIGINT          NOT NULL,
    updatedAt       BIGINT          NOT NULL,
    deletedAt       BIGINT,

    CONSTRAINT fk_courses_created_by
        FOREIGN KEY (createdBy) REFERENCES users(id),

    CONSTRAINT chk_courses_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE'))
);

CREATE INDEX idx_courses_created_by ON courses(createdBy);
```

### enrollments

```sql
CREATE TABLE enrollments (
    id              VARCHAR(50)     PRIMARY KEY,
    courseId         VARCHAR(50)     NOT NULL,
    userId          VARCHAR(50)     NOT NULL,
    role            VARCHAR(50)     NOT NULL,
    status          VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    joinedAt        BIGINT,
    createdAt       BIGINT          NOT NULL,
    updatedAt       BIGINT          NOT NULL,

    CONSTRAINT fk_enrollments_course
        FOREIGN KEY (courseId) REFERENCES courses(id) ON DELETE CASCADE,

    CONSTRAINT fk_enrollments_user
        FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE,

    CONSTRAINT uq_enrollments_course_user UNIQUE (courseId, userId),

    CONSTRAINT chk_enrollments_role CHECK (role IN ('TEACHER', 'LEARNER')),
    CONSTRAINT chk_enrollments_status CHECK (status IN ('PENDING', 'ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_enrollments_course_id ON enrollments(courseId);
CREATE INDEX idx_enrollments_user_id ON enrollments(userId);
```

### modules

```sql
CREATE TABLE modules (
    id              VARCHAR(50)     PRIMARY KEY,
    courseId         VARCHAR(50)     NOT NULL,
    title           VARCHAR(500)    NOT NULL,
    orderIndex      INTEGER         NOT NULL,
    createdAt       BIGINT          NOT NULL,
    updatedAt       BIGINT          NOT NULL,
    deletedAt       BIGINT,

    CONSTRAINT fk_modules_course
        FOREIGN KEY (courseId) REFERENCES courses(id) ON DELETE CASCADE,

    CONSTRAINT uq_modules_course_order UNIQUE (courseId, orderIndex)
);

CREATE INDEX idx_modules_course_id ON modules(courseId);
```

### lessons

```sql
CREATE TABLE lessons (
    id              VARCHAR(50)     PRIMARY KEY,
    moduleId        VARCHAR(50)     NOT NULL,
    title           VARCHAR(500)    NOT NULL,
    orderIndex      INTEGER         NOT NULL,
    createdAt       BIGINT          NOT NULL,
    updatedAt       BIGINT          NOT NULL,
    deletedAt       BIGINT,

    CONSTRAINT fk_lessons_module
        FOREIGN KEY (moduleId) REFERENCES modules(id) ON DELETE CASCADE,

    CONSTRAINT uq_lessons_module_order UNIQUE (moduleId, orderIndex)
);

CREATE INDEX idx_lessons_module_id ON lessons(moduleId);
```

### summaries

```sql
CREATE TABLE summaries (
    id              VARCHAR(50)     PRIMARY KEY,
    lessonId        VARCHAR(50)     UNIQUE NOT NULL,
    content         TEXT            NOT NULL,
    editedBy        VARCHAR(50)     NOT NULL,
    version         INTEGER         NOT NULL DEFAULT 1,
    createdAt       BIGINT          NOT NULL,
    updatedAt       BIGINT          NOT NULL,

    CONSTRAINT fk_summaries_lesson
        FOREIGN KEY (lessonId) REFERENCES lessons(id) ON DELETE CASCADE,

    CONSTRAINT fk_summaries_edited_by
        FOREIGN KEY (editedBy) REFERENCES users(id)
);

CREATE INDEX idx_summaries_lesson_id ON summaries(lessonId);
```

### exercises

```sql
CREATE TABLE exercises (
    id              VARCHAR(50)     PRIMARY KEY,
    lessonId        VARCHAR(50)     NOT NULL,
    title           VARCHAR(500)    NOT NULL,
    type            VARCHAR(50)     NOT NULL,
    settings        JSONB           DEFAULT '{}',
    orderIndex      INTEGER         NOT NULL,
    createdAt       BIGINT          NOT NULL,
    updatedAt       BIGINT          NOT NULL,
    deletedAt       BIGINT,

    CONSTRAINT fk_exercises_lesson
        FOREIGN KEY (lessonId) REFERENCES lessons(id) ON DELETE CASCADE,

    CONSTRAINT uq_exercises_lesson_order UNIQUE (lessonId, orderIndex),

    CONSTRAINT chk_exercises_type CHECK (type IN ('QUIZ', 'ASSIGNMENT', 'PRACTICE'))
);

CREATE INDEX idx_exercises_lesson_id ON exercises(lessonId);
```

### questions

Note: `exerciseId` is a plain UUID column in the JPA entity (no `@ManyToOne` relationship). It is still a logical foreign key to `exercises`.

```sql
CREATE TABLE questions (
    id              VARCHAR(50)     PRIMARY KEY,
    exerciseId      VARCHAR(50)     NOT NULL,
    content         TEXT            NOT NULL,
    orderIndex      INTEGER         NOT NULL,
    meta            JSONB           DEFAULT '{}',
    visibility      VARCHAR(50)     DEFAULT 'VISIBLE',
    createdAt       BIGINT          NOT NULL,
    updatedAt       BIGINT          NOT NULL,
    deletedAt       BIGINT,

    CONSTRAINT fk_questions_exercise
        FOREIGN KEY (exerciseId) REFERENCES exercises(id) ON DELETE CASCADE,

    CONSTRAINT chk_questions_visibility CHECK (visibility IN ('VISIBLE', 'HIDDEN'))
);

CREATE INDEX idx_questions_exercise_id ON questions(exerciseId);
```

### submissions

```sql
CREATE TABLE submissions (
    id              VARCHAR(50)     PRIMARY KEY,
    exerciseId      VARCHAR(50)     NOT NULL,
    userId          VARCHAR(50)     NOT NULL,
    status          VARCHAR(50)     NOT NULL DEFAULT 'DRAFT',
    createdAt       BIGINT          NOT NULL,
    updatedAt       BIGINT          NOT NULL,
    submittedAt     BIGINT,
    approvedAt      BIGINT,

    CONSTRAINT fk_submissions_exercise
        FOREIGN KEY (exerciseId) REFERENCES exercises(id) ON DELETE CASCADE,

    CONSTRAINT fk_submissions_user
        FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE,

    CONSTRAINT uq_submissions_exercise_user UNIQUE (exerciseId, userId),

    CONSTRAINT chk_submissions_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REVISION_REQUESTED', 'APPROVED'))
);

CREATE INDEX idx_submissions_exercise_id ON submissions(exerciseId);
CREATE INDEX idx_submissions_user_id ON submissions(userId);
```

### student_answers

```sql
CREATE TABLE student_answers (
    id              VARCHAR(50)     PRIMARY KEY,
    questionId      VARCHAR(50)     NOT NULL,
    submissionId    VARCHAR(50)     NOT NULL,
    answer          TEXT,
    teacherComment  TEXT,
    createdAt       BIGINT          NOT NULL,
    updatedAt       BIGINT          NOT NULL,

    CONSTRAINT fk_student_answers_question
        FOREIGN KEY (questionId) REFERENCES questions(id) ON DELETE CASCADE,

    CONSTRAINT fk_student_answers_submission
        FOREIGN KEY (submissionId) REFERENCES submissions(id) ON DELETE CASCADE
);

CREATE INDEX idx_student_answers_question_id ON student_answers(questionId);
CREATE INDEX idx_student_answers_submission_id ON student_answers(submissionId);
```

---

## Entity-Relationship Diagram

```mermaid
erDiagram
    users {
        VARCHAR(50) id PK
        VARCHAR(255) email UK
        VARCHAR(255) username UK
        VARCHAR(255) passwordHash
        VARCHAR(255) name
        TEXT avatarUrl
        BOOLEAN emailVerified
        VARCHAR(255) pending_email
        VARCHAR(255) google_id UK
        VARCHAR(20) status
        BIGINT createdAt
        BIGINT updatedAt
        BIGINT deletedAt
    }

    email_otps {
        VARCHAR(50) id PK
        VARCHAR(255) email
        VARCHAR(30) purpose
        VARCHAR(64) code_hash
        INTEGER attempts
        BIGINT expires_at
        BIGINT created_at
    }

    pending_registrations {
        VARCHAR(50) id PK
        VARCHAR(255) email
        VARCHAR(255) google_id
        VARCHAR(255) name
        TEXT avatar_url
        BOOLEAN email_verified
        BIGINT created_at
        BIGINT expires_at
    }

    user_global_roles {
        VARCHAR(50) userId PK,FK
        VARCHAR(50) role PK
    }

    courses {
        VARCHAR(50) id PK
        VARCHAR(500) title
        TEXT description
        VARCHAR(50) visibility
        VARCHAR(50) createdBy FK
        JSONB settings
        BIGINT createdAt
        BIGINT updatedAt
        BIGINT deletedAt
    }

    enrollments {
        VARCHAR(50) id PK
        VARCHAR(50) courseId FK
        VARCHAR(50) userId FK
        VARCHAR(50) role
        VARCHAR(50) status
        BIGINT joinedAt
        BIGINT createdAt
        BIGINT updatedAt
    }

    modules {
        VARCHAR(50) id PK
        VARCHAR(50) courseId FK
        VARCHAR(500) title
        INTEGER orderIndex
        BIGINT createdAt
        BIGINT updatedAt
        BIGINT deletedAt
    }

    lessons {
        VARCHAR(50) id PK
        VARCHAR(50) moduleId FK
        VARCHAR(500) title
        INTEGER orderIndex
        BIGINT createdAt
        BIGINT updatedAt
        BIGINT deletedAt
    }

    summaries {
        VARCHAR(50) id PK
        VARCHAR(50) lessonId FK
        TEXT content
        VARCHAR(50) editedBy FK
        INTEGER version
        BIGINT createdAt
        BIGINT updatedAt
    }

    exercises {
        VARCHAR(50) id PK
        VARCHAR(50) lessonId FK
        VARCHAR(500) title
        VARCHAR(50) type
        JSONB settings
        INTEGER orderIndex
        BIGINT createdAt
        BIGINT updatedAt
        BIGINT deletedAt
    }

    questions {
        VARCHAR(50) id PK
        VARCHAR(50) exerciseId FK
        TEXT content
        INTEGER orderIndex
        JSONB meta
        VARCHAR(50) visibility
        BIGINT createdAt
        BIGINT updatedAt
        BIGINT deletedAt
    }

    submissions {
        VARCHAR(50) id PK
        VARCHAR(50) exerciseId FK
        VARCHAR(50) userId FK
        VARCHAR(50) status
        BIGINT createdAt
        BIGINT updatedAt
        BIGINT submittedAt
        BIGINT approvedAt
    }

    student_answers {
        VARCHAR(50) id PK
        VARCHAR(50) questionId FK
        VARCHAR(50) submissionId FK
        TEXT answer
        TEXT teacherComment
        BIGINT createdAt
        BIGINT updatedAt
    }

    users ||--o{ user_global_roles : "has roles"
    users ||--o{ courses : "creates"
    users ||--o{ enrollments : "enrolls in"
    courses ||--o{ enrollments : "has"
    courses ||--o{ modules : "contains"
    modules ||--o{ lessons : "contains"
    lessons ||--o| summaries : "has"
    lessons ||--o{ exercises : "contains"
    exercises ||--o{ questions : "contains"
    exercises ||--o{ submissions : "receives"
    users ||--o{ submissions : "submits"
    users ||--o{ summaries : "edits"
    questions ||--o{ student_answers : "answered by"
    submissions ||--o{ student_answers : "contains"
```

---

## Soft Delete Summary

| Table         | Soft Delete | Notes                                          |
|---------------|-------------|-------------------------------------------------|
| users         | Yes         | `deletedAt` column                              |
| email_otps    | No          | Hard-deleted: consumed, expired, or swept       |
| pending_registrations | No  | Hard-deleted: completed, restarted, or swept    |
| courses       | Yes         | `deletedAt` column                              |
| enrollments   | No          | Status-based lifecycle (PENDING/ACTIVE/INACTIVE)|
| modules       | Yes         | `deletedAt` column                              |
| lessons       | Yes         | `deletedAt` column                              |
| summaries     | No          | Versioned, not deleted                          |
| exercises     | Yes         | `deletedAt` column                              |
| questions     | Yes         | `deletedAt` column                              |
| submissions   | No          | Status-based lifecycle (DRAFT/SUBMITTED/etc.)   |
| student_answers | No        | Tied to submission lifecycle                    |

---

## Enum Values Reference

| Column                    | Allowed Values                                    |
|---------------------------|---------------------------------------------------|
| `users.status`            | `ACTIVE`, `INACTIVE`, `BANNED`                    |
| `email_otps.purpose`      | `REGISTER`, `EMAIL_CHANGE`, `LOGIN` (reserved), `PASSWORD_RESET` (reserved) |
| `user_global_roles.role`  | `ADMIN`, `USER`, `TEACHER`                          |
| `courses.visibility`      | `PUBLIC`, `PRIVATE`                               |
| `enrollments.role`        | `TEACHER`, `LEARNER`                              |
| `enrollments.status`      | `PENDING`, `ACTIVE`, `INACTIVE`                   |
| `exercises.type`          | `QUIZ`, `ASSIGNMENT`, `PRACTICE`                  |
| `questions.visibility`    | `VISIBLE`, `HIDDEN`                               |
| `submissions.status`      | `DRAFT`, `SUBMITTED`, `REVISION_REQUESTED`, `APPROVED` |
