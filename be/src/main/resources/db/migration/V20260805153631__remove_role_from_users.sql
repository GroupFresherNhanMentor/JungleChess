-- ============================================================
-- Migration: Remove denormalised `role` column from users
-- Roles are now managed exclusively via user_roles (M:N).
-- ============================================================

-- 1. Drop the index that was on users.role
DROP INDEX IF EXISTS idx_users_role;

-- 2. Drop the denormalised role column
ALTER TABLE users DROP COLUMN IF EXISTS role;

-- 3. (Safety) Ensure every existing user has at least one entry
--    in user_roles. Back-fill any gaps using the roles table.
--    (No-op if data is already consistent.)
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM   users u
CROSS JOIN roles r
WHERE  r.name = 'ADMIN'           -- default fallback role
  AND  NOT EXISTS (
      SELECT 1 FROM user_roles ur
      WHERE ur.user_id = u.id
  );
