-- Lightning Kart: the server deals each race and checks each answer. One row per race holds the
-- dealt questions, the key and the counters, so an answer is one UPDATE on that row.
-- Every saved result is deleted: those times were set on the old track, with counts the browser
-- reported itself.
-- Guarded: a database baselined at V1 is only assumed to match it.

DO $$
BEGIN
    IF to_regclass('race_results') IS NOT NULL THEN
        DELETE FROM race_results;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS races (
    id             varchar(50) NOT NULL,
    user_id        varchar(50) NOT NULL,
    course_id      varchar(50) NOT NULL,
    module_id      varchar(50),           -- null for a race over the whole course
    seed           bigint      NOT NULL,
    question_ids   jsonb       NOT NULL,  -- ["rqn_..."], one per question number, from 0
    answer_key     jsonb       NOT NULL,  -- [2, 0, ...]: the right shown option per question number
    next_seq       integer     NOT NULL,  -- the lowest question number still open
    correct_count  integer     NOT NULL,
    wrong_count    integer     NOT NULL,
    last_correct   boolean,               -- the verdict on question next_seq - 1; null before any
    started_at     bigint      NOT NULL,
    finished_at    bigint,
    CONSTRAINT races_pkey PRIMARY KEY (id),
    CONSTRAINT races_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT races_course_id_fkey FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT races_module_id_fkey FOREIGN KEY (module_id) REFERENCES modules (id)
);
