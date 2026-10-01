-- Baseline: the schema Hibernate's ddl-auto produced before Flyway took over.
--
-- Databases that already had these tables (Supabase) never run this file: Flyway marks them as
-- version 1 on first boot (spring.flyway.baseline-on-migrate). It runs only on an empty database.
-- Constraint names are the ones Postgres and Hibernate generated, so later migrations can refer to
-- them by name in every environment.

CREATE TABLE users (
    id              varchar(50)  NOT NULL,
    email           varchar(255),
    username        varchar(255) NOT NULL,
    password_hash   varchar(255),
    name            varchar(255) NOT NULL,
    avatar_url      varchar(255),
    email_verified  boolean      NOT NULL,
    pending_email   varchar(255),
    google_id       varchar(255),
    status          varchar(20)  NOT NULL,
    created_at      bigint       NOT NULL,
    updated_at      bigint       NOT NULL,
    deleted_at      bigint,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_username_key UNIQUE (username),
    CONSTRAINT users_google_id_key UNIQUE (google_id),
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'INACTIVE', 'BANNED'))
);

CREATE TABLE user_global_roles (
    user_id  varchar(50)  NOT NULL,
    role     varchar(255),
    CONSTRAINT user_global_roles_role_check CHECK (role IN ('ADMIN', 'USER', 'TEACHER')),
    CONSTRAINT fkaov6f0l996be2sjlt4mo9h6oe FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE email_otps (
    id          varchar(50)  NOT NULL,
    email       varchar(255) NOT NULL,
    purpose     varchar(30)  NOT NULL,
    code_hash   varchar(64)  NOT NULL,
    attempts    integer      NOT NULL,
    expires_at  bigint       NOT NULL,
    created_at  bigint       NOT NULL,
    CONSTRAINT email_otps_pkey PRIMARY KEY (id),
    CONSTRAINT email_otps_email_purpose_key UNIQUE (email, purpose),
    CONSTRAINT email_otps_purpose_check CHECK (purpose IN ('REGISTER', 'EMAIL_CHANGE', 'LOGIN', 'PASSWORD_RESET'))
);

CREATE TABLE pending_registrations (
    id              varchar(50)  NOT NULL,
    email           varchar(255) NOT NULL,
    google_id       varchar(255),
    name            varchar(255),
    avatar_url      text,
    email_verified  boolean      NOT NULL,
    created_at      bigint       NOT NULL,
    expires_at      bigint       NOT NULL,
    CONSTRAINT pending_registrations_pkey PRIMARY KEY (id)
);

CREATE TABLE courses (
    id           varchar(50)  NOT NULL,
    code         varchar(10)  NOT NULL,
    title        varchar(500) NOT NULL,
    description  text,
    visibility   varchar(50)  NOT NULL,
    created_by   varchar(50)  NOT NULL,
    settings     jsonb,
    created_at   bigint       NOT NULL,
    updated_at   bigint       NOT NULL,
    deleted_at   bigint,
    CONSTRAINT courses_pkey PRIMARY KEY (id),
    CONSTRAINT courses_code_key UNIQUE (code),
    CONSTRAINT courses_visibility_check CHECK (visibility IN ('PUBLIC', 'PRIVATE', 'UNLISTED')),
    CONSTRAINT fk4u40nf46n1nqa5h38sn5g17ac FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE enrollments (
    id                      varchar(50) NOT NULL,
    course_id               varchar(50) NOT NULL,
    user_id                 varchar(50) NOT NULL,
    role                    varchar(50) NOT NULL,
    status                  varchar(50) NOT NULL,
    joined_at               bigint,
    last_visited_lesson_id  varchar(50),
    last_visited_at         bigint,
    created_at              bigint      NOT NULL,
    updated_at              bigint      NOT NULL,
    CONSTRAINT enrollments_pkey PRIMARY KEY (id),
    CONSTRAINT enrollments_course_id_user_id_key UNIQUE (course_id, user_id),
    CONSTRAINT enrollments_role_check CHECK (role IN ('LEARNER', 'TEACHER')),
    CONSTRAINT enrollments_status_check CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'INACTIVE')),
    CONSTRAINT fkho8mcicp4196ebpltdn9wl6co FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk3hjx6rcnbmfw368sxigrpfpx0 FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE modules (
    id           varchar(50)  NOT NULL,
    course_id    varchar(50)  NOT NULL,
    title        varchar(500) NOT NULL,
    order_index  integer      NOT NULL,
    created_at   bigint       NOT NULL,
    updated_at   bigint       NOT NULL,
    deleted_at   bigint,
    CONSTRAINT modules_pkey PRIMARY KEY (id),
    CONSTRAINT modules_course_id_order_index_key UNIQUE (course_id, order_index),
    CONSTRAINT fk8qnnp812q1jd38fx7mxrhpw9 FOREIGN KEY (course_id) REFERENCES courses (id)
);

CREATE TABLE lessons (
    id           varchar(50)  NOT NULL,
    module_id    varchar(50)  NOT NULL,
    title        varchar(500) NOT NULL,
    order_index  integer      NOT NULL,
    created_at   bigint       NOT NULL,
    updated_at   bigint       NOT NULL,
    deleted_at   bigint,
    CONSTRAINT lessons_pkey PRIMARY KEY (id),
    CONSTRAINT lessons_module_id_order_index_key UNIQUE (module_id, order_index),
    CONSTRAINT fkt9yjhjbd9y3w6fxs66ny1wu02 FOREIGN KEY (module_id) REFERENCES modules (id)
);

CREATE TABLE summaries (
    id          varchar(50) NOT NULL,
    lesson_id   varchar(50) NOT NULL,
    content     text        NOT NULL,
    edited_by   varchar(50) NOT NULL,
    version     integer     NOT NULL,
    created_at  bigint      NOT NULL,
    updated_at  bigint      NOT NULL,
    CONSTRAINT summaries_pkey PRIMARY KEY (id),
    CONSTRAINT summaries_lesson_id_key UNIQUE (lesson_id),
    CONSTRAINT fkle9tahjyj1nl8aruiu170vpdr FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    CONSTRAINT fklv2kktltb1uqwxgi206ofty9u FOREIGN KEY (edited_by) REFERENCES users (id)
);

CREATE TABLE course_resource_freshness (
    id             varchar(50) NOT NULL,
    course_id      varchar(50) NOT NULL,
    resource_type  varchar(50) NOT NULL,
    last_update    bigint      NOT NULL,
    CONSTRAINT course_resource_freshness_pkey PRIMARY KEY (id),
    CONSTRAINT course_resource_freshness_course_id_resource_type_key UNIQUE (course_id, resource_type),
    CONSTRAINT course_resource_freshness_resource_type_check
        CHECK (resource_type IN ('SYLLABUS', 'LESSON', 'SUMMARY', 'EXERCISE', 'SUBMISSION'))
);

CREATE TABLE exercises (
    id           varchar(50)  NOT NULL,
    lesson_id    varchar(50)  NOT NULL,
    title        varchar(500) NOT NULL,
    type         varchar(50)  NOT NULL,
    settings     jsonb,
    order_index  integer      NOT NULL,
    created_at   bigint       NOT NULL,
    updated_at   bigint       NOT NULL,
    deleted_at   bigint,
    CONSTRAINT exercises_pkey PRIMARY KEY (id),
    CONSTRAINT exercises_lesson_id_order_index_key UNIQUE (lesson_id, order_index),
    CONSTRAINT exercises_type_check
        CHECK (type IN ('TEXT_ANSWER', 'CODE', 'FILE_UPLOAD', 'MULTIPLE_CHOICE', 'PROJECT_LINK')),
    CONSTRAINT fkes9e0n86cjfb0l6349clxvxc1 FOREIGN KEY (lesson_id) REFERENCES lessons (id)
);

-- exercise_id is a plain column in the entity, so Hibernate never created a foreign key for it.
CREATE TABLE questions (
    id           varchar(50) NOT NULL,
    exercise_id  varchar(50) NOT NULL,
    content      text        NOT NULL,
    order_index  integer     NOT NULL,
    meta         jsonb,
    visibility   varchar(50),
    created_at   bigint      NOT NULL,
    updated_at   bigint      NOT NULL,
    deleted_at   bigint,
    CONSTRAINT questions_pkey PRIMARY KEY (id),
    CONSTRAINT questions_visibility_check CHECK (visibility IN ('VISIBLE', 'HIDDEN'))
);

CREATE TABLE submissions (
    id            varchar(50) NOT NULL,
    exercise_id   varchar(50) NOT NULL,
    user_id       varchar(50) NOT NULL,
    status        varchar(50) NOT NULL,
    created_at    bigint      NOT NULL,
    updated_at    bigint      NOT NULL,
    submitted_at  bigint,
    approved_at   bigint,
    CONSTRAINT submissions_pkey PRIMARY KEY (id),
    CONSTRAINT submissions_exercise_id_user_id_key UNIQUE (exercise_id, user_id),
    CONSTRAINT submissions_status_check CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'NEEDS_REVISION')),
    CONSTRAINT fkrjkxeiu6wxkbw9h1u58093jm5 FOREIGN KEY (exercise_id) REFERENCES exercises (id),
    CONSTRAINT fk760bgu69957phd7hax608jdms FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE student_answers (
    id               varchar(50) NOT NULL,
    question_id      varchar(50) NOT NULL,
    submission_id    varchar(50) NOT NULL,
    answer           text,
    teacher_comment  text,
    created_at       bigint      NOT NULL,
    updated_at       bigint      NOT NULL,
    CONSTRAINT student_answers_pkey PRIMARY KEY (id),
    CONSTRAINT fk8nyksamccim8emu803uhf2da FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT fkf0s9eifp1j4hxy0v2of0obgqf FOREIGN KEY (submission_id) REFERENCES submissions (id)
);
