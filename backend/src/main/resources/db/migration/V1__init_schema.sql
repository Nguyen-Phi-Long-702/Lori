-- V1__init_schema.sql
-- Schema khoi tao cho Lori Backend (PostgreSQL 16 tren Neon).
-- Gom: users, refresh_tokens, user_progress, subscriptions.
-- Cac bang con lai (exam_*, learning/AI...) se duoc them bang migration V2, V3 theo ke hoach.

-- ============================================================
-- users: tai khoan nguoi dung (Email + Google, co trang thai Premium)
-- ============================================================
CREATE TABLE users (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(255),
    display_name        VARCHAR(100) NOT NULL,
    avatar_url          VARCHAR(500),
    google_id           VARCHAR(255),
    is_premium          BOOLEAN      NOT NULL DEFAULT FALSE,
    premium_expires_at  TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email     UNIQUE (email),
    CONSTRAINT uq_users_google_id UNIQUE (google_id)
);

-- ============================================================
-- refresh_tokens: Refresh Token 7 ngay, luu DB, co co thu hoi (is_revoked)
-- ============================================================
CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token       TEXT         NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    is_revoked  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_refresh_tokens_token UNIQUE (token)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- ============================================================
-- user_progress: tien trinh hoc dong bo tu Android (user_progress_local)
-- Moi (user, item_type, item_id) chi co 1 dong; xung dot giai quyet theo last_studied_at
-- ============================================================
CREATE TABLE user_progress (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    item_type        VARCHAR(50)  NOT NULL,
    item_id          INTEGER      NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    correct_count    INTEGER      NOT NULL DEFAULT 0,
    incorrect_count  INTEGER      NOT NULL DEFAULT 0,
    last_studied_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_user_progress_item UNIQUE (user_id, item_type, item_id)
);

-- ============================================================
-- subscriptions: lich su goi Premium (GOOGLE_PLAY / VNPAY / MOMO / MOCK / TEST)
-- ============================================================
CREATE TABLE subscriptions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan_type       VARCHAR(30)  NOT NULL,
    payment_method  VARCHAR(30)  NOT NULL,
    transaction_id  VARCHAR(255),
    status          VARCHAR(30)  NOT NULL,
    starts_at       TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ,
    CONSTRAINT uq_subscriptions_transaction_id UNIQUE (transaction_id)
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions (user_id);