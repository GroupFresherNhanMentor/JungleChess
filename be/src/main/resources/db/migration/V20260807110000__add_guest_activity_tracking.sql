-- Track guest lifecycle without affecting registered users.
ALTER TABLE users
    ALTER COLUMN password DROP NOT NULL;

ALTER TABLE users
    ADD COLUMN is_guest BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX idx_users_guest_last_activity
    ON users (last_activity_at)
    WHERE is_guest = TRUE;
