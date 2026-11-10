-- Lightning Kart, phase 1. A course keeps its own bank of race questions, separate from the
-- exercise questions; a teacher assigns each one to a module. Every finished race is saved as a
-- result and shown on a leaderboard per course or per module, so results are append-only: no
-- deleted_at. The racer's chosen look (character and kart) lives on the user.
-- IF NOT EXISTS: a database baselined at V1 is only assumed to match it.

CREATE TABLE IF NOT EXISTS race_questions (
    id             varchar(50) NOT NULL,
    course_id      varchar(50) NOT NULL,
    module_id      varchar(50) NOT NULL,
    content        text        NOT NULL,
    options        jsonb       NOT NULL,
    correct_index  integer     NOT NULL,
    order_index    integer     NOT NULL,
    created_at     bigint      NOT NULL,
    updated_at     bigint      NOT NULL,
    deleted_at     bigint,
    CONSTRAINT race_questions_pkey PRIMARY KEY (id),
    CONSTRAINT race_questions_course_id_fkey FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT race_questions_module_id_fkey FOREIGN KEY (module_id) REFERENCES modules (id)
);

CREATE INDEX IF NOT EXISTS race_questions_course_id_idx ON race_questions (course_id);
CREATE INDEX IF NOT EXISTS race_questions_course_id_module_id_idx ON race_questions (course_id, module_id);

CREATE TABLE IF NOT EXISTS race_results (
    id             varchar(50) NOT NULL,
    user_id        varchar(50) NOT NULL,
    course_id      varchar(50) NOT NULL,
    module_id      varchar(50),
    finish_ms      integer     NOT NULL,
    correct_count  integer     NOT NULL,
    wrong_count    integer     NOT NULL,
    seed           bigint      NOT NULL,
    created_at     bigint      NOT NULL,
    CONSTRAINT race_results_pkey PRIMARY KEY (id),
    CONSTRAINT race_results_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT race_results_course_id_fkey FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT race_results_module_id_fkey FOREIGN KEY (module_id) REFERENCES modules (id)
);

CREATE INDEX IF NOT EXISTS race_results_course_id_module_id_user_id_idx
    ON race_results (course_id, module_id, user_id);

-- {"characterId": "...", "kartId": "..."}; null until the person picks a look.
ALTER TABLE users ADD COLUMN IF NOT EXISTS race_appearance jsonb;
