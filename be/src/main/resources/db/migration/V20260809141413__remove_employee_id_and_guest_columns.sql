DROP INDEX IF EXISTS idx_users_guest_last_activity;

ALTER TABLE users DROP COLUMN IF EXISTS employee_id;
ALTER TABLE users DROP COLUMN IF EXISTS is_guest;
ALTER TABLE users DROP COLUMN IF EXISTS last_activity_at;
