-- ============================================================
-- Migration: Create bots table
-- Module: Bot AI Engine (Dev 2 - Khôi)
-- Based on: docs/DB_Design_Co_Thu_Online.md §3.3
-- ============================================================

-- ─────────────────────────────────────────────
-- 1. TABLE: bots
--    Lookup table – one row per bot difficulty.
--    Maps player-facing botDifficulty to minimax search depth.
-- ─────────────────────────────────────────────

CREATE TABLE bots (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(50)  NOT NULL,
    difficulty   VARCHAR(20)  NOT NULL CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    search_depth INT          NOT NULL,
    description  TEXT
);

CREATE INDEX idx_bots_difficulty ON bots (difficulty);

-- ─────────────────────────────────────────────
-- 2. SEED: default bot configurations
--    Depths match docs/DB_Design_Co_Thu_Online.md §3.3 seed data.
-- ─────────────────────────────────────────────

INSERT INTO bots (name, difficulty, search_depth, description) VALUES
    ('Bot Dễ',         'EASY',   2, 'Độ sâu tìm kiếm thấp, phù hợp người mới'),
    ('Bot Trung Bình', 'MEDIUM', 4, 'Cân bằng tốc độ và độ khó'),
    ('Bot Khó',        'HARD',   6, 'Tìm kiếm sâu, phản hồi chậm hơn');
