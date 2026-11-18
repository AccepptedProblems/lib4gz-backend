-- Refresh tokens: one row per sign-in on one browser. The browser holds a random value; only its
-- SHA-256 hex is stored, so a leaked table holds no usable token. A refresh revokes the row it was
-- given and inserts the next one. Rows are hard-deleted by a daily job once expired, or revoked more
-- than seven days ago (a reused token is recognised only while its row exists).
-- Guarded: a database baselined at V1 is only assumed to match it.

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          varchar(50) NOT NULL,
    user_id     varchar(50) NOT NULL,
    token_hash  varchar(64) NOT NULL,
    expires_at  bigint      NOT NULL,
    revoked_at  bigint,               -- set by refresh, logout or a theft response
    rotated_at  bigint,               -- set only when a refresh revoked the row; starts the reuse grace
    created_at  bigint      NOT NULL,
    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS refresh_tokens_user_id_idx ON refresh_tokens (user_id);
