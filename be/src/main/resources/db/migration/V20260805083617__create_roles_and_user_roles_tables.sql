-- ============================================================
-- Migration: Create users, roles, user_roles tables
-- Architecture: users <->> user_roles <<-> roles (M:N)
-- Module: Auth & User Management (Dev 1)
-- ============================================================

-- ─────────────────────────────────────────────
-- 1. ENUMS
-- ─────────────────────────────────────────────

CREATE TYPE user_status AS ENUM (
    'ACTIVE',
    'LOCKED'
);

-- sys_role is kept for backward-compat & JWT claim (single "primary" role per user)
CREATE TYPE sys_role AS ENUM (
    'ADMIN',
    'DOCTOR',
    'NURSE',
    'RECEPTIONIST',
    'PHARMACIST',
    'LAB_TECHNICIAN',
    'BILLING_STAFF',
    'PATIENT'
);

-- ─────────────────────────────────────────────
-- 2. TABLE: roles
--    Lookup table – one row per system role.
--    Seeded immediately after table creation.
-- ─────────────────────────────────────────────

CREATE TABLE roles (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name        sys_role        NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

-- Seed all roles
INSERT INTO roles (name, description) VALUES
    ('ADMIN',           'System administrator – full access'),
    ('DOCTOR',          'Physician – examine patients, write prescriptions'),
    ('NURSE',           'Nurse – monitor vitals, handle alerts'),
    ('RECEPTIONIST',    'Front-desk – register patients, manage queues'),
    ('PHARMACIST',      'Pharmacist – manage drug catalogue & inventory'),
    ('LAB_TECHNICIAN',  'Lab/Imaging technician – perform tests, enter results'),
    ('BILLING_STAFF',   'Billing staff – manage invoices & reports'),
    ('PATIENT',         'Patient – self-service portal access');

-- ─────────────────────────────────────────────
-- 3. TABLE: users
-- ─────────────────────────────────────────────

CREATE TABLE users (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     VARCHAR(50)     UNIQUE,                  -- NULL for PATIENT role
    username        VARCHAR(100)    NOT NULL UNIQUE,
    full_name       VARCHAR(150)    NOT NULL,
    email           VARCHAR(150)    NOT NULL UNIQUE,
    password        VARCHAR(255)    NOT NULL,
    -- Denormalised primary role for JWT claim & fast RBAC checks.
    -- Must stay in sync with user_roles (enforced by application layer).
    role            sys_role        NOT NULL,
    status          user_status     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email    ON users (email);
CREATE INDEX idx_users_role     ON users (role);
CREATE INDEX idx_users_status   ON users (status);

-- ─────────────────────────────────────────────
-- 4. TABLE: user_roles  (junction / pivot)
--    Allows a user to hold multiple roles if
--    needed in the future (e.g. DOCTOR + ADMIN).
-- ─────────────────────────────────────────────

CREATE TABLE user_roles (
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id     UUID        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

-- ─────────────────────────────────────────────
-- 5. Default ADMIN seed account
--    Password: Admin@123  (BCrypt, cost=12)
--    Change on first login in production!
-- ─────────────────────────────────────────────

INSERT INTO users (id, employee_id, username, full_name, email, password, role, status)
VALUES (
    gen_random_uuid(),
    'EMP-0001',
    'admin',
    'System Administrator',
    'admin@hospital.local',
    '$2a$12$GdAk7L2nFbPa5P3y4XZgIe.QSTL.kZ8k7cUOHGE3Z.d5CflekG/wC',  -- Admin@123
    'ADMIN',
    'ACTIVE'
)
RETURNING id;

-- Link admin user → ADMIN role in junction table
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM   users u
CROSS JOIN roles r
WHERE  u.username = 'admin'
  AND  r.name     = 'ADMIN';
