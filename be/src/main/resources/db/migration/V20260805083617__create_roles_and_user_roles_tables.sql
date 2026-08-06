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

CREATE TYPE sys_role AS ENUM (
    'ADMIN',
    'USER',
    'BOT'
);

-- ─────────────────────────────────────────────
-- 2. TABLE: roles
--    Lookup table – one row per system role.
--    Seeded by DataInitializer on application startup.
-- ─────────────────────────────────────────────

CREATE TABLE roles (
    id          UUID            PRIMARY KEY,
    name        sys_role        NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);


-- ─────────────────────────────────────────────
-- 3. TABLE: users
-- ─────────────────────────────────────────────

CREATE TABLE users (
    id              UUID            PRIMARY KEY,
    employee_id     VARCHAR(50)     UNIQUE,
    username        VARCHAR(100)    NOT NULL UNIQUE,
    full_name       VARCHAR(150)    NOT NULL,
    password        VARCHAR(255)    NOT NULL,
    status          user_status     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_users_username ON users (username);
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

