DROP INDEX IF EXISTS idx_users_guest_last_activity;

ALTER TABLE users
    DROP COLUMN IF EXISTS employee_id,
    DROP COLUMN IF EXISTS is_guest,
    DROP COLUMN IF EXISTS last_activity_at;
